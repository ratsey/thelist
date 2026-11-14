package com.ratsey.thelist.data

import com.ratsey.thelist.receipt.ItemMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceSummaryTest {

    private fun row(
        name: String,
        price: Double,
        at: Long,
        variant: String? = null,
        unit: String? = null,
    ) = PriceRow(
        productKey = ItemMatcher.productKey(name),
        displayName = name,
        quantity = 1.0,
        unitPrice = price,
        lineTotal = price,
        storeName = "Walmart",
        purchasedAt = at,
        currency = DEFAULT_CURRENCY,
        receiptId = "r$at",
        variant = variant,
        priceUnit = unit,
    )

    @Test
    fun `whole and semi skimmed are different histories`() {
        val summaries = ReceiptRepository.summarise(
            listOf(
                row("milk", 2.0, 1, variant = "whole"),
                row("milk", 3.0, 2, variant = "semi skimmed"),
            ),
        )
        assertEquals(2, summaries.size)
        assertEquals(2.0, summaries.first { it.variant == "whole" }.latestPrice, 0.001)
        assertEquals(3.0, summaries.first { it.variant == "semi skimmed" }.latestPrice, 0.001)
    }

    @Test
    fun `best is won in the same unit, not the same number`() {
        val summary = ReceiptRepository.summarise(
            listOf(
                row("chicken thighs", 2.99, 1, unit = "lb"),
                row("chicken thighs", 5.00, 2, unit = "kg"),
            ),
        ).single()
        // $2.99/lb is $6.59/kg, so the kilo at $5.00 really is the better price.
        assertEquals(5.00, summary.latestPrice, 0.001)
        assertEquals(5.00, summary.lowestPrice, 0.001)
        assertEquals("kg", summary.unitLabel)
    }

    @Test
    fun `weight is not compared with volume`() {
        val summary = ReceiptRepository.summarise(
            listOf(
                row("milk", 1.50, 1, unit = "L"),
                row("milk", 9.00, 2, unit = "lb"),
            ),
        ).single()
        assertEquals(9.00, summary.latestPrice, 0.001)
        assertEquals(9.00, summary.lowestPrice, 0.001)
        assertEquals(2, summary.timesBought)
    }

    @Test
    fun `money with no unit is compared as plain money`() {
        val summary = ReceiptRepository.summarise(
            listOf(row("bread", 3.0, 1), row("bread", 2.0, 2)),
        ).single()
        assertEquals(2.0, summary.latestPrice, 0.001)
        assertEquals(2.0, summary.lowestPrice, 0.001)
        assertNull(summary.unitLabel)
    }

    @Test
    fun `a tub of thirty is compared per count`() {
        val summary = ReceiptRepository.summarise(
            listOf(
                row("laundry pods", 0.30, 1, unit = "pod"),
                row("laundry pods", 12.99, 2, unit = "30 count"),
            ),
        ).single()
        // A pod is $9.00 per thirty, so loose pods are the cheaper way to buy them.
        assertEquals(12.99, summary.latestPrice, 0.001)
        assertEquals(9.00, summary.lowestPrice, 0.001)
        assertEquals("30 count", summary.unitLabel)
    }
}
