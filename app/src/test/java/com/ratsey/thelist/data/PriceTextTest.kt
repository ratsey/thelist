package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceTextTest {

    @Test
    fun `whole prices lose the decimal part`() {
        assertEquals("3", formatPriceField(3.0))
    }

    @Test
    fun `part prices keep what was typed`() {
        assertEquals("3.5", formatPriceField(3.5))
        assertEquals("12.99", formatPriceField(12.99))
    }

    @Test
    fun `no price is an empty field`() {
        assertEquals("", formatPriceField(null))
    }

    @Test
    fun `a typed price reads back`() {
        assertEquals(3.5, parsePriceField(" 3.5 ")!!, 0.001)
        assertEquals(12.99, parsePriceField("12.99")!!, 0.001)
    }

    @Test
    fun `a comma is a decimal point`() {
        assertEquals(3.5, parsePriceField("3,5")!!, 0.001)
    }

    @Test
    fun `blank and nonsense are not prices`() {
        assertNull(parsePriceField(""))
        assertNull(parsePriceField("   "))
        assertNull(parsePriceField("three"))
        assertNull(parsePriceField("3.5.1"))
    }

    @Test
    fun `a negative price is not a price`() {
        assertNull(parsePriceField("-2"))
    }
}
