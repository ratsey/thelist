package app.trolley.lists.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
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
import app.trolley.lists.R
import app.trolley.lists.data.Item
import app.trolley.lists.ui.UpdateUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeHeader(
    tab: Int,
    activeCount: Int,
    historyCount: Int,
    updateState: UpdateUi,
    onTabSelected: (Int) -> Unit,
    onCheckForUpdates: () -> Unit,
    onClearHistory: () -> Unit,
    onAbout: () -> Unit,
    onStartUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismissUpdate: () -> Unit,
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
                    text = subtitleFor(tab, activeCount, historyCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
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
                    if (tab == 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_clear_history)) },
                            enabled = historyCount > 0,
                            onClick = {
                                menuOpen = false
                                onClearHistory()
                            },
                        )
                    }
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
            historyCount = historyCount,
            onSelect = onTabSelected,
        )

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun subtitleFor(tab: Int, activeCount: Int, historyCount: Int): String = when {
    tab == 0 && activeCount == 0 -> stringResource(R.string.empty_subtitle)
    tab == 0 -> pluralStringResource(R.plurals.active_count, activeCount, activeCount)
    historyCount == 0 -> stringResource(R.string.empty_subtitle)
    else -> pluralStringResource(R.plurals.history_count, historyCount, historyCount)
}

@Composable
fun ShoppingList(
    items: List<Item>,
    showHint: Boolean,
    onToggle: (Item) -> Unit,
    onDelete: (Item) -> Unit,
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
                inHistory = false,
                onToggle = { onToggle(item) },
                onDelete = { onDelete(item) },
                onOpenActions = { onOpenActions(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
fun HistoryList(
    items: List<Item>,
    onToggle: (Item) -> Unit,
    onDelete: (Item) -> Unit,
    onOpenActions: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.CheckCircle,
            title = stringResource(R.string.empty_history_title),
            body = stringResource(R.string.empty_history_body),
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
                    inHistory = true,
                    onToggle = { onToggle(item) },
                    onDelete = { onDelete(item) },
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
