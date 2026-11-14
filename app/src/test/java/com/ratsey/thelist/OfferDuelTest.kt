package com.ratsey.thelist

import com.ratsey.thelist.data.Cheaper
import com.ratsey.thelist.data.compareOffers
import com.ratsey.thelist.data.parsePriceBasis
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
    fun `prices with no unit still meet as bare money`() {
        val duel = requireNotNull(compareOffers(4.00, null, 5.00, null))

        assertEquals(Cheaper.HERE, duel.cheaper)
        assertEquals(0.2, duel.difference, 0.001)
    }
}
