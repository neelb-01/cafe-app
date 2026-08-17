package com.adl.cafe.data

import com.adl.cafe.data.dao.CartDao
import com.adl.cafe.data.dao.MenuDao
import com.adl.cafe.data.dao.OrderDao
import com.adl.cafe.data.model.CartItem
import com.adl.cafe.data.model.CartLine
import com.adl.cafe.data.model.DrinkSize
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.data.model.OrderEntity
import com.adl.cafe.data.model.OrderLine
import com.adl.cafe.data.model.OrderType
import com.adl.cafe.data.model.OrderWithLines
import kotlinx.coroutines.flow.Flow

/**
 * The single place the UI talks to for data. ViewModels depend on this, never
 * on the DAOs directly, so the storage layer stays swappable.
 */
class CafeRepository(
    private val menuDao: MenuDao,
    private val cartDao: CartDao,
    private val orderDao: OrderDao
) {

    /** Populates the menu on first launch. Safe to call on every start. */
    suspend fun seedIfEmpty() {
        if (menuDao.count() == 0) {
            menuDao.insertAll(SeedData.menu)
        }
    }

    // ---- Menu -------------------------------------------------------------

    fun observeCategories(): Flow<List<String>> = menuDao.observeCategories()

    fun observeMenu(category: String?, query: String): Flow<List<MenuItem>> =
        menuDao.observeMenu(category, query.trim())

    fun observeMenuItem(id: Long): Flow<MenuItem?> = menuDao.observeById(id)

    // ---- Cart -------------------------------------------------------------

    fun observeCartLines(): Flow<List<CartLine>> = cartDao.observeCartLines()

    fun observeCartCount(): Flow<Int> = cartDao.observeCartCount()

    /** Adding an item already in the cart at the same size bumps its quantity. */
    suspend fun addToCart(menuItemId: Long, size: DrinkSize, quantity: Int) {
        val existing = cartDao.findLine(menuItemId, size)
        if (existing == null) {
            cartDao.insert(CartItem(menuItemId = menuItemId, size = size, quantity = quantity))
        } else {
            cartDao.update(existing.copy(quantity = existing.quantity + quantity))
        }
    }

    /** A quantity of zero or less removes the line entirely. */
    suspend fun setCartQuantity(cartItemId: Long, quantity: Int) {
        val lines = cartDao.getCartLines()
        val target = lines.firstOrNull { it.cartItem.id == cartItemId } ?: return
        if (quantity <= 0) {
            cartDao.delete(target.cartItem)
        } else {
            cartDao.update(target.cartItem.copy(quantity = quantity))
        }
    }

    suspend fun removeFromCart(cartItemId: Long) = cartDao.deleteById(cartItemId)

    suspend fun clearCart() = cartDao.clear()

    // ---- Orders -----------------------------------------------------------

    fun observeOrders(): Flow<List<OrderWithLines>> = orderDao.observeOrders()

    fun observeOrder(orderId: Long): Flow<OrderWithLines?> = orderDao.observeOrder(orderId)

    /**
     * Turns the current cart into an order and empties the cart.
     * Returns the new order id, or null if the cart was empty.
     */
    suspend fun placeOrder(
        customerName: String,
        orderType: OrderType,
        note: String
    ): Long? {
        val lines = cartDao.getCartLines()
        if (lines.isEmpty()) return null

        val subtotal = lines.sumOf { it.lineTotalCents }
        val tax = Pricing.taxOn(subtotal)

        val orderId = orderDao.insertOrder(
            OrderEntity(
                placedAtMillis = System.currentTimeMillis(),
                customerName = customerName.trim(),
                orderType = orderType,
                note = note.trim(),
                subtotalCents = subtotal,
                taxCents = tax,
                totalCents = subtotal + tax
            )
        )
        orderDao.setOrderNumber(orderId, Pricing.orderNumber(orderId))

        orderDao.insertLines(
            lines.map { line ->
                OrderLine(
                    orderId = orderId,
                    itemName = line.menuItem.name,
                    emoji = line.menuItem.emoji,
                    size = line.cartItem.size,
                    sizable = line.menuItem.sizable,
                    quantity = line.cartItem.quantity,
                    unitPriceCents = line.unitPriceCents
                )
            }
        )

        cartDao.clear()
        return orderId
    }
}
