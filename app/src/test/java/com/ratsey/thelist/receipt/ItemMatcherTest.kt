package com.ratsey.thelist.receipt

import com.ratsey.thelist.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemMatcherTest {

    private fun item(name: String) = Item(id = name, name = name)

    private fun named(id: String, name: String) = Item(id = id, name = name)

    private fun matchOnList(line: String) = ItemMatcher.match(line, emptyList(), list)?.item?.name

    private val list = listOf(
        item("Milk"),
        item("Eggs"),
        item("Bananas"),
        item("Chicken"),
        item("Bread"),
        item("Toilet roll"),
    )

    @Test
    fun `matches noisy receipt names to short list items`() {
        assertEquals("Milk", matchOnList("TESCO SEMI SKIMMED MILK 2L"))
        assertEquals("Eggs", matchOnList("FREE RANGE EGGS 6PK"))
        assertEquals("Bananas", matchOnList("BANANAS 1KG"))
        assertEquals("Chicken", matchOnList("CHICKEN BREAST FILLETS 500G"))
    }

    @Test
    fun `multiplies quantity lines still match`() {
        assertEquals("Milk", matchOnList("MILK  2 @ 1.20"))
    }

    @Test
    fun `does not match unrelated products`() {
        assertNull(ItemMatcher.match("WASHING UP LIQUID", emptyList(), list))
        assertNull(ItemMatcher.match("BREADED HAM", emptyList(), list))
    }

    @Test
    fun `prefers what the shopper already swiped`() {
        val matched = ItemMatcher.match(
            "TESCO BREAD 800G",
            basket = listOf(named("basket-bread", "Bread")),
            list = listOf(named("list-bread", "Bread")),
        )
        assertEquals(ItemMatcher.Source.Basket, matched?.source)
        assertEquals("basket-bread", matched?.item?.id)
    }

    @Test
    fun `basket wins even when the list reads closer`() {
        val matched = ItemMatcher.match(
            "TESCO SEMI SKIMMED MILK 2L",
            basket = listOf(named("basket-milk", "Milk")),
            list = listOf(named("list-milk", "Semi skimmed milk")),
        )
        assertEquals(ItemMatcher.Source.Basket, matched?.source)
        assertEquals("basket-milk", matched?.item?.id)
    }

    @Test
    fun `falls back to the list when nothing has been swiped`() {
        val matched = ItemMatcher.match(
            "TESCO BREAD 800G",
            basket = emptyList(),
            list = listOf(named("list-bread", "Bread")),
        )
        assertEquals(ItemMatcher.Source.List, matched?.source)
        assertEquals("list-bread", matched?.item?.id)
    }

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
    fun `score prefers a fully contained item name`() {
        val itemTokens = setOf("milk")
        val lineTokens = setOf("tesco", "semi", "skimmed", "milk")
        assertEquals(1.0, ItemMatcher.score(itemTokens, lineTokens), 0.001)
    }
}
