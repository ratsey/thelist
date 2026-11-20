package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfMatchesTest {

    private fun shelfItem(name: String, variant: String? = null) = Item(
        name = name,
        createdAt = 1L,
        updatedAt = 1L,
        onList = false,
        variant = variant,
    )

    @Test
    fun `a partial word finds the shelf row`() {
        assertEquals(
            listOf("Milk"),
            shelfMatches(listOf(shelfItem("Milk")), "mil").map { it.name },
        )
    }

    @Test
    fun `matching ignores case and edges`() {
        assertEquals(1, shelfMatches(listOf(shelfItem("Milk")), "  MILK ").size)
    }

    @Test
    fun `the same thing with extra words still matches`() {
        assertEquals(1, shelfMatches(listOf(shelfItem("Semi Skimmed Milk 2L")), "skimmed milk").size)
    }

    @Test
    fun `an empty draft suggests nothing`() {
        assertTrue(shelfMatches(listOf(shelfItem("Milk")), "   ").isEmpty())
    }

    @Test
    fun `the strip stays short`() {
        val shelf = (1..6).map { shelfItem("milk $it") }
        assertEquals(3, shelfMatches(shelf, "milk").size)
    }

    @Test
    fun `the type travels with the suggestion`() {
        val match = shelfMatches(listOf(shelfItem("Milk", "semi skimmed")), "milk").single()
        assertEquals("semi skimmed", match.variant)
    }

    @Test
    fun `typing a type narrows the suggestions to that type`() {
        val shelf = listOf(shelfItem("Milk", "Skimmed"), shelfItem("Milk", "Full Fat"))
        assertEquals(
            listOf("Full Fat"),
            shelfMatches(shelf, "milk / full").map { it.variant },
        )
    }

    @Test
    fun `typing the bare name still offers every type`() {
        val shelf = listOf(shelfItem("Milk", "Skimmed"), shelfItem("Milk", "Full Fat"))
        assertEquals(2, shelfMatches(shelf, "milk").size)
    }

    @Test
    fun `an old slash-in-name row is found by its type too`() {
        val shelf = listOf(shelfItem("Milk / Full Fat"))
        assertEquals(1, shelfMatches(shelf, "milk / full").size)
    }
}
