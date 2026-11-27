package com.ratsey.thelist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.PriceSummary
import com.ratsey.thelist.data.ShelfGroup
import com.ratsey.thelist.data.shelfGroups
import com.ratsey.thelist.data.versionLabel
import com.ratsey.thelist.receipt.ItemMatcher
import java.util.Locale
import com.ratsey.thelist.data.historyKey

/**
 * The shelf, on its own tab: everything off the list but kept. One swipe, and it only ever adds —
 * swipe a name right to put it straight back on the list. Nothing here deletes from a swipe or a
 * tap: hold the row, or tap its ⋮, and delete from the menu.
 *
 * That is what lets every swipe in the app be safe to try: a mis-swipe moves a row and can be
 * undone, and destruction is never one careless gesture away.
 *
 * Versions of one product are drawn as one line. Full fat and skimmed keep their own prices, but
 * printing them as two rows both called "Milk" is the duplication the types were meant to stop —
 * so the line says how many versions there are and the swipe asks which one.
 */
@Composable
fun ShelfScreen(
    items: List<Item>,
    storeNames: Map<String, String>,
    summaries: Map<String, PriceSummary>,
    currentStoreId: String? = null,
    /** Whether the filter field is showing; the header's search button owns this. */
    searchOpen: Boolean = false,
    hintVisible: Boolean = true,
    onHideHint: () -> Unit = {},
    onNeverShowHint: () -> Unit = {},
    onAdd: (Item) -> Unit,
    onOpenActions: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Archive,
            title = stringResource(R.string.shelf_empty_title),
            body = stringResource(R.string.shelf_empty_body),
            modifier = modifier,
        )
        return
    }

    var filter by rememberSaveable { mutableStateOf("") }
    // Which gathered line's ⋮ was tapped. Its versions are chosen from here rather than printed as
    // rows, so rename, placement and delete stay one menu away on every version.
    var variantMenu by remember { mutableStateOf<ShelfGroup?>(null) }

    // Closing the search gives the rows their height back, and the filter goes with the field:
    // a hidden filter would quietly show a shorter shelf with nothing to explain it.
    LaunchedEffect(searchOpen) {
        if (!searchOpen) filter = ""
    }

    val groups = remember(items) { shelfGroups(items) }
    val shown = remember(groups, filter, storeNames) {
        val needle = filter.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) {
            groups
        } else {
            // The aisle is searchable too: typing "A6" should show what is in it. Searching asks
            // what a row knows, not what the row prints — the label drops the aisle when no shop
            // is chosen, but a shopper hunting for A6 still means A6. A version's name counts
            // too, because a gathered line no longer prints "Skimmed" anywhere.
            groups.filter { group ->
                group.items.any { item ->
                    item.name.lowercase(Locale.ROOT).contains(needle) ||
                        item.variant?.lowercase(Locale.ROOT)?.contains(needle) == true ||
                        item.location?.lowercase(Locale.ROOT)?.contains(needle) == true ||
                        storeNames[item.storeId]?.lowercase(Locale.ROOT)?.contains(needle) == true
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Asked for, not always there: a filter you are not using is a row of the list you cannot
        // see, and the shelf is the screen where rows are the point.
        if (searchOpen) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.shelf_filter)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.shelf_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // In the list rather than above it: help that has been read scrolls away with the
                // rows instead of holding a strip of the screen for good.
                if (hintVisible) {
                    item(key = "shelf-hint") {
                        ShelfHint(onDismiss = onHideHint, onDismissForever = onNeverShowHint)
                    }
                }
                items(shown, key = { group -> group.key }) { group ->
                    ShelfRow(
                        name = group.label,
                        variant = group.variant,
                        variantsAvailable = group.variants.size,
                        meta = groupMeta(group, storeNames, summaries, currentStoreId),
                        onAdd = { onAdd(group.items.first()) },
                        // A gathered line has no single row to open, so ask which version first.
                        onOpenActions = {
                            if (group.variants.isEmpty()) {
                                onOpenActions(group.items.first())
                            } else {
                                variantMenu = group
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    variantMenu?.let { group ->
        AlertDialog(
            onDismissRequest = { variantMenu = null },
            title = {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.shelf_variants_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    group.items.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    variantMenu = null
                                    onOpenActions(row)
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = versionLabel(row) ?: stringResource(R.string.shelf_no_type),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { variantMenu = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

/**
 * What a line says besides its name. A single row states its rate, as the shelf always has — that
 * is the one column of numbers you read down. A gathered line cannot: the rate belongs to each
 * version, and printing one version's price under the product's name would price all of them. So it
 * states only where they all live, and only when they all agree on it.
 */
@Composable
private fun groupMeta(
    group: ShelfGroup,
    storeNames: Map<String, String>,
    summaries: Map<String, PriceSummary>,
    currentStoreId: String?,
): String? {
    val one = group.items.singleOrNull()
    if (one == null) {
        return group.items
            .map { item -> placeLabel(item, storeNames, currentStoreId) }
            .distinct()
            .singleOrNull()
    }
    return itemMeta(
        item = one,
        storeNames = storeNames,
        summary = summaries[historyKey(ItemMatcher.productKey(one.name), one.variant)],
        currentStoreId = currentStoreId,
        // The shelf is where things are compared, so it carries the rate and only the rate: one
        // column of numbers to read down.
        rateOnly = true,
    )
}

/**
 * The shelf's help, on screen until it has been read. Two ways out, because they are different
 * answers: the × forgets it for this run of the app, "Don't show again" stores the choice.
 */
@Composable
private fun ShelfHint(onDismiss: () -> Unit, onDismissForever: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.primary.copy(alpha = 0.07f))
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.shelf_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.update_action_dismiss),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = stringResource(R.string.shelf_hint_never),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDismissForever)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(
    name: String,
    variant: String?,
    /** How many versions the line gathers; 0 when it stands for a single row. */
    variantsAvailable: Int,
    meta: String?,
    onAdd: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) {
                return@rememberSwipeToDismissBoxState false
            }

            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onAdd()

            // The row is not gone yet: the database flow drops it from the shelf a frame later.
            // Springing back keeps a failed or late update from leaving a blank gap.
            false
        },
        positionalThreshold = { distance -> distance * 0.32f },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = { ShelfReveal(dismissState.dismissDirection) },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // A tap lands nowhere on purpose; only a long press (or the ⋮) opens the menu.
                    .combinedClickable(onClick = {}, onLongClick = onOpenActions)
                    .padding(start = 18.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Same shape as a list row: the type is the line under the name, in italics, and
                // only when there is one, so the shelf reads like the list it feeds.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp, top = 13.dp, bottom = 13.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (variantsAvailable > 1) {
                        // Not a type, so not italic: it is the count of types you can choose from.
                        Text(
                            text = pluralStringResource(
                                R.plurals.shelf_variants_available,
                                variantsAvailable,
                                variantsAvailable,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        variant?.trim()?.takeIf { it.isNotEmpty() }?.let { type ->
                            Text(
                                text = type,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (meta != null) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        // Fills its share rather than taking what it needs: a short rate on one row
                        // and a long one on the next would otherwise leave the ⋮ at a different
                        // distance from the edge on every line.
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp, top = 13.dp, bottom = 13.dp),
                    )
                }

                IconButton(
                    onClick = onOpenActions,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.action_menu),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfReveal(direction: SwipeToDismissBoxValue) {
    if (direction != SwipeToDismissBoxValue.StartToEnd) return

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.swipe_add_back),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
