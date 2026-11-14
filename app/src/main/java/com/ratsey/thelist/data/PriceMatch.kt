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

/** Which of two offers wins, once both are money for the same amount of the same thing. */
enum class Cheaper { HERE, OTHER, SAME }

/**
 * The result of putting two offers side by side: [herePer] and [otherPer] are both in
 * [Cheaper]'s terms — money for the [here] offer's unit — so they can be read against each other.
 */
data class OfferDuel(
    val cheaper: Cheaper,
    /** How much cheaper, as a fraction of the dearer offer: 0.13 is 13% less. */
    val difference: Double,
    val herePer: Double,
    val otherPer: Double,
)

/**
 * Two prices, each with what it is for, judged against each other.
 *
 * The second offer is restated into the first one's unit, which is the only way "$12.99 for 30" and
 * "$9.99 for 20" can be answered at all. Null means the question cannot be asked: a pound and an
 * item are not the same thing, and pretending otherwise is worse than saying so.
 */
fun compareOffers(
    herePrice: Double,
    hereBasis: PriceBasis?,
    otherPrice: Double,
    otherBasis: PriceBasis?,
): OfferDuel? {
    if (herePrice <= 0.0 || otherPrice <= 0.0) return null
    val other = priceIn(otherPrice, otherBasis, hereBasis) ?: return null
    if (other <= 0.0) return null

    // Half a percent is not a decision: two offers that far apart are the same price.
    val gap = kotlin.math.abs(herePrice - other)
    val cheaper = when {
        gap < herePrice * 0.005 -> Cheaper.SAME
        herePrice < other -> Cheaper.HERE
        else -> Cheaper.OTHER
    }
    return OfferDuel(
        cheaper = cheaper,
        difference = if (cheaper == Cheaper.SAME) 0.0 else gap / maxOf(herePrice, other),
        herePer = herePrice,
        otherPer = other,
    )
}
