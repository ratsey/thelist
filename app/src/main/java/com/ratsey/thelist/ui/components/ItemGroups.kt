package com.ratsey.thelist.ui.components

import com.ratsey.thelist.data.Item

/** Rows that share a location, in the order the list should read them. */
data class LocationGroup(val label: String?, val items: List<Item>)

/**
 * Groups the list by the aisle each row lives in, because that is the order you walk a shop in.
 * Locations sort case-insensitively (A2 before A10 would need natural sort; signs are usually
 * letters and one digit, and shuffling by hand beats a wrong guess at the scheme). Rows with no
 * location come last, since they are the ones you have to hunt for anyway.
 *
 * A list where nothing has a location comes back as one unnamed group, so the caller can leave it
 * flat instead of drawing a header over the whole screen.
 */
fun groupByLocation(items: List<Item>): List<LocationGroup> {
    val byLocation = items.groupBy { item -> item.location?.trim()?.takeIf { it.isNotEmpty() } }
    val named = byLocation.keys.filterNotNull().sortedWith(String.CASE_INSENSITIVE_ORDER)
    val groups = named.map { label -> LocationGroup(label, byLocation.getValue(label)) }
    val unplaced = byLocation[null].orEmpty()
    return if (unplaced.isEmpty()) groups else groups + LocationGroup(null, unplaced)
}

/**
 * Whether the list draws its aisle bands.
 *
 * Only inside a chosen shop: under "All shops" the list holds several shops' rows at once, so one
 * shop's A6 says nothing about where you are and a band would gather other shops' rows under it.
 * And only when a row actually knows where it lives — otherwise the band would be a header over
 * the whole screen saying nothing.
 */
fun showsAisleBands(currentStoreId: String?, groups: List<LocationGroup>): Boolean =
    currentStoreId != null && (groups.size > 1 || groups.firstOrNull()?.label != null)
