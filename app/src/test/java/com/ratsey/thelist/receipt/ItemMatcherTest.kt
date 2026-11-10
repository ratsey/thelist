package com.ratsey.thelist.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ItemMatcherTest {

    @Test
    fun `product key ignores case and punctuation but keeps size`() {
        assertEquals(
            ItemMatcher.productKey("Semi Skimmed Milk 2L"),
            ItemMatcher.productKey("semi skimmed milk 2l"),
        )
        assertNotEquals(
            ItemMatcher.productKey("Semi Skimmed Milk 2L"),
            ItemMatcher.productKey("Semi Skimmed Milk 1L"),
        )
    }

    @Test
    fun `word order does not make a new product`() {
        assertEquals(
            ItemMatcher.productKey("Tinned Tomatoes"),
            ItemMatcher.productKey("tomatoes, tinned"),
        )
    }

    @Test
    fun `an item with no usable words still gets a key`() {
        assertEquals("milk", ItemMatcher.productKey("Milk"))
        assertEquals("", ItemMatcher.productKey("   ").trim())
    }
}
