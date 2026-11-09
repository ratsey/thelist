package com.ratsey.thelist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Item::class, Receipt::class, PurchaseLine::class, SyncOp::class],
    version = 2,
    // Schema history lives in app/schemas and is committed: without it, a migration to v3
    // could only be written by reconstructing v2 by hand and could not be tested.
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun purchaseLineDao(): PurchaseLineDao
    abstract fun syncDao(): SyncDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "thelist.db")
                // Pre-1.0: the schema moved to tombstones and receipts, so local data is reset
                // rather than migrated. Removing this in favour of real migrations is the first
                // item on the roadmap before a wider release.
                .fallbackToDestructiveMigration()
                .build()
    }
}
