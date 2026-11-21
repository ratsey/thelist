package com.ratsey.thelist.ui

import com.ratsey.thelist.data.Item
import com.ratsey.thelist.ui.components.placeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceLabelTest {

    private val walmart = "store-walmart"
    private val tesco = "store-tesco"
    private val names = mapOf(walmart to "Walmart", tesco to "Tesco")

    private fun coffee(storeId: String? = null, location: String? = null) =
        Item(name = "Coffee", storeId = storeId, location = location)

    @Test
    fun `with no shop chosen the row names the shop but not the aisle`() {
        // "All shops" is a view of every shop at once, so one shop's aisle number would be a
        // fragment of a walk you are not taking.
        assertEquals("Walmart", placeLabel(coffee(walmart, "A6"), names))
        assertNull(placeLabel(coffee(null, "A6"), names))
    }

    @Test
    fun `standing in that shop the row says only the aisle`() {
        assertEquals("A6", placeLabel(coffee(walmart, "A6"), names, currentStoreId = walmart))
    }

    @Test
    fun `standing in another shop only its name is worth saying`() {
        assertEquals("Tesco", placeLabel(coffee(tesco, "B3"), names, currentStoreId = walmart))
        assertEquals("Tesco", placeLabel(coffee(tesco, null), names, currentStoreId = walmart))
    }

    @Test
    fun `an item with no shop keeps its aisle wherever you are`() {
        assertEquals("A6", placeLabel(coffee(null, "A6"), names, currentStoreId = walmart))
    }

    @Test
    fun `nothing known means nothing to say`() {
        assertNull(placeLabel(coffee(), names, currentStoreId = walmart))
        assertNull(placeLabel(coffee(walmart), names, currentStoreId = walmart))
    }
}
