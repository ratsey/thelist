package com.ratsey.thelist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Item::class, Store::class, Receipt::class, PurchaseLine::class, SyncOp::class],
    version = 4,
    // Schema history lives in app/schemas and is committed: without it, a migration could only
    // be written by reconstructing the previous version by hand.
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun storeDao(): StoreDao
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

        /**
         * v4 added shops and the two placement columns. Both columns are nullable with no default,
         * which is exactly what an `ALTER TABLE ADD COLUMN` produces, so existing rows come across
         * as "nothing said yet" rather than as a shop with an empty name.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stores` (`id` TEXT NOT NULL, `listId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL("ALTER TABLE `items` ADD COLUMN `storeId` TEXT")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `location` TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "thelist.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                // No install predates v2, so older schemas are reset rather than migrated.
                .fallbackToDestructiveMigration()
                .build()
    }
}
