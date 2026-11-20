package com.ratsey.thelist.data

/**
 * The row a shopper saves from the details sheet. Name and type are both editable there, while a
 * blank name means "leave the product as it was" rather than an unnamed row.
 */
object ItemDetails {

    fun nameOrExisting(item: Item, entered: String?): String =
        entered?.trim()?.takeIf { it.isNotEmpty() } ?: item.name

    fun withDetails(
        item: Item,
        name: String?,
        storeId: String?,
        location: String?,
        variant: String?,
        amount: String?,
        price: Double?,
        priceUnit: String?,
        now: Long,
    ): Item = item.copy(
        name = nameOrExisting(item, name),
        storeId = storeId,
        location = location?.trim()?.takeIf { it.isNotEmpty() },
        variant = variant?.trim()?.takeIf { it.isNotEmpty() },
        amount = amount?.trim()?.takeIf { it.isNotEmpty() },
        price = price,
        priceUnit = priceUnit?.trim()?.takeIf { it.isNotEmpty() },
        updatedAt = now,
    )
}
