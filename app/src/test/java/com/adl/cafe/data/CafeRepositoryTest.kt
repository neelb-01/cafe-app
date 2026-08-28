package com.adl.cafe.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adl.cafe.data.dao.OrderDao
import com.adl.cafe.data.model.DrinkSize
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.data.model.OrderEntity
import com.adl.cafe.data.model.OrderLine
import com.adl.cafe.data.model.OrderType
import com.adl.cafe.data.model.OrderWithLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room needs the framework SQLite, so these run under Robolectric — no emulator,
 * just `./gradlew test`. [AndroidJUnit4] is a delegating runner: it picks
 * Robolectric on the JVM and the device runner if this file is ever moved to
 * `src/androidTest/`.
 *
 * `application = Application::class` deliberately swaps out [com.adl.cafe.CafeApplication],
 * whose `onCreate` opens the real on-disk database and seeds the menu. These
 * tests want an empty in-memory database and nothing else.
 *
 * Method names stay camelCase rather than backticked so the file can move to
 * `src/androidTest/` unchanged — API 24 rejects method names containing spaces
 * at runtime, and Robolectric does not.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CafeRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CafeRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = repositoryWith(database.orderDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    // ---- addToCart merges atomically --------------------------------------

    @Test
    fun addingTheSameItemAndSizeMergesIntoOneLine() = runBlocking {
        seedMenu()

        repository.addToCart(LATTE_ID, DrinkSize.MEDIUM, 1)
        repository.addToCart(LATTE_ID, DrinkSize.MEDIUM, 2)

        val lines = database.cartDao().getCartLines()
        assertEquals(1, lines.size)
        assertEquals(3, lines.single().cartItem.quantity)
    }

    @Test
    fun differentSizesOfTheSameItemStaySeparate() = runBlocking {
        seedMenu()

        repository.addToCart(LATTE_ID, DrinkSize.SMALL, 1)
        repository.addToCart(LATTE_ID, DrinkSize.LARGE, 1)

        assertEquals(2, database.cartDao().getCartLines().size)
    }

    /**
     * The bug this guards: the find-then-insert pair used to sit outside a
     * transaction, so racing quick-adds could both miss the existing row and
     * both insert. The unique index on (menuItemId, size) then turned the loser
     * into an uncaught SQLiteConstraintException.
     */
    @Test
    fun concurrentAddsOfTheSameLineAllSurvive() = runBlocking {
        seedMenu()

        coroutineScope {
            (1..CONCURRENT_ADDS)
                .map {
                    async(Dispatchers.Default) {
                        repository.addToCart(LATTE_ID, DrinkSize.MEDIUM, 1)
                    }
                }
                .awaitAll()
        }

        val lines = database.cartDao().getCartLines()
        assertEquals("racing adds should still merge into one line", 1, lines.size)
        assertEquals(
            "every concurrent add should be counted",
            CONCURRENT_ADDS,
            lines.single().cartItem.quantity
        )
    }

    @Test
    fun concurrentAddsOfDifferentSizesAllSurvive() = runBlocking {
        seedMenu()
        val sizes = DrinkSize.entries

        coroutineScope {
            (0 until CONCURRENT_ADDS)
                .map { i ->
                    async(Dispatchers.Default) {
                        repository.addToCart(LATTE_ID, sizes[i % sizes.size], 1)
                    }
                }
                .awaitAll()
        }

        val lines = database.cartDao().getCartLines()
        assertEquals(sizes.size, lines.size)
        assertEquals(CONCURRENT_ADDS, lines.sumOf { it.cartItem.quantity })
    }

    // ---- placeOrder is all-or-nothing -------------------------------------

    @Test
    fun placeOrderSnapshotsTheCartAndEmptiesIt() = runBlocking {
        seedMenu()
        repository.addToCart(LATTE_ID, DrinkSize.LARGE, 2)     // 450 base + 120 surcharge
        repository.addToCart(CROISSANT_ID, DrinkSize.SMALL, 1) // 380, not sizable

        // requireNotNull rather than assertNotNull: this needs to narrow to Long,
        // not just assert, because Pricing.orderNumber takes a non-null id.
        val orderId = requireNotNull(
            repository.placeOrder("  Sam  ", OrderType.DINE_IN, "  extra hot  ")
        ) { "placeOrder returned null for a non-empty cart" }

        val stored = requireNotNull(repository.observeOrder(orderId).first())
        val subtotal = (450 + 120) * 2 + 380

        assertEquals(subtotal, stored.order.subtotalCents)
        assertEquals(Pricing.taxOn(subtotal), stored.order.taxCents)
        assertEquals(subtotal + Pricing.taxOn(subtotal), stored.order.totalCents)
        assertEquals(Pricing.orderNumber(orderId), stored.order.orderNumber)
        assertEquals("Sam", stored.order.customerName)
        assertEquals("extra hot", stored.order.note)
        assertEquals(2, stored.lines.size)
        assertEquals(3, stored.itemCount)
        assertTrue(
            "the cart should be empty once the order lands",
            database.cartDao().getCartLines().isEmpty()
        )
    }

    @Test
    fun placeOrderReturnsNullOnAnEmptyCart() = runBlocking {
        assertNull(repository.placeOrder("Sam", OrderType.PICKUP, ""))
        assertTrue(database.orderDao().observeOrders().first().isEmpty())
    }

    /**
     * The bug this guards: the order row, its number, its lines and the cart
     * clear used to be four separate writes, so a failure after the first one
     * left a committed order with no lines behind.
     */
    @Test
    fun placeOrderLeavesNothingBehindWhenTheLineInsertFails() = runBlocking {
        assertNothingCommittedWhenFailingAt(FailingOrderDao.Point.LINES)
    }

    /** Same invariant, one write earlier: an order that never got its number. */
    @Test
    fun placeOrderLeavesNothingBehindWhenTheNumberUpdateFails() = runBlocking {
        assertNothingCommittedWhenFailingAt(FailingOrderDao.Point.ORDER_NUMBER)
    }

    private suspend fun assertNothingCommittedWhenFailingAt(point: FailingOrderDao.Point) {
        seedMenu()
        repository.addToCart(LATTE_ID, DrinkSize.MEDIUM, 2)

        val brittle = repositoryWith(FailingOrderDao(database.orderDao(), point))
        val thrown = runCatching {
            brittle.placeOrder("Sam", OrderType.PICKUP, "")
        }.exceptionOrNull()

        assertTrue(
            "expected the injected failure to propagate, got " + thrown,
            thrown is IllegalStateException
        )
        assertTrue(
            "a half-written order was committed",
            database.orderDao().observeOrders().first().isEmpty()
        )
        assertEquals(
            "the cart must survive a failed checkout",
            1,
            database.cartDao().getCartLines().size
        )
    }

    // ---- helpers ----------------------------------------------------------

    private fun repositoryWith(orderDao: OrderDao) = CafeRepository(
        database,
        database.menuDao(),
        database.cartDao(),
        orderDao
    )

    /** Explicit ids: Room only autogenerates when the id is left at 0. */
    private suspend fun seedMenu() {
        database.menuDao().insertAll(
            listOf(
                MenuItem(
                    id = LATTE_ID,
                    name = "Latte",
                    description = "Double shot with steamed milk.",
                    category = "Espresso",
                    priceCents = 450,
                    emoji = "L",
                    sizable = true
                ),
                MenuItem(
                    id = CROISSANT_ID,
                    name = "Croissant",
                    description = "Baked this morning.",
                    category = "Pastries",
                    priceCents = 380,
                    emoji = "C",
                    sizable = false
                )
            )
        )
    }

    private companion object {
        const val LATTE_ID = 1L
        const val CROISSANT_ID = 2L
        const val CONCURRENT_ADDS = 24
    }
}

/**
 * Delegates to the real DAO but throws at one chosen write, so a test can check
 * what [CafeRepository.placeOrder] leaves behind when it fails partway through.
 */
private class FailingOrderDao(
    private val delegate: OrderDao,
    private val failAt: Point
) : OrderDao {

    enum class Point { ORDER_NUMBER, LINES }

    override suspend fun insertOrder(order: OrderEntity): Long = delegate.insertOrder(order)

    override suspend fun setOrderNumber(orderId: Long, number: String) {
        if (failAt == Point.ORDER_NUMBER) throw IllegalStateException("injected failure")
        delegate.setOrderNumber(orderId, number)
    }

    override suspend fun insertLines(lines: List<OrderLine>) {
        if (failAt == Point.LINES) throw IllegalStateException("injected failure")
        delegate.insertLines(lines)
    }

    override fun observeOrders(): Flow<List<OrderWithLines>> = delegate.observeOrders()

    override fun observeOrder(orderId: Long): Flow<OrderWithLines?> = delegate.observeOrder(orderId)
}
