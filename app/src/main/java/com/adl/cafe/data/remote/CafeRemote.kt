package com.adl.cafe.data.remote

import com.adl.cafe.data.model.CartLine
import com.adl.cafe.data.model.DrinkSize
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.data.model.OrderType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The backend, as [com.adl.cafe.data.CafeRepository] sees it. An interface so
 * the repository tests can stand in a fake instead of hitting the network.
 */
interface CafeRemote {

    /** The full menu, unavailable items included (they carry `isAvailable = false`). */
    suspend fun fetchMenu(): List<MenuItem>

    /**
     * Submits the cart. The server prices it from its own menu and assigns the
     * order number, so the returned [PlacedOrder] is the receipt of record.
     * Throws if the order was not accepted.
     */
    suspend fun placeOrder(
        customerName: String,
        orderType: OrderType,
        note: String,
        lines: List<CartLine>
    ): PlacedOrder
}

/** What `place_order` in `supabase/schema.sql` returns. */
@Serializable
data class PlacedOrder(
    @SerialName("order_number") val orderNumber: String,
    @SerialName("placed_at_millis") val placedAtMillis: Long,
    @SerialName("subtotal_cents") val subtotalCents: Int,
    @SerialName("tax_cents") val taxCents: Int,
    @SerialName("total_cents") val totalCents: Int,
    val lines: List<PlacedLine>
)

@Serializable
data class PlacedLine(
    @SerialName("item_name") val itemName: String,
    val emoji: String,
    val size: DrinkSize,
    val sizable: Boolean,
    val quantity: Int,
    @SerialName("unit_price_cents") val unitPriceCents: Int
)
