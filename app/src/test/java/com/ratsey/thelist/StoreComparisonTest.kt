package com.ratsey.thelist

import com.ratsey.thelist.data.PricePoint
import com.ratsey.thelist.data.bestPerStore
import com.ratsey.thelist.data.parsePriceBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreComparisonTest {

    private val lb = requireNotNull(parsePriceBasis("lb"))
    private val kg = requireNotNull(parsePriceBasis("kg"))

    @Test
    fun `shops are ordered by price in the comparison unit`() {
        val best = bestPerStore(
            listOf(
                PricePoint(1L, 3.00, "Tesco", lb),
                PricePoint(2L, 6.00, "Aldi", kg),
            ),
            kg,
        )

        assertEquals(listOf("Aldi", "Tesco"), best.map { it.store })
        assertEquals(6.00, best[0].price, 0.001)
        assertEquals(6.61, best[1].price, 0.01)
        assertTrue(best[0].cheapest)
        assertFalse(best[1].cheapest)
    }

    @Test
    fun `a shop counts once at its own best price`() {
        val best = bestPerStore(
            listOf(
                PricePoint(1L, 8.00, "Aldi", kg),
                PricePoint(2L, 6.00, "Aldi", kg),
                PricePoint(3L, 7.00, "Aldi", kg),
            ),
            kg,
        )

        assertEquals(1, best.size)
        assertEquals(6.00, best[0].price, 0.001)
        assertEquals(2L, best[0].at)
        assertEquals(3, best[0].purchases)
    }

    @Test
    fun `prices per something else are left out rather than compared raw`() {
        val best = bestPerStore(
            listOf(
                PricePoint(1L, 3.00, "Tesco", lb),
                PricePoint(2L, 99.00, "Unknown", null),
            ),
            kg,
        )

        assertEquals(listOf("Tesco"), best.map { it.store })
    }

    @Test
    fun `no unit at all is still comparable`() {
        val best = bestPerStore(
            listOf(
                PricePoint(1L, 2.50, "Tesco", null),
                PricePoint(2L, 2.20, "Aldi", null),
            ),
            null,
        )

        assertEquals(listOf("Aldi", "Tesco"), best.map { it.store })
        assertTrue(best[0].cheapest)
    }
}
