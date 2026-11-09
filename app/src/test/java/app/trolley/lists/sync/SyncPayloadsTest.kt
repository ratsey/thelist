package app.trolley.lists.sync

import app.trolley.lists.data.Item
import app.trolley.lists.data.PurchaseLine
import app.trolley.lists.data.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadsTest {

    @Test
    fun `item survives a round trip`() {
        val item = Item(
            id = "item-1",
            name = "Milk",
            createdAt = 1_000L,
            completedAt = 2_000L,
            updatedAt = 3_000L,
            deletedAt = null,
        )

        val decoded = SyncPayloads.itemFromJson(SyncPayloads.itemJson(item))

        assertEquals(item, decoded)
    }

    @Test
    fun `tombstones survive a round trip`() {
        val item = Item(id = "item-2", name = "Eggs", updatedAt = 9_000L, deletedAt = 9_500L)
        val decoded = SyncPayloads.itemFromJson(SyncPayloads.itemJson(item))

        assertEquals(9_500L, decoded.deletedAt)
    }

    @Test
    fun `receipt keeps nullable total and photo`() {
        val receipt = Receipt(
            id = "receipt-1",
            storeName = "Tesco",
            purchasedAt = 1_700_000_000_000L,
            total = null,
            currency = "£",
            photoPath = null,
            updatedAt = 42L,
        )

        val decoded = SyncPayloads.receiptFromJson(SyncPayloads.receiptJson(receipt))

        assertEquals(receipt, decoded)
        assertNull(decoded.total)
        assertNull(decoded.photoPath)
    }

    @Test
    fun `purchase line keeps precision`() {
        val line = PurchaseLine(
            id = "line-1",
            receiptId = "receipt-1",
            productKey = "milk",
            displayName = "Semi Skimmed Milk",
            quantity = 2.0,
            unitPrice = 1.15,
            lineTotal = 2.30,
            matchedItemId = "item-1",
            updatedAt = 7L,
        )

        val decoded = SyncPayloads.lineFromJson(SyncPayloads.lineJson(line))

        assertEquals(line, decoded)
    }

    @Test
    fun `last write wins uses updatedAt with incoming winning ties`() {
        assertTrue(SyncPayloads.shouldApply(remoteUpdatedAt = 10L, localUpdatedAt = null))
        assertTrue(SyncPayloads.shouldApply(remoteUpdatedAt = 10L, localUpdatedAt = 9L))
        assertTrue(SyncPayloads.shouldApply(remoteUpdatedAt = 10L, localUpdatedAt = 10L))
        assertFalse(SyncPayloads.shouldApply(remoteUpdatedAt = 9L, localUpdatedAt = 10L))
    }
}
