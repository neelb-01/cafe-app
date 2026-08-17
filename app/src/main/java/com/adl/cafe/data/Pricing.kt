package com.adl.cafe.data

import kotlin.math.roundToInt

/** Money rules kept in one place so the cart, checkout and receipt always agree. */
object Pricing {

    const val TAX_RATE = 0.085

    fun taxOn(subtotalCents: Int): Int = (subtotalCents * TAX_RATE).roundToInt()

    fun orderNumber(orderId: Long): String = "ADL-%04d".format(1000 + orderId)
}
