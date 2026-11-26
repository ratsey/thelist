package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The lines are transcribed from a real Walmart digital shelf label: a Bertolli olive oil tag
 * showing $7.52 retail, $7.12 a pint, and the shelf's own modular position. Recognition tends to
 * hand back these as separate lines, with the retail figure much taller than the rest.
 */
class ShelfTagTest {

    private fun lines(vararg text: String) = text.map { TagLine(it, height = 0) }

    @Test
    fun `the retail price is the big figure, not the rate beside it`() {
        val tag = readShelfTag(
            listOf(
                TagLine("BERTOLI OO SUSTAIN", 24),
                TagLine("RETAIL PRICE", 22),
                TagLine("$7.52", 88),
                TagLine("UNIT PRICE", 20),
                TagLine("$7.12", 44),
                TagLine("PER PT", 18),
                TagLine("SNAP", 16),
                TagLine("FAC 1 CAP 8 0302", 16),
            ),
        )

        assertEquals(7.52, tag?.price!!, 0.0001)
        // No size is printed, so the size is the one the two figures imply: $7.52 at $7.12 a pint.
        assertEquals("1.056 pt", tag.pack)
        assertEquals(7.12, unitPriceOf(tag.price, tag.pack)!!.per, 0.01)
        assertEquals(7.12, tag.rate!!.per, 0.0001)
        assertEquals("pt", tag.rate!!.label)
    }

    @Test
    fun `a pack size beside the name is kept when the app knows the unit`() {
        val tag = readShelfTag(
            lines(
                "BERTOLI OO SUSTAIN 17 OZ",
                "RETAIL PRICE",
                "$7.52",
                "UNIT PRICE",
                "$7.12",
                "PER PT",
            ),
        )

        assertEquals(7.52, tag?.price!!, 0.0001)
        assertEquals("17 oz", tag.pack)
    }

    @Test
    fun `a dollar sign read as an S still reads as a price`() {
        val tag = readShelfTag(lines("RETAIL PRICE", "S7.52", "UNIT PRICE", "S7.12", "PER PT"))

        assertEquals(7.52, tag?.price!!, 0.0001)
    }

    @Test
    fun `a rate printed larger than the retail price is still not the price`() {
        val tag = readShelfTag(
            listOf(
                TagLine("GREAT VALUE COFFEE", 20),
                TagLine("UNIT PRICE $7.12", 90),
                TagLine("RETAIL PRICE", 22),
                TagLine("$9.98", 30),
            ),
        )

        assertEquals(9.98, tag?.price!!, 0.0001)
    }

    @Test
    fun `bare numbers on the label are not prices`() {
        val tag = readShelfTag(
            lines("BERTOLI OO SUSTAIN", "SNAP", "FAC 1 CAP 8 0302", "35"),
        )

        assertNull(tag)
    }

    @Test
    fun `a narrow tag read as one line still separates the price from the rate`() {
        val tag = readShelfTag(
            lines("UNIT PRICE $7.12 RETAIL PRICE $7.52"),
        )

        assertEquals(7.52, tag?.price!!, 0.0001)
    }

    @Test
    fun `the rate and the price on one line are both read`() {
        val tag = readShelfTag(lines("RETAIL PRICE $7.52 UNIT PRICE $7.12 PER PT"))

        assertEquals(7.52, tag?.price!!, 0.0001)
        assertEquals(7.12, tag.rate!!.per, 0.0001)
        assertEquals("pt", tag.rate!!.label)
    }

    @Test
    fun `raised cents read with a space are still the price`() {
        // What the photographed Bertoli tag looks like to recognition: the cents are printed small
        // and high, so the decimal point comes back as a space.
        val tag = readShelfTag(
            listOf(
                TagLine("BERTOLI OO SUSTAIN", 24),
                TagLine("35", 22),
                TagLine("RETAIL PRICE", 22),
                TagLine("$7 52", 88),
                TagLine("UNIT PRICE", 20),
                TagLine("$7 12", 44),
                TagLine("PER", 18),
                TagLine("PT", 18),
                TagLine("SNAP", 16),
                TagLine("FAC 1 CAP 8 0302", 16),
            ),
        )

        assertEquals(7.52, tag?.price!!, 0.0001)
        assertEquals(7.12, tag.rate!!.per, 0.0001)
        assertEquals("pt", tag.rate!!.label)
        assertEquals("1.056 pt", tag.pack)
    }

    @Test
    fun `raised cents on one line still separate the price from the rate`() {
        val tag = readShelfTag(lines("UNIT PRICE $7 12 RETAIL PRICE $7 52 PER PT"))

        assertEquals(7.52, tag?.price!!, 0.0001)
        assertEquals(7.12, tag.rate!!.per, 0.0001)
    }

    @Test
    fun `raised cents on the next line are joined to the price above`() {
        // What the same label looks like when recognition puts the small raised cents on a line of
        // their own instead of keeping them beside the dollar figure.
        val tag = readShelfTag(
            listOf(
                TagLine("BERTOLI OO SUSTAIN", 24),
                TagLine("RETAIL PRICE", 22),
                TagLine("$7", 50),
                TagLine("52", 34),
                TagLine("UNIT PRICE", 20),
                TagLine("$7", 30),
                TagLine("12", 20),
                TagLine("PER", 18),
                TagLine("PT", 18),
            ),
        )

        assertEquals(7.52, tag?.price!!, 0.0001)
        assertEquals(7.12, tag.rate!!.per, 0.0001)
        assertEquals("pt", tag.rate!!.label)
    }

    @Test
    fun `a bare number is not joined onto the number above it`() {
        val tag = readShelfTag(lines("BERTOLI OO SUSTAIN", "FAC 1 CAP 8", "0302", "35"))

        assertNull(tag)
    }

    @Test
    fun `spaced cents are read without a mark when the line says it is the price`() {
        // The camera often loses the dollar sign on a dim shelf; the words still say what it is.
        val tag = readShelfTag(
            lines("OLIVE OIL", "RETAIL PRICE 7 52", "UNIT PRICE 7 12", "PER PT"),
        )

        assertEquals(7.52, tag?.price!!, 0.0001)
        assertEquals(7.12, tag.rate!!.per, 0.0001)
    }

    @Test
    fun `a bare pair of numbers is not a price unless the line names one`() {
        assertNull(readShelfTag(lines("PAPER TOWELS", "6 99", "SNAP")))
    }

    @Test
    fun `a printed pack size wins over the one the rate implies`() {
        val tag = readShelfTag(
            lines("OLIVE OIL 500ML", "RETAIL PRICE", "$7.52", "UNIT PRICE", "$7.12", "PER PT"),
        )

        assertEquals("500 ml", tag?.pack)
    }

    @Test
    fun `a rate counted per item states how many`() {
        // $3.00 at 50 cents each is six of them, which is the same size said the other way round.
        val tag = readShelfTag(lines("RETAIL PRICE", "$3.00", "UNIT PRICE", "$0.50", "PER CT"))

        assertEquals(3.0, tag?.price!!, 0.0001)
        assertEquals("6 each", tag.pack)
        assertEquals(0.50, unitPriceOf(tag.price, tag.pack)!!.per, 0.01)
    }

    @Test
    fun `a metric pack size is kept as the app spells it`() {
        val tag = readShelfTag(lines("OLIVE OIL 500ML", "RETAIL PRICE", "$7.52"))

        assertEquals("500 ml", tag?.pack)
        assertEquals(7.52, tag?.price!!, 0.0001)
    }
}
