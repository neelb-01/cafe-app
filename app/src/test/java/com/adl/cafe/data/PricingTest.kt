package com.adl.cafe.data

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM tests for the money rules. [Pricing] deliberately holds no Android
 * types so these run under `./gradlew test` with no device.
 */
class PricingTest {

    private lateinit var originalLocale: Locale

    @Before
    fun fixLocale() {
        // taxRateLabel formats through the default locale.
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // ---- taxOn ------------------------------------------------------------

    @Test
    fun `tax on an empty subtotal is zero`() {
        assertEquals(0, Pricing.taxOn(0))
    }

    @Test
    fun `tax is eight and a half percent`() {
        // $100.00 of coffee attracts exactly $8.50 of tax.
        assertEquals(850, Pricing.taxOn(10_000))
        assertEquals(27, Pricing.taxOn(320))
        assertEquals(105, Pricing.taxOn(1_234))
    }

    @Test
    fun `an exact half cent rounds up`() {
        // 8.5% of 100c is 8.5c exactly — the boundary the old Double path relied
        // on roundToInt for. 300c gives 25.5c, the same case one step further out.
        assertEquals(9, Pricing.taxOn(100))
        assertEquals(26, Pricing.taxOn(300))
    }

    @Test
    fun `tax is monotonic in the subtotal`() {
        var previous = 0
        for (cents in 0..20_000) {
            val tax = Pricing.taxOn(cents)
            assertTrue("tax went backwards at $cents cents", tax >= previous)
            previous = tax
        }
    }

    @Test
    fun `a subtotal at the top of the Int range does not overflow`() {
        // subtotal * 850 leaves Int range well before this; the multiply has to
        // happen in Long or the result comes back negative.
        val tax = Pricing.taxOn(Int.MAX_VALUE)
        assertTrue("tax overflowed to $tax", tax > 0)
        assertEquals(182_536_110, tax)
    }

    // ---- taxRateLabel -----------------------------------------------------

    @Test
    fun `the displayed rate matches the rate actually charged`() {
        assertEquals("8.5%", Pricing.taxRateLabel)
        // Same claim, stated as arithmetic: 8.5% of $100.00 is $8.50.
        assertEquals(850, Pricing.taxOn(10_000))
    }
}
