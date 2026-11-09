package app.trolley.lists.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Item::class, Receipt::class, PurchaseLine::class, SyncOp::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun purchaseLineDao(): PurchaseLineDao
    abstract fun syncDao(): SyncDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "trolley.db")
                // Pre-1.0: the schema moved to tombstones and receipts, so local data is reset
                // rather than migrated. Removing this in favour of real migrations is the first
                // item on the roadmap before a wider release.
                .fallbackToDestructiveMigration()
                .build()
    }
}
