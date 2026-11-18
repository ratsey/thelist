package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
        assertEquals("30", perTub.label)
    }

    @Test
    fun `counts are not weighed`() {
        assertNull(parsePriceBasis("count")!!.convert(0.30, parsePriceBasis("lb")!!))
    }

    @Test
    fun `every offered comparison unit is a real measure`() {
        MeasureSystem.entries.forEach { system ->
            comparisonUnits(system).forEach { unit ->
                assertNotEquals(Measure.OTHER, parsePriceBasis(unit)!!.measure)
            }
        }
    }

    @Test
    fun `each system offers its own measures and always counts`() {
        val metric = comparisonUnits(MeasureSystem.Metric)
        val imperial = comparisonUnits(MeasureSystem.Imperial)
        assertTrue(metric.contains("kg"))
        assertTrue(metric.contains("L"))
        assertTrue(imperial.contains("lb"))
        assertTrue(imperial.contains("fl oz"))
        assertTrue(imperial.contains("gal"))
        // Both systems are always offered; the setting only decides the order, because a shopper
        // here thinks in pounds and buys in kilos.
        assertEquals(metric.toSet(), imperial.toSet())
        assertTrue(metric.indexOf("g") < metric.indexOf("oz"))
        assertTrue(imperial.indexOf("oz") < imperial.indexOf("g"))
        // The gallon is ordered with the measures it lives between: after fl oz in the US aisle,
        // after the metric pair in the metric one.
        assertTrue(imperial.indexOf("gal") > imperial.indexOf("fl oz"))
        assertTrue(metric.indexOf("gal") > metric.indexOf("L"))
    }

    @Test
    fun `a gallon price meets a litre price in either direction`() {
        val perGal = parsePriceBasis("gal")!!
        val perL = parsePriceBasis("L")!!
        // $1.00/L restated per gallon: 3.7854… litres to the gallon.
        assertEquals(3.7854, perL.convert(1.00, perGal)!!, 0.001)
        assertEquals(1.00, perGal.convert(3.7854, perL)!!, 0.001)
    }

    @Test
    fun `the per hint speaks the chosen system`() {
        assertTrue(perFieldHint(MeasureSystem.Metric).contains("kg"))
        assertTrue(perFieldHint(MeasureSystem.Imperial).contains("lb"))
    }

    @Test
    fun `currency is cleaned but not guessed`() {
        assertEquals("€", normaliseCurrency("  € "))
        assertEquals("US$", normaliseCurrency("US$"))
        assertEquals(DEFAULT_CURRENCY, normaliseCurrency(""))
        assertEquals(DEFAULT_CURRENCY, normaliseCurrency(null))
    }

    @Test
    fun `an unreadable measure system falls back instead of failing`() {
        assertEquals(MeasureSystem.Imperial, MeasureSystem.fromStored(null))
        assertEquals(MeasureSystem.Imperial, MeasureSystem.fromStored("furlongs"))
        assertEquals(MeasureSystem.Metric, MeasureSystem.fromStored("metric"))
    }
}
