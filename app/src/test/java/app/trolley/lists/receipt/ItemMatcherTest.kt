package app.trolley.lists.receipt

import app.trolley.lists.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemMatcherTest {

    private fun item(name: String) = Item(id = name, name = name)

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
        assertEquals("Milk", ItemMatcher.match("TESCO SEMI SKIMMED MILK 2L", list)?.name)
        assertEquals("Eggs", ItemMatcher.match("FREE RANGE EGGS 6PK", list)?.name)
        assertEquals("Bananas", ItemMatcher.match("BANANAS 1KG", list)?.name)
        assertEquals("Chicken", ItemMatcher.match("CHICKEN BREAST FILLETS 500G", list)?.name)
    }

    @Test
    fun `multiplies quantity lines still match`() {
        assertEquals("Milk", ItemMatcher.match("MILK  2 @ 1.20", list)?.name)
    }

    @Test
    fun `does not match unrelated products`() {
        assertNull(ItemMatcher.match("WASHING UP LIQUID", list))
        assertNull(ItemMatcher.match("BREADED HAM", list))
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
