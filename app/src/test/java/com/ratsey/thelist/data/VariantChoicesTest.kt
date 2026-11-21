package com.ratsey.thelist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantChoicesTest {

    private fun item(name: String, variant: String? = null) =
        Item(id = "$name/$variant", name = name, variant = variant)

    @Test
    fun `two versions of one product are both offered`() {
        val shelf = listOf(item("Milk", "Skimmed"), item("Milk", "Full Fat"), item("Bread", "Wholemeal"))

        assertEquals(listOf("Full Fat", "Skimmed"), variantChoices(shelf, shelf.first()))
    }

    @Test
    fun `one version means there is nothing to ask`() {
        val shelf = listOf(item("Milk", "Skimmed"), item("Bread", "Wholemeal"))

        assertTrue(variantChoices(shelf, shelf.first()).isEmpty())
    }

    @Test
    fun `the same version spelled twice is one choice`() {
        val shelf = listOf(item("Milk", "Skimmed"), item("milk", " skimmed "), item("Milk", "Full fat"))

        assertEquals(listOf("Full fat", "Skimmed"), variantChoices(shelf, shelf.first()))
    }

    @Test
    fun `rows with no version offer nothing`() {
        val shelf = listOf(item("Milk"), item("Milk"))

        assertTrue(variantChoices(shelf, shelf.first()).isEmpty())
    }

    @Test
    fun `an older row with the type still in its name counts as a version`() {
        val shelf = listOf(item("Milk / Skimmed"), item("Milk", "Full Fat"))

        assertEquals(listOf("Full Fat", "Skimmed"), variantChoices(shelf, shelf.last()))
    }
}
