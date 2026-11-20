package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemDetailsTest {

    private val item = Item(id = "row-1", name = "Milk", variant = "Skimmed")

    @Test
    fun `the details sheet can change the product name`() {
        val updated = ItemDetails.withDetails(
            item = item,
            name = "Oat milk",
            storeId = null,
            location = null,
            variant = "Skimmed",
            amount = null,
            price = null,
            priceUnit = null,
            now = 42L,
        )
        assertEquals("Oat milk", updated.name)
        assertEquals("Skimmed", updated.variant)
    }

    @Test
    fun `a blank name keeps the existing product name`() {
        val updated = ItemDetails.withDetails(
            item = item,
            name = "   ",
            storeId = null,
            location = null,
            variant = null,
            amount = null,
            price = null,
            priceUnit = null,
            now = 42L,
        )
        assertEquals("Milk", updated.name)
    }

    @Test
    fun `unchanged blank details stay null`() {
        val updated = ItemDetails.withDetails(
            item = item,
            name = "Milk",
            storeId = null,
            location = "  ",
            variant = " ",
            amount = "",
            price = 3.5,
            priceUnit = " ",
            now = 42L,
        )
        assertNull(updated.location)
        assertNull(updated.variant)
        assertNull(updated.amount)
        assertNull(updated.priceUnit)
        assertEquals(3.5, updated.price ?: 0.0, 0.0)
        assertEquals(42L, updated.updatedAt)
    }
}
