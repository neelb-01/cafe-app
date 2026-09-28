package com.adl.cafe.data.model

/**
 * Cup sizes. [surchargeCents] is added on top of the menu item's base price.
 * Food items (see [MenuItem.sizable]) are always ordered as [SMALL].
 *
 * `place_order` in `supabase/schema.sql` repeats these surcharges when it
 * prices an order; change both together.
 */
enum class DrinkSize(val label: String, val surchargeCents: Int) {
    SMALL("Small", 0),
    MEDIUM("Medium", 60),
    LARGE("Large", 120)
}

enum class OrderType(val label: String) {
    PICKUP("Pickup"),
    DINE_IN("Dine in")
}

enum class OrderStatus(val label: String) {
    PLACED("Placed"),
    PREPARING("Preparing"),
    READY("Ready"),
    COMPLETED("Completed")
}
