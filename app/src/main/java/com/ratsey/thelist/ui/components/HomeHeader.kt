package com.ratsey.thelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.PriceSummary
import com.ratsey.thelist.data.Store
import com.ratsey.thelist.receipt.ItemMatcher
import com.ratsey.thelist.ui.SyncState
import com.ratsey.thelist.ui.SyncUi
import com.ratsey.thelist.ui.UpdateUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.ratsey.thelist.data.historyKey

@Composable
fun HomeHeader(
    tab: Int,
    activeCount: Int,
    basketCount: Int,
    shelfCount: Int,
    updateState: UpdateUi,
    syncUi: SyncUi,
    stores: List<Store>,
    currentStoreId: String?,
    onSelectStore: (String?) -> Unit,
    onTabSelected: (Int) -> Unit,
    onCheckForUpdates: () -> Unit,
    onFinishShop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStores: () -> Unit,
    onAbout: () -> Unit,
    onStartUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismissUpdate: () -> Unit,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.list_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitleFor(tab, activeCount, basketCount, shelfCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ShopPill(
                        stores = stores,
                        currentStoreId = currentStoreId,
                        onSelect = onSelectStore,
                        onAddStore = onOpenStores,
                    )
                }
            }

            IconButton(onClick = onOpenSync) {
                Icon(
                    imageVector = when (syncUi.state) {
                        SyncState.Syncing -> Icons.Rounded.CloudSync
                        SyncState.Offline -> Icons.Rounded.CloudOff
                        SyncState.Idle -> Icons.Rounded.CloudDone
                        SyncState.Disabled -> Icons.Rounded.CloudOff
                    },
                    contentDescription = stringResource(R.string.menu_sync),
                    tint = when (syncUi.state) {
                        SyncState.Idle -> colors.primary
                        SyncState.Offline -> colors.error
                        else -> colors.onSurfaceVariant
                    },
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_check_updates)) },
                        onClick = {
                            menuOpen = false
                            onCheckForUpdates()
                        },
                    )
                    if (tab == 2) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_finish_shop)) },
                            enabled = basketCount > 0,
                            onClick = {
                                menuOpen = false
                                onFinishShop()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_settings)) },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_stores)) },
                        onClick = {
                            menuOpen = false
                            onOpenStores()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_about)) },
                        onClick = {
                            menuOpen = false
                            onAbout()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        UpdateBanner(
            state = updateState,
            onDownload = onStartUpdate,
            onInstall = onInstallUpdate,
            onAllowInstall = onOpenInstallPermission,
            onDismiss = onDismissUpdate,
        )

        ScreenTabs(
            selected = tab,
            activeCount = activeCount,
            basketCount = basketCount,
            shelfCount = shelfCount,
            onSelect = onTabSelected,
        )

        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The shop you are standing in, sitting on the subtitle line so it costs the list no height. With
 * one chosen, its rows show only the aisle; with none, every row shows shop and aisle both.
 */
@Composable
private fun ShopPill(
    stores: List<Store>,
    currentStoreId: String?,
    onSelect: (String?) -> Unit,
    onAddStore: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val current = stores.firstOrNull { it.id == currentStoreId }
    val tint = if (current == null) colors.onSurfaceVariant else colors.onPrimaryContainer

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (current == null) {
                        colors.surfaceVariant.copy(alpha = 0.45f)
                    } else {
                        colors.primaryContainer
                    },
                )
                .clickable(
                    onClickLabel = stringResource(R.string.shop_pill_label),
                ) { open = true }
                .padding(start = 10.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Storefront,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = current?.name ?: stringResource(R.string.shop_pill_all),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shop_pill_all)) },
                onClick = {
                    open = false
                    onSelect(null)
                },
            )
            stores.forEach { store ->
                DropdownMenuItem(
                    text = { Text(store.name) },
                    onClick = {
                        open = false
                        onSelect(store.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.placement_new_store)) },
                onClick = {
                    open = false
                    onAddStore()
                },
            )
        }
    }
}

@Composable
private fun subtitleFor(
    tab: Int,
    activeCount: Int,
    basketCount: Int,
    shelfCount: Int,
): String = when {
    tab == 0 && shelfCount == 0 -> stringResource(R.string.shelf_subtitle)
    tab == 0 -> pluralStringResource(R.plurals.shelf_count, shelfCount, shelfCount)
    tab == 1 && activeCount == 0 -> stringResource(R.string.empty_subtitle)
    tab == 1 -> pluralStringResource(R.plurals.active_count, activeCount, activeCount)
    tab == 2 && basketCount == 0 -> stringResource(R.string.empty_subtitle)
    else -> pluralStringResource(R.plurals.basket_count, basketCount, basketCount)
}

@Composable
fun ShoppingList(
    items: List<Item>,
    storeNames: Map<String, String>,
    summaries: Map<String, PriceSummary>,
    currentStoreId: String? = null,
    showHint: Boolean,
    onToggle: (Item) -> Unit,
    onPutAway: (Item) -> Unit,
    onOpenPrices: (Item) -> Unit,
    onOpenActions: (Item) -> Unit,
    onDismissHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.ShoppingCart,
            title = stringResource(R.string.empty_active_title),
            body = stringResource(R.string.empty_active_body),
            modifier = modifier,
        )
        return
    }

    // The list follows the walk: aisles in order, then everything with no aisle. Headers only
    // appear when at least one row actually knows where it lives.
    val locationGroups = remember(items) { groupByLocation(items) }
    val grouped = locationGroups.size > 1 || locationGroups.firstOrNull()?.label != null

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showHint) {
            item(key = "swipe-hint") {
                SwipeHint(onDismiss = onDismissHint)
            }
        }
        if (grouped) {
            locationGroups.forEach { group ->
                item(key = "aisle-${group.label.orEmpty()}") {
                    LocationHeader(label = group.label, count = group.items.size)
                }
                items(group.items, key = { it.id }) { item ->
                    ShoppingRow(
                        item = item,
                        storeNames = storeNames,
                        summaries = summaries,
                        currentStoreId = currentStoreId,
                        onToggle = onToggle,
                        onPutAway = onPutAway,
                        onOpenPrices = onOpenPrices,
                        onOpenActions = onOpenActions,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { item ->
                ShoppingRow(
                    item = item,
                    storeNames = storeNames,
                    summaries = summaries,
                    currentStoreId = currentStoreId,
                    onToggle = onToggle,
                    onPutAway = onPutAway,
                    onOpenPrices = onOpenPrices,
                    onOpenActions = onOpenActions,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ShoppingRow(
    item: Item,
    storeNames: Map<String, String>,
    summaries: Map<String, PriceSummary>,
    currentStoreId: String?,
    onToggle: (Item) -> Unit,
    onPutAway: (Item) -> Unit,
    onOpenPrices: (Item) -> Unit,
    onOpenActions: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    ItemRow(
        item = item,
        meta = itemMeta(
            item = item,
            storeNames = storeNames,
            summary = summaries[historyKey(ItemMatcher.productKey(item.name), item.variant)],
            currentStoreId = currentStoreId,
        ),
        swipeRight = SwipeAction(
            label = R.string.swipe_got_it,
            icon = Icons.Rounded.Check,
            onClick = { onToggle(item) },
        ),
        swipeLeft = SwipeAction(
            label = R.string.swipe_put_away,
            icon = Icons.Rounded.Archive,
            onClick = { onPutAway(item) },
            muted = true,
        ),
        onOpenPrices = { onOpenPrices(item) },
        onOpenActions = { onOpenActions(item) },
        modifier = modifier,
    )
}

/** An aisle band: small enough that a whole shop of aisles costs a row or two of height. */
@Composable
private fun LocationHeader(label: String?, count: Int) {
    val colors = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, bottom = 1.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Place,
            contentDescription = null,
            tint = colors.outline,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label ?: stringResource(R.string.group_no_location),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.outline,
        )
    }
}

@Composable
fun BasketList(
    items: List<Item>,
    storeNames: Map<String, String>,
    summaries: Map<String, PriceSummary>,
    currentStoreId: String? = null,
    onToggle: (Item) -> Unit,
    onPutAway: (Item) -> Unit,
    onOpenDetails: (Item) -> Unit,
    onOpenActions: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.CheckCircle,
            title = stringResource(R.string.empty_basket_title),
            body = stringResource(R.string.empty_basket_body),
            modifier = modifier,
        )
        return
    }

    val zone = remember { ZoneId.systemDefault() }
    val groups = remember(items) {
        items.groupBy { item ->
            item.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?: LocalDate.now()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { (date, groupItems) ->
            item(key = "header-$date") {
                SectionLabel(text = dayLabel(date))
            }
            items(groupItems, key = { it.id }) { item ->
                ItemRow(
                    item = item,
                    meta = itemMeta(
                        item = item,
                        storeNames = storeNames,
                        summary = summaries[historyKey(ItemMatcher.productKey(item.name), item.variant)],
                        currentStoreId = currentStoreId,
                    ),
                    // In the basket, forward is done with it: onto the shelf, as at checkout.
                    swipeRight = SwipeAction(
                        label = R.string.swipe_put_away,
                        icon = Icons.Rounded.Archive,
                        onClick = { onPutAway(item) },
                        muted = true,
                    ),
                    // Back a step, for the thing you put in but did not buy.
                    swipeLeft = SwipeAction(
                        label = R.string.swipe_add_back,
                        icon = Icons.Rounded.Add,
                        onClick = { onToggle(item) },
                    ),
                    // At the till the thing to change is what it cost, so the long press opens the
                    // sheet that sets a price rather than the price history it has always shown.
                    onOpenPrices = { onOpenDetails(item) },
                    onOpenActions = { onOpenActions(item) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = remember { LocalDate.now() }
    return when (date) {
        today -> stringResource(R.string.section_today)
        today.minusDays(1) -> stringResource(R.string.section_yesterday)
        else -> remember(date) { date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) }
    }
}
