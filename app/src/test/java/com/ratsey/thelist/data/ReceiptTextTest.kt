package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptTextTest {

    @Test
    fun `a shouted receipt line becomes a name`() {
        assertEquals(
            "Tesco Finest Colombian Coffee 227g",
            prettyReceiptName("TESCO FINEST COLOMBIAN COFFEE 227G"),
        )
    }

    @Test
    fun `a line already written properly is left alone`() {
        assertEquals("Nescafé Original 200g", prettyReceiptName("Nescafé Original 200g"))
    }

    @Test
    fun `surrounding whitespace goes`() {
        assertEquals("Milk", prettyReceiptName("  MILK \n"))
    }
}
