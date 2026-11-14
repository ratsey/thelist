package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PricingTest {

    @Test
    fun `a bare unit is one of them`() {
        val lb = parsePriceBasis("lb")!!
        assertEquals(Measure.WEIGHT, lb.measure)
        assertEquals(1.0, lb.quantity, 0.0001)
        assertEquals("lb", lb.label)
    }

    @Test
    fun `a quantity and a unit are both read`() {
        val per100g = parsePriceBasis("100 g")!!
        assertEquals(Measure.WEIGHT, per100g.measure)
        assertEquals(100.0, per100g.quantity, 0.0001)
        assertEquals("100 g", per100g.label)
    }

    @Test
    fun `per is not part of the unit`() {
        assertEquals("lb", parsePriceBasis("per lb")!!.unit)
        assertEquals("each", parsePriceBasis("  EACH ")!!.unit)
    }

    @Test
    fun `pounds become kilos and back`() {
        val perLb = parsePriceBasis("lb")!!
        val perKg = parsePriceBasis("kg")!!
        val perKgPrice = perLb.convert(2.99, perKg)!!
        assertEquals(6.5914, perKgPrice, 0.001)
        assertEquals(2.99, perKg.convert(perKgPrice, perLb)!!, 0.001)
    }

    @Test
    fun `weight and volume are never compared`() {
        assertNull(parsePriceBasis("lb")!!.convert(2.99, parsePriceBasis("L")!!))
    }

    @Test
    fun `an unknown unit is only compared with itself`() {
        val punnet = parsePriceBasis("punnet")!!
        assertEquals(Measure.OTHER, punnet.measure)
        assertNull(punnet.convert(1.99, parsePriceBasis("each")!!))
        assertEquals(1.99, punnet.convert(1.99, punnet)!!, 0.0001)
    }

    @Test
    fun `money with nothing per is not a basis`() {
        assertNull(parsePriceBasis(null))
        assertNull(parsePriceBasis("   "))
    }

    @Test
    fun `history is per product and per type`() {
        assertEquals("milk", historyKey("milk", null))
        assertEquals("milk", historyKey("milk", "   "))
        assertEquals("milk|semi skimmed", historyKey("milk", "Semi Skimmed"))
    }

    @Test
    fun `a countable pack is a count`() {
        val pod = parsePriceBasis("pod")!!
        assertEquals(Measure.COUNT, pod.measure)
        // A pod, an each and a count are one price written three ways.
        assertEquals(0.30, pod.convert(0.30, parsePriceBasis("each")!!)!!, 0.0001)
        assertEquals(0.30, pod.convert(0.30, parsePriceBasis("count")!!)!!, 0.0001)
    }

    @Test
    fun `a price per n divides by n`() {
        val perTub = parsePriceBasis("30 count")!!
        assertEquals(30.0, perTub.quantity, 0.0001)
        assertEquals(0.433, perTub.convert(12.99, parsePriceBasis("count")!!)!!, 0.001)
        assertEquals("30 count", perTub.label)
    }

    @Test
    fun `counts are not weighed`() {
        assertNull(parsePriceBasis("count")!!.convert(0.30, parsePriceBasis("lb")!!))
    }
}
