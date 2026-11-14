package com.ratsey.thelist.data

/** One shop's best price for a product, restated in whatever unit is being compared. */
data class StorePrice(
    val store: String,
    val price: Double,
    /** When that best price was paid, so an old price can be judged as old. */
    val at: Long,
    val purchases: Int,
    val cheapest: Boolean = false,
)

/**
 * The lowest price seen for a product at each shop, cheapest first.
 *
 * Prices that are not per the same thing cannot answer the question — a kilo that looks cheaper
 * than a pound is not cheaper — so anything that will not convert into [basis] is left out rather
 * than compared as a raw number.
 */
fun bestPerStore(points: List<PricePoint>, basis: PriceBasis?): List<StorePrice> =
    points
        .mapNotNull { point ->
            val price = priceIn(point.unitPrice, point.basis, basis) ?: return@mapNotNull null
            point.storeName to (price to point.purchasedAt)
        }
        .groupBy({ it.first }, { it.second })
        .map { (store, seen) ->
            val best = seen.minBy { it.first }
            StorePrice(
                store = store,
                price = best.first,
                at = best.second,
                purchases = seen.size,
            )
        }
        .sortedWith(compareBy({ it.price }, { it.store }))
        .mapIndexed { index, storePrice -> storePrice.copy(cheapest = index == 0) }
