package com.ratsey.thelist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Item::class, Receipt::class, PurchaseLine::class, SyncOp::class],
    version = 3,
    // Schema history lives in app/schemas and is committed: without it, a migration could only
    // be written by reconstructing the previous version by hand.
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun purchaseLineDao(): PurchaseLineDao
    abstract fun syncDao(): SyncDao

    companion object {
        /** v3 added the shelf flag; everything before that is still a reset, since it never shipped. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN onList INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "thelist.db")
                .addMigrations(MIGRATION_2_3)
                // No install predates v2, so older schemas are reset rather than migrated.
                .fallbackToDestructiveMigration()
                .build()
    }
}
