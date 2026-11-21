package com.ratsey.thelist.ui.components

import com.ratsey.thelist.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class LocationGroupsTest {

    private fun item(name: String, location: String? = null) =
        Item(id = name, name = name, location = location)

    @Test
    fun `aisles come first in order and the unplaced rows last`() {
        val groups = groupByLocation(
            listOf(
                item("Milk"),
                item("Coffee", "B3"),
                item("Bread", "A6"),
                item("Tea", "a2"),
            ),
        )

        assertEquals(listOf("a2", "A6", "B3", null), groups.map { it.label })
        assertEquals(listOf("Milk"), groups.last().items.map { it.name })
    }

    @Test
    fun `a list with no locations stays one unnamed group`() {
        val groups = groupByLocation(listOf(item("Milk"), item("Eggs")))

        assertEquals(1, groups.size)
        assertNull(groups.single().label)
        assertEquals(listOf("Milk", "Eggs"), groups.single().items.map { it.name })
    }

    @Test
    fun `the same aisle typed with a stray space is one group`() {
        val groups = groupByLocation(listOf(item("Coffee", "A6 "), item("Bread", "A6")))

        assertEquals(1, groups.size)
        assertEquals("A6", groups.single().label)
        assertEquals(listOf("Coffee", "Bread"), groups.single().items.map { it.name })
    }

    @Test
    fun `a blank aisle counts as no aisle`() {
        val groups = groupByLocation(listOf(item("Coffee", "A6"), item("Milk", "  ")))

        assertEquals(listOf("A6", null), groups.map { it.label })
    }

    @Test
    fun `no aisle bands unless a shop is chosen`() {
        val items = listOf(item("Bread", "A6"), item("Coffee", "B3"), item("Milk"))
        val groups = groupByLocation(items)

        // Across all shops the rows are not one walk, so no bands.
        assertFalse(showsAisleBands(null, groups))
        assertTrue(showsAisleBands("store-walmart", groups))
    }

    @Test
    fun `nothing to band when no row knows its aisle`() {
        val groups = groupByLocation(listOf(item("Milk"), item("Eggs")))

        assertFalse(showsAisleBands("store-walmart", groups))
    }
}
