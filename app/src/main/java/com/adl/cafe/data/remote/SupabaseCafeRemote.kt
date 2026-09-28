package com.adl.cafe.data.remote

import com.adl.cafe.data.model.CartLine
import com.adl.cafe.data.model.DrinkSize
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.data.model.OrderType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [CafeRemote] over Supabase. The tables, grants and the `place_order` function
 * this talks to are defined in `supabase/schema.sql`.
 */
class SupabaseCafeRemote(private val client: SupabaseClient) : CafeRemote {

    override suspend fun fetchMenu(): List<MenuItem> =
        client.from(TABLE_MENU).select().decodeList<RemoteMenuItem>().map { it.toMenuItem() }

    override suspend fun placeOrder(
        customerName: String,
        orderType: OrderType,
        note: String,
        lines: List<CartLine>
    ): PlacedOrder {
        val params = PlaceOrderParams(
            customerName = customerName,
            orderType = orderType,
            note = note,
            items = lines.map { line ->
                OrderItemParam(
                    menuItemId = line.cartItem.menuItemId,
                    size = line.cartItem.size,
                    quantity = line.cartItem.quantity
                )
            }
        )
        return client.postgrest.rpc(FUNCTION_PLACE_ORDER, params).decodeAs()
    }

    companion object {
        private const val TABLE_MENU = "menu_items"
        private const val FUNCTION_PLACE_ORDER = "place_order"

        fun create(url: String, key: String): SupabaseCafeRemote =
            SupabaseCafeRemote(createSupabaseClient(url, key) { install(Postgrest) })
    }
}

/** A `menu_items` row. */
@Serializable
private data class RemoteMenuItem(
    val id: Long,
    val name: String,
    val description: String,
    val category: String,
    @SerialName("price_cents") val priceCents: Int,
    val emoji: String,
    val sizable: Boolean,
    @SerialName("is_popular") val isPopular: Boolean,
    @SerialName("is_available") val isAvailable: Boolean
) {
    fun toMenuItem() = MenuItem(
        id = id,
        name = name,
        description = description,
        category = category,
        priceCents = priceCents,
        emoji = emoji,
        sizable = sizable,
        isPopular = isPopular,
        isAvailable = isAvailable
    )
}

/** Parameter names must match the `place_order` signature exactly. */
@Serializable
private data class PlaceOrderParams(
    @SerialName("p_customer_name") val customerName: String,
    @SerialName("p_order_type") val orderType: OrderType,
    @SerialName("p_note") val note: String,
    @SerialName("p_items") val items: List<OrderItemParam>
)

/** Ids, sizes and quantities only: the server looks the prices up itself. */
@Serializable
private data class OrderItemParam(
    @SerialName("menu_item_id") val menuItemId: Long,
    val size: DrinkSize,
    val quantity: Int
)
