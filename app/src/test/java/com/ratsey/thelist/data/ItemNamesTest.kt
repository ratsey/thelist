package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `a slash splits the name from its type`() {
        val parsed = ItemNames.parse("  Milk / Full Fat  ")
        assertEquals("Milk", parsed.name)
        assertEquals("Full Fat", parsed.variant)
    }

    @Test
    fun `only the first slash splits, so a type may contain one`() {
        val parsed = ItemNames.parse("Oil / Olive / Extra Virgin")
        assertEquals("Oil", parsed.name)
        assertEquals("Olive / Extra Virgin", parsed.variant)
    }

    @Test
    fun `a plain name has no type`() {
        val parsed = ItemNames.parse("Milk")
        assertEquals("Milk", parsed.name)
        assertNull(parsed.variant)
    }

    @Test
    fun `a dangling slash is not a type`() {
        val parsed = ItemNames.parse("Milk /")
        assertEquals("Milk /", parsed.name)
        assertNull(parsed.variant)
    }

    @Test
    fun `two types of one product are different products`() {
        assertNotEquals(
            ItemNames.productKey("Milk", "Skimmed"),
            ItemNames.productKey("Milk", "Full Fat"),
        )
    }

    @Test
    fun `a type-less name is its own product`() {
        assertNotEquals(
            ItemNames.productKey("Milk", null),
            ItemNames.productKey("Milk / Skimmed"),
        )
    }

    @Test
    fun `an old slash-in-name row matches a freshly typed one`() {
        assertEquals(
            ItemNames.productKey("Milk", "Skimmed"),
            ItemNames.productKey("Milk / Skimmed"),
        )
    }

    @Test
    fun `case and padding do not make a second type`() {
        assertEquals(
            ItemNames.productKey("Milk", "Full Fat"),
            ItemNames.productKey("  milk  /  FULL   FAT "),
        )
    }

    @Test
    fun `the duplicate finder ignores other types`() {
        val skimmed = Item(name = "Milk", variant = "Skimmed", updatedAt = 1L)
        val fullFat = Item(name = "Milk", variant = "Full Fat", updatedAt = 2L)
        assertEquals(
            fullFat.id,
            ItemNames.findExisting(listOf(skimmed, fullFat), "Milk / Full Fat")?.id,
        )
    }

    @Test
    fun `the duplicate finder still catches the same thing`() {
        val milk = Item(name = "Milk", variant = null, updatedAt = 1L)
        assertEquals(milk.id, ItemNames.findExisting(listOf(milk), "  milk ")?.id)
    }
}
