package com.ratsey.thelist

import com.ratsey.thelist.data.Cheaper
import com.ratsey.thelist.data.Measure
import com.ratsey.thelist.data.compareOffers
import com.ratsey.thelist.data.composePerField
import com.ratsey.thelist.data.parsePriceBasis
import com.ratsey.thelist.data.splitPerField
import com.ratsey.thelist.data.priceIn
import com.ratsey.thelist.data.unitPriceOf
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
    fun `a three pound bag at three dollars is a dollar a pound`() {
        val one = requireNotNull(unitPriceOf(3.0, "3 lb"))

        assertEquals(1.0, one.per, 0.0001)
        assertEquals("lb", one.label)
    }

    @Test
    fun `sixty for eight twelve is fourteen cents each`() {
        val one = requireNotNull(unitPriceOf(8.12, "60"))

        assertEquals(0.1353, one.per, 0.0005)
        assertEquals("each", one.label)
    }

    @Test
    fun `a price with no unit has no rate`() {
        assertNull(unitPriceOf(2.5, null))
        assertNull(unitPriceOf(null, "lb"))
    }

    @Test
    fun `a count, a unit and an each are one and the same thing`() {
        val count = requireNotNull(parsePriceBasis("1 count"))
        val unit = requireNotNull(parsePriceBasis("unit"))
        val each = requireNotNull(parsePriceBasis("each"))

        assertEquals(Measure.COUNT, count.measure)
        assertEquals(Measure.COUNT, unit.measure)
        assertEquals(Measure.COUNT, each.measure)
        // One name for one thing, whichever word was typed.
        assertEquals("each", count.label)
        assertEquals("each", unit.label)
        assertEquals("each", each.label)

        // $2 each, $2 the unit, $2 the count: the same offer, twice.
        assertEquals(Cheaper.SAME, requireNotNull(compareOffers(2.0, each, 2.0, unit)).cheaper)
        assertEquals(Cheaper.SAME, requireNotNull(compareOffers(2.0, unit, 2.0, count)).cheaper)
    }

    @Test
    fun `six of them is half a dollar off a dollar each`() {
        val six = requireNotNull(parsePriceBasis("6 count"))
        val each = requireNotNull(parsePriceBasis("each"))

        // $3 for six is 50 cents each, and 50 cents each is $3 for six.
        assertEquals(0.5, priceIn(3.0, six, each)!!, 0.0001)
        assertEquals(3.0, priceIn(0.5, each, six)!!, 0.0001)
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
        assertEquals(Measure.LENGTH, metre.measure)

        // $3 the metre against $10 the foot: a metre is 3.28 feet, so the foot price is $32.81/m.
        val duel = requireNotNull(compareOffers(3.0, metre, 10.0, parsePriceBasis("ft")))

        assertEquals(Cheaper.HERE, duel.cheaper)
        assertEquals(32.808, duel.otherPer, 0.001)
    }

    @Test
    fun `a roll of fifty yards is compared with a roll of thirty metres`() {
        // 30 m is 32.8 yd, so the 30 m roll is $12.19 worth of tape for every 50 yd on the left.
        val duel = requireNotNull(
            compareOffers(
                herePrice = 12.99,
                hereBasis = parsePriceBasis("50 yd"),
                otherPrice = 8.0,
                otherBasis = parsePriceBasis("30 m"),
            ),
        )

        assertEquals(Cheaper.OTHER, duel.cheaper)
        assertEquals(12.192, duel.otherPer, 0.01)
        assertEquals(45.72, requireNotNull(parsePriceBasis("50 yd")).quantity * 0.9144, 0.001)
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
