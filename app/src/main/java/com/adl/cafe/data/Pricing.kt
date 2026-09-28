package com.adl.cafe.data

import java.text.NumberFormat

/** Money rules kept in one place so the cart, checkout and receipt always agree. */
object Pricing {

    /**
     * Tax rate in basis points (850 = 8.5%). Held as an Int so [taxOn] can stay
     * in integer arithmetic — cents never pass through a Double.
     *
     * The server charges its own copy of this rate (`place_order` in
     * `supabase/schema.sql`); change both together. The rate here drives the
     * checkout estimate, the server's drives the stored receipt.
     */
    const val TAX_BASIS_POINTS = 850

    private const val BASIS_POINT_SCALE = 10_000

    /**
     * Tax on a subtotal, rounded half-up. Widened to Long for the multiply so a
     * large cart cannot overflow before the divide brings it back into range.
     */
    fun taxOn(subtotalCents: Int): Int =
        ((subtotalCents.toLong() * TAX_BASIS_POINTS + BASIS_POINT_SCALE / 2) /
            BASIS_POINT_SCALE).toInt()

    /**
     * "8.5%" for the tax row's label, derived from [TAX_BASIS_POINTS] so the
     * displayed rate cannot drift from the one actually charged. The Double here
     * is display formatting only and never touches a price.
     */
    val taxRateLabel: String
        get() = NumberFormat.getPercentInstance()
            .apply { maximumFractionDigits = 2 }
            .format(TAX_BASIS_POINTS.toDouble() / BASIS_POINT_SCALE)
}
