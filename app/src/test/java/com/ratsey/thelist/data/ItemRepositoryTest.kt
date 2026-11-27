package com.ratsey.thelist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ratsey.thelist.receipt.ItemMatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The repository against a real database: an in-memory Room instance on the JVM, so the SQL these
 * paths write is exercised rather than merely compiled. There is no emulator in CI, and the row
 * state (on the list, off the list, which version) is exactly what these writes decide.
 */
@RunWith(RobolectricTestRunner::class)
class ItemRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ItemRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ItemRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun rows(): List<Item> = db.itemDao().all(Item.DEFAULT_LIST_ID)

    @Test
    fun `a version added to the shelf stays on the shelf`() = runBlocking {
        repository.add("Milk / Full Fat", onList = false)

        val outcome = repository.add("Milk / Skimmed", onList = false)

        assertEquals(ItemRepository.AddOutcome.Added, outcome)
        assertEquals(2, rows().size)
        assertTrue(rows().none { it.onList })
        assertTrue(rows().any { it.name == "Milk" && it.variant == "Skimmed" })
    }

    @Test
    fun `the shop and aisle carry to the new shelf version`() = runBlocking {
        repository.add("Milk / Full Fat", storeId = "shop", location = "A6", onList = false)

        repository.add("Milk / Skimmed", storeId = "shop", location = "A6", onList = false)

        assertTrue(rows().all { it.storeId == "shop" && it.location == "A6" })
    }

    @Test
    fun `asking again for a version already kept changes nothing`() = runBlocking {
        repository.add("Milk / Skimmed", onList = false)
        val before = rows().single().updatedAt

        // The same version, typed with different padding and case.
        val outcome = repository.add("  milk  /  skimmed ", onList = false)

        assertEquals(ItemRepository.AddOutcome.AlreadyOnShelf, outcome)
        assertEquals(1, rows().size)
        assertEquals(before, rows().single().updatedAt)
    }

    @Test
    fun `a version already on the list is not shelved`() = runBlocking {
        repository.add("Milk / Skimmed")

        val outcome = repository.add("Milk / Skimmed", onList = false)

        assertEquals(ItemRepository.AddOutcome.AlreadyListed, outcome)
        assertTrue(rows().single().onList)
    }

    @Test
    fun `a version added to something being bought is bought too`() = runBlocking {
        repository.add("Milk / Full Fat")

        repository.add("Milk / Skimmed", onList = true)

        assertEquals(2, rows().count { it.onList && it.completedAt == null })
    }

    @Test
    fun `moving a row makes it a version of the product`() = runBlocking {
        repository.add("Tea / Black", onList = false)
        repository.add("Green Tea", onList = false)
        val green = rows().first { it.name == "Green Tea" }

        val outcome = repository.makeVariant(green, "Tea", "Green")

        assertEquals(ItemRepository.VariantOutcome.Moved, outcome)
        val moved = db.itemDao().byId(green.id)!!
        assertEquals("Tea", moved.name)
        assertEquals("Green", moved.variant)
        // Where it lives is not what it is: moving it does not put it back on the list.
        assertFalse(moved.onList)
    }

    @Test
    fun `the prices a row earned move with it`() = runBlocking {
        repository.add("Green Tea", onList = false)
        val green = rows().single()
        db.receiptDao().upsert(Receipt(storeName = "Walmart", purchasedAt = 1_000L))
        val receipt = db.receiptDao().all(Item.DEFAULT_LIST_ID).single()
        db.purchaseLineDao().upsert(
            PurchaseLine(
                receiptId = receipt.id,
                productKey = ItemMatcher.productKey("Green Tea"),
                displayName = "Green Tea",
                unitPrice = 3.0,
                lineTotal = 3.0,
                matchedItemId = green.id,
            ),
        )

        repository.makeVariant(green, "Tea", "Green")

        val line = db.purchaseLineDao().all(Item.DEFAULT_LIST_ID).single()
        assertEquals(ItemMatcher.productKey("Tea"), line.productKey)
        assertEquals("Tea", line.displayName)
        assertEquals("Green", line.variant)
        assertEquals(green.id, line.matchedItemId)
    }

    @Test
    fun `the chain is shelf to buy to basket and back`() = runBlocking {
        repository.add("Milk")
        val item = rows().single()

        // On the list, then in the basket: one row, one step forward each time.
        repository.complete(item)
        assertTrue(rows().single().let { it.onList && it.completedAt != null })

        // Out of the basket is one step back — onto the list, not onto the shelf.
        repository.restore(rows().single())
        val back = rows().single()
        assertTrue(back.onList)
        assertEquals(null, back.completedAt)

        // And off the list is the shelf.
        repository.putAway(back)
        assertFalse(rows().single().onList)
    }

    @Test
    fun `a version the product already has is left alone`() = runBlocking {
        repository.add("Tea / Green", onList = false)
        repository.add("Green Tea", onList = false)
        val standalone = rows().first { it.name == "Green Tea" }

        val outcome = repository.makeVariant(standalone, " tea ", " green ")

        assertEquals(ItemRepository.VariantOutcome.AlreadyTaken, outcome)
        assertEquals("Green Tea", db.itemDao().byId(standalone.id)!!.name)
        assertEquals(2, rows().size)
    }
}
