package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfGroupsTest {

    private fun item(name: String, variant: String? = null) =
        Item(id = "$name/$variant", name = name, variant = variant)

    @Test
    fun `two versions of one product are one line`() {
        val shelf = listOf(item("Milk", "Skimmed"), item("Milk", "Full Fat"), item("Bread", "Wholemeal"))

        val groups = shelfGroups(shelf)

        assertEquals(listOf("Milk", "Bread"), groups.map { it.label })
        assertEquals(listOf("Full Fat", "Skimmed"), groups.first().variants)
        assertNull(groups.first().variant)
        assertEquals(2, groups.first().items.size)
    }

    @Test
    fun `a product with one version keeps its row and its type`() {
        val groups = shelfGroups(listOf(item("Bread", "Wholemeal")))

        assertEquals(1, groups.size)
        assertEquals("Bread", groups.first().label)
        assertEquals("Wholemeal", groups.first().variant)
        assertTrue(groups.first().variants.isEmpty())
    }

    @Test
    fun `rows with no type to choose between stay separate`() {
        val groups = shelfGroups(listOf(item("Milk"), item("Milk")))

        assertEquals(2, groups.size)
        assertEquals(listOf(null, null), groups.map { it.variant })
    }

    @Test
    fun `a type still in the name is gathered with the rest`() {
        val shelf = listOf(item("Milk / Skimmed"), item("Milk", "Full Fat"))

        val groups = shelfGroups(shelf)

        assertEquals(1, groups.size)
        assertEquals("Milk", groups.first().label)
        assertEquals(listOf("Full Fat", "Skimmed"), groups.first().variants)
    }

    @Test
    fun `the gathered line keeps the product's place on the shelf`() {
        val shelf = listOf(
            item("Apples"),
            item("Milk", "Skimmed"),
            item("Milk", "Full Fat"),
            item("Zucchini"),
        )

        assertEquals(listOf("Apples", "Milk", "Zucchini"), shelfGroups(shelf).map { it.label })
    }

    @Test
    fun `a gathered line is keyed by its product, not by one of its rows`() {
        val shelf = listOf(item("Milk", "Skimmed"), item("Milk", "Full Fat"))

        assertEquals("product:" + ItemNames.key("Milk"), shelfGroups(shelf).single().key)
    }
}
