package com.adl.cafe.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adl.cafe.data.dao.OrderDao
import com.adl.cafe.data.model.CartLine
import com.adl.cafe.data.model.DrinkSize
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.data.model.OrderEntity
import com.adl.cafe.data.model.OrderLine
import com.adl.cafe.data.model.OrderType
import com.adl.cafe.data.model.OrderWithLines
import com.adl.cafe.data.remote.CafeRemote
import com.adl.cafe.data.remote.PlacedLine
import com.adl.cafe.data.remote.PlacedOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * tests want an empty in-memory database and nothing else. The backend is a
 * [FakeCafeRemote], so nothing here touches the network.
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
    private val remote = FakeCafeRemote()

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
        // not just assert, because observeOrder takes a non-null id.
        val orderId = requireNotNull(
            repository.placeOrder("  Sam  ", OrderType.DINE_IN, "  extra hot  ")
        ) { "placeOrder returned null for a non-empty cart" }

        val stored = requireNotNull(repository.observeOrder(orderId).first())
        val subtotal = (450 + 120) * 2 + 380

        assertEquals("the server gets trimmed input", "Sam", remote.lastCustomerName)
        assertEquals(subtotal, stored.order.subtotalCents)
        assertEquals(Pricing.taxOn(subtotal), stored.order.taxCents)
        assertEquals(subtotal + Pricing.taxOn(subtotal), stored.order.totalCents)
        assertEquals("the number is the server's", "ADL-1001", stored.order.orderNumber)
        assertEquals("Sam", stored.order.customerName)
        assertEquals("extra hot", stored.order.note)
        assertEquals(2, stored.lines.size)
        assertEquals(3, stored.itemCount)
        assertTrue(
            "the cart should be empty once the order lands",
            database.cartDao().getCartLines().isEmpty()
        )
    }

    /**
     * The receipt must be what the server charged. If the menu changed since
     * the last refresh, the server's price wins over the cart's stale one.
     */
    @Test
    fun placeOrderStoresTheServersPricesNotTheCarts() = runBlocking {
        seedMenu()
        repository.addToCart(LATTE_ID, DrinkSize.SMALL, 1)
        remote.priceOverrideCents = 999

        val orderId = requireNotNull(repository.placeOrder("Sam", OrderType.PICKUP, ""))
        val stored = requireNotNull(repository.observeOrder(orderId).first())

        assertEquals(999, stored.lines.single().unitPriceCents)
        assertEquals(999, stored.order.subtotalCents)
    }

    @Test
    fun placeOrderReturnsNullOnAnEmptyCart() = runBlocking {
        assertNull(repository.placeOrder("Sam", OrderType.PICKUP, ""))
        assertTrue(database.orderDao().observeOrders().first().isEmpty())
        assertEquals("an empty cart never reaches the server", 0, remote.ordersPlaced)
    }

    /** Offline, or rejected: the error surfaces and the cart is kept for a retry. */
    @Test
    fun placeOrderKeepsTheCartWhenTheServerFails() = runBlocking {
        seedMenu()
        repository.addToCart(LATTE_ID, DrinkSize.MEDIUM, 2)
        remote.failOrders = true

        val thrown = runCatching {
            repository.placeOrder("Sam", OrderType.PICKUP, "")
        }.exceptionOrNull()

        assertTrue("expected the server failure to propagate", thrown is IllegalStateException)
        assertTrue(database.orderDao().observeOrders().first().isEmpty())
        assertEquals(1, database.cartDao().getCartLines().size)
    }

    /**
     * The bug this guards: the order row, its lines and the cart clear used to
     * be separate writes, so a failure after the first one left a committed
     * order with no lines behind.
     */
    @Test
    fun placeOrderLeavesNothingBehindWhenTheLineInsertFails() = runBlocking {
        seedMenu()
        repository.addToCart(LATTE_ID, DrinkSize.MEDIUM, 2)

        val brittle = repositoryWith(FailingOrderDao(database.orderDao()))
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

    // ---- refreshMenu --------------------------------------------------------

    @Test
    fun refreshMenuCopiesTheServerMenuAndDropsRemovedItems() = runBlocking {
        seedMenu()
        remote.menu = listOf(
            latte().copy(priceCents = 500),
            MenuItem(
                id = 3,
                name = "Mocha",
                description = "Chocolate.",
                category = "Espresso",
                priceCents = 495,
                emoji = "M"
            )
        )

        repository.refreshMenu()

        val menu = repository.observeMenu(null, "").first()
        assertEquals(setOf(LATTE_ID, 3L), menu.map { it.id }.toSet())
        assertEquals(500, menu.single { it.id == LATTE_ID }.priceCents)
        assertFalse("the croissant was removed server-side", menu.any { it.id == CROISSANT_ID })
    }

    /**
     * Upsert, not REPLACE: a delete-and-reinsert of the menu row would cascade
     * through the cart's foreign key and silently empty the cart.
     */
    @Test
    fun refreshMenuKeepsCartLinesAndRepricesThem() = runBlocking {
        seedMenu()
        repository.addToCart(LATTE_ID, DrinkSize.SMALL, 2)
        remote.menu = listOf(latte().copy(priceCents = 500), croissant())

        repository.refreshMenu()

        val line = database.cartDao().getCartLines().single()
        assertEquals(2, line.cartItem.quantity)
        assertEquals(500, line.unitPriceCents)
    }

    @Test
    fun refreshMenuIgnoresAnEmptyResponse() = runBlocking {
        seedMenu()
        remote.menu = emptyList()

        repository.refreshMenu()

        assertEquals(2, database.menuDao().count())
    }

    // ---- helpers ----------------------------------------------------------

    private fun repositoryWith(orderDao: OrderDao) = CafeRepository(
        database,
        database.menuDao(),
        database.cartDao(),
        orderDao,
        remote
    )

    /** Explicit ids: Room only autogenerates when the id is left at 0. */
    private suspend fun seedMenu() {
        database.menuDao().insertAll(listOf(latte(), croissant()))
    }

    private fun latte() = MenuItem(
        id = LATTE_ID,
        name = "Latte",
        description = "Double shot with steamed milk.",
        category = "Espresso",
        priceCents = 450,
        emoji = "L",
        sizable = true
    )

    private fun croissant() = MenuItem(
        id = CROISSANT_ID,
        name = "Croissant",
        description = "Baked this morning.",
        category = "Pastries",
        priceCents = 380,
        emoji = "C",
        sizable = false
    )

    private companion object {
        const val LATTE_ID = 1L
        const val CROISSANT_ID = 2L
        const val CONCURRENT_ADDS = 24
    }
}

/**
 * Delegates to the real DAO but throws on the line insert, so a test can check
 * what [CafeRepository.placeOrder] leaves behind when it fails partway through.
 */
private class FailingOrderDao(private val delegate: OrderDao) : OrderDao {

    override suspend fun insertOrder(order: OrderEntity): Long = delegate.insertOrder(order)

    override suspend fun insertLines(lines: List<OrderLine>) {
        throw IllegalStateException("injected failure")
    }

    override fun observeOrders(): Flow<List<OrderWithLines>> = delegate.observeOrders()

    override fun observeOrder(orderId: Long): Flow<OrderWithLines?> = delegate.observeOrder(orderId)
}

/**
 * Stands in for Supabase. Prices and numbers orders the way `place_order` in
 * `supabase/schema.sql` does, from whatever menu the cart lines carry.
 */
private class FakeCafeRemote : CafeRemote {

    var menu: List<MenuItem> = emptyList()
    var failOrders = false

    /** When set, every line is charged this instead of its menu price. */
    var priceOverrideCents: Int? = null

    var ordersPlaced = 0
        private set
    var lastCustomerName: String? = null
        private set

    override suspend fun fetchMenu(): List<MenuItem> = menu

    override suspend fun placeOrder(
        customerName: String,
        orderType: OrderType,
        note: String,
        lines: List<CartLine>
    ): PlacedOrder {
        if (failOrders) throw IllegalStateException("server unavailable")
        ordersPlaced++
        lastCustomerName = customerName

        val placedLines = lines.map { line ->
            PlacedLine(
                itemName = line.menuItem.name,
                emoji = line.menuItem.emoji,
                size = line.cartItem.size,
                sizable = line.menuItem.sizable,
                quantity = line.cartItem.quantity,
                unitPriceCents = priceOverrideCents ?: line.unitPriceCents
            )
        }
        val subtotal = placedLines.sumOf { it.unitPriceCents * it.quantity }
        val tax = Pricing.taxOn(subtotal)
        return PlacedOrder(
            orderNumber = "ADL-" + (1000 + ordersPlaced),
            placedAtMillis = 1_700_000_000_000,
            subtotalCents = subtotal,
            taxCents = tax,
            totalCents = subtotal + tax,
            lines = placedLines
        )
    }
}
