package com.ratsey.thelist.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ShoppingCart
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.ui.SyncState
import com.ratsey.thelist.ui.SyncUi
import com.ratsey.thelist.ui.UpdateUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeHeader(
    tab: Int,
    activeCount: Int,
    basketCount: Int,
    shelfCount: Int,
    updateState: UpdateUi,
    syncUi: SyncUi,
    onTabSelected: (Int) -> Unit,
    onCheckForUpdates: () -> Unit,
    onClearBasket: () -> Unit,
    onOpenSettings: () -> Unit,
    onAbout: () -> Unit,
    onStartUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismissUpdate: () -> Unit,
    onScan: () -> Unit,
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
                Text(
                    text = subtitleFor(tab, activeCount, basketCount, shelfCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
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

            IconButton(onClick = onScan) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = stringResource(R.string.menu_scan),
                    tint = colors.onSurfaceVariant,
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
                            text = { Text(stringResource(R.string.menu_clear_basket)) },
                            enabled = basketCount > 0,
                            onClick = {
                                menuOpen = false
                                onClearBasket()
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHint) {
            item(key = "swipe-hint") {
                SwipeHint(onDismiss = onDismissHint)
            }
        }
        items(items, key = { it.id }) { item ->
            ItemRow(
                item = item,
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
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
fun BasketList(
    items: List<Item>,
    onToggle: (Item) -> Unit,
    onPutAway: (Item) -> Unit,
    onOpenPrices: (Item) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (date, groupItems) ->
            item(key = "header-$date") {
                SectionLabel(text = dayLabel(date))
            }
            items(groupItems, key = { it.id }) { item ->
                ItemRow(
                    item = item,
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
                    onOpenPrices = { onOpenPrices(item) },
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
