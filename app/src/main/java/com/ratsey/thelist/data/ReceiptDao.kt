package com.ratsey.thelist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM receipts WHERE listId = :listId AND deletedAt IS NULL ORDER BY purchasedAt DESC")
    fun observeReceipts(listId: String): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun byId(id: String): Receipt?

    @Query("SELECT * FROM receipts WHERE listId = :listId")
    suspend fun all(listId: String): List<Receipt>

    @Query(
        "SELECT pl.productKey AS productKey, pl.displayName AS displayName, pl.quantity AS quantity, " +
            "pl.unitPrice AS unitPrice, pl.lineTotal AS lineTotal, r.storeName AS storeName, " +
            "r.purchasedAt AS purchasedAt, r.currency AS currency, r.id AS receiptId " +
            "FROM purchase_lines pl JOIN receipts r ON r.id = pl.receiptId " +
            "WHERE pl.listId = :listId AND pl.deletedAt IS NULL AND r.deletedAt IS NULL " +
            "ORDER BY r.purchasedAt DESC, pl.displayName ASC",
    )
    fun observePriceRows(listId: String): Flow<List<PriceRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(receipt: Receipt)

    @Query("DELETE FROM receipts")
    suspend fun deleteAll()
}

@Dao
interface PurchaseLineDao {

    @Query(
        "SELECT * FROM purchase_lines WHERE listId = :listId AND receiptId = :receiptId AND deletedAt IS NULL " +
            "ORDER BY rowid ASC",
    )
    fun observeForReceipt(listId: String, receiptId: String): Flow<List<PurchaseLine>>

    @Query("SELECT * FROM purchase_lines WHERE receiptId = :receiptId ORDER BY rowid ASC")
    suspend fun forReceipt(receiptId: String): List<PurchaseLine>

    @Query("SELECT * FROM purchase_lines WHERE id = :id")
    suspend fun byId(id: String): PurchaseLine?

    @Query("SELECT * FROM purchase_lines WHERE listId = :listId")
    suspend fun all(listId: String): List<PurchaseLine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(line: PurchaseLine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lines: List<PurchaseLine>)

    @Query("DELETE FROM purchase_lines")
    suspend fun deleteAll()
}
