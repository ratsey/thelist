package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ItemNamesTest {

    @Test
    fun `trims, collapses spaces and lowercases`() {
        assertEquals("semi skimmed milk", ItemNames.key("  Semi   Skimmed Milk "))
    }

    @Test
    fun `case and padding do not count as a second item`() {
        assertEquals(ItemNames.key("Milk"), ItemNames.key("  MILK  "))
    }

    @Test
    fun `different products stay different`() {
        assertNotEquals(ItemNames.key("Milk"), ItemNames.key("Oat milk"))
    }
}
