package com.ratsey.thelist

import com.ratsey.thelist.data.Cheaper
import com.ratsey.thelist.data.Measure
import com.ratsey.thelist.data.compareOffers
import com.ratsey.thelist.data.composePerField
import com.ratsey.thelist.data.parsePriceBasis
import com.ratsey.thelist.data.splitPerField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfferDuelTest {

    @Test
    fun `thirty for twelve ninety nine beats twenty for nine ninety nine`() {
        val duel = requireNotNull(
            compareOffers(
                herePrice = 12.99,
                hereBasis = parsePriceBasis("30 count"),
                otherPrice = 9.99,
                otherBasis = parsePriceBasis("20 count"),
            ),
        )

        assertEquals(Cheaper.HERE, duel.cheaper)
        // 9.99 for 20 is 14.99 for 30, against 12.99.
        assertEquals(14.985, duel.otherPer, 0.001)
        assertEquals(12.99, duel.herePer, 0.001)
        assertEquals(0.133, duel.difference, 0.005)
    }

    @Test
    fun `a kilo beats a pound at the same number`() {
        val duel = requireNotNull(
            compareOffers(
                herePrice = 3.00,
                hereBasis = parsePriceBasis("lb"),
                otherPrice = 6.00,
                otherBasis = parsePriceBasis("kg"),
            ),
        )

        assertEquals(Cheaper.OTHER, duel.cheaper)
        assertEquals(2.7216, duel.otherPer, 0.001)
        assertEquals(0.093, duel.difference, 0.005)
    }

    @Test
    fun `an item and a pound cannot be compared`() {
        assertNull(
            compareOffers(
                herePrice = 2.00,
                hereBasis = parsePriceBasis("each"),
                otherPrice = 3.00,
                otherBasis = parsePriceBasis("lb"),
            ),
        )
    }

    @Test
    fun `the same price per the same thing is a draw`() {
        val duel = requireNotNull(
            compareOffers(
                herePrice = 2.50,
                hereBasis = parsePriceBasis("lb"),
                otherPrice = 2.50,
                otherBasis = parsePriceBasis("lb"),
            ),
        )

        assertEquals(Cheaper.SAME, duel.cheaper)
        assertEquals(0.0, duel.difference, 0.0001)
    }

    @Test
    fun `a bag of three pounds splits into a number and a unit and comes back whole`() {
        val (amount, unit) = splitPerField("3 lb")

        assertEquals("3", amount)
        assertEquals("lb", unit)
        assertEquals("3 lb", composePerField(amount, unit))
    }

    @Test
    fun `a plain count splits to its number and keeps it`() {
        val (amount, unit) = splitPerField("60")

        assertEquals("60", amount)
        assertEquals("count", unit)
        assertEquals("60", composePerField(amount, unit))
    }

    @Test
    fun `a unit the app has never heard of survives the round trip`() {
        val (amount, unit) = splitPerField("punnet")

        assertEquals("", amount)
        assertEquals("punnet", unit)
        assertEquals("punnet", composePerField(amount, unit))
    }

    @Test
    fun `a bare number in the per field is a count`() {
        val basis = requireNotNull(parsePriceBasis("60"))

        assertEquals(com.ratsey.thelist.data.Measure.COUNT, basis.measure)
        assertEquals(60.0, basis.quantity, 0.0)
        assertEquals("60", basis.label)
    }

    @Test
    fun `twelve for sixty against six for six is comparable`() {
        val duel = requireNotNull(
            compareOffers(
                herePrice = 8.12,
                hereBasis = parsePriceBasis("60"),
                otherPrice = 6.0,
                otherBasis = parsePriceBasis("6"),
            ),
        )

        // Six for six is sixty for sixty, against eight twelve.
        assertEquals(Cheaper.HERE, duel.cheaper)
        assertEquals(60.0, duel.otherPer, 0.01)
        assertEquals(0.865, duel.difference, 0.005)
    }

    @Test
    fun `a metre is a distance and converts with feet`() {
        val metre = requireNotNull(parsePriceBasis("m"))
        assertEquals(Measure.DISTANCE, metre.measure)

        // $3 the metre against $10 the foot: a metre is 3.28 feet, so the foot price is $32.81/m.
        val duel = requireNotNull(compareOffers(3.0, metre, 10.0, parsePriceBasis("ft")))

        assertEquals(Cheaper.HERE, duel.cheaper)
        assertEquals(32.808, duel.otherPer, 0.001)
    }

    @Test
    fun `a length is never weighed`() {
        assertNull(compareOffers(2.0, parsePriceBasis("m"), 3.0, parsePriceBasis("lb")))
    }

    @Test
    fun `prices with no unit still meet as bare money`() {
        val duel = requireNotNull(compareOffers(4.00, null, 5.00, null))

        assertEquals(Cheaper.HERE, duel.cheaper)
        assertEquals(0.2, duel.difference, 0.001)
    }
}
