package com.ratsey.thelist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import java.util.Locale

/**
 * The shelf, on its own tab: everything off the list but kept. Same reading as everywhere else —
 * right is on, left is off: swipe a name right to put it straight back on the list, left to delete
 * it for good. That left swipe is the only destructive gesture in the app, which is what lets every
 * swipe on the list and the basket be safe to try.
 *
 * Like the list rows, nothing here acts on a tap.
 */
@Composable
fun ShelfScreen(
    items: List<Item>,
    onAdd: (Item) -> Unit,
    onDelete: (Item) -> Unit,
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
    val shown = remember(items, filter) {
        val needle = filter.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) {
            items
        } else {
            items.filter { it.name.lowercase(Locale.ROOT).contains(needle) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.shelf_filter)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            text = stringResource(R.string.shelf_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp),
        )
        Spacer(Modifier.height(8.dp))

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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { item ->
                    ShelfRow(
                        name = item.name,
                        onAdd = { onAdd(item) },
                        onDelete = { onDelete(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfRow(
    name: String,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) {
                return@rememberSwipeToDismissBoxState false
            }

            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onAdd()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }

            // Never leave the row dismissed: the shelf drops it when the change lands, and a row
            // that stays dismissed without being removed is just a blank gap.
            false
        },
        positionalThreshold = { distance -> distance * 0.32f },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
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
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ShelfReveal(direction: SwipeToDismissBoxValue) {
    val colors = MaterialTheme.colorScheme
    val isDelete = direction == SwipeToDismissBoxValue.EndToStart
    val isAdd = direction == SwipeToDismissBoxValue.StartToEnd
    if (!isDelete && !isAdd) return

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDelete) colors.errorContainer else colors.primary)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isDelete) Arrangement.End else Arrangement.Start,
    ) {
        val contentColor = if (isDelete) colors.onErrorContainer else colors.onPrimary
        val labelRes = if (isDelete) R.string.swipe_delete else R.string.swipe_add_back
        val icon = if (isDelete) Icons.Rounded.Delete else Icons.Rounded.Add

        if (isDelete) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
        if (!isDelete) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
