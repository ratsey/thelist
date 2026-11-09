package com.ratsey.thelist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item

/**
 * One row: right is on, left is off. Swipe right to move the item to the other list, swipe left to
 * put it away on the shelf; both are reversible, so no swipe here can lose anything.
 *
 * Nothing on the row reacts to a tap. A thumb brushing the list while you shop should never move an
 * item or bury it, so every change of state is a deliberate swipe. A long press shows what you have
 * paid for the thing; the small ⋮ opens the menu — rename, put away, delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemRow(
    item: Item,
    inBasket: Boolean,
    onToggle: () -> Unit,
    onPutAway: () -> Unit,
    onOpenPrices: () -> Unit,
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
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggle()
                SwipeToDismissBoxValue.EndToStart -> onPutAway()
                SwipeToDismissBoxValue.Settled -> Unit
            }

            // The action is done, but the row is not gone yet: the database flow takes it out of
            // this list a frame later. Letting the box stay dismissed leaves a blank gap whenever
            // that update is late or does not arrive, which looks like a swipe that did nothing.
            false
        },
        positionalThreshold = { distance -> distance * 0.32f },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
        backgroundContent = { SwipeReveal(dismissState.dismissDirection, inBasket) },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        ItemCard(item = item, onOpenPrices = onOpenPrices, onOpenActions = onOpenActions)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(
    item: Item,
    onOpenPrices: () -> Unit,
    onOpenActions: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val completed = item.completedAt != null

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A tap lands nowhere on purpose: a long press shows prices, the ⋮ opens the menu.
                .combinedClickable(onClick = {}, onLongClick = onOpenPrices)
                .heightIn(min = 62.dp)
                .padding(start = 18.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (completed) FontWeight.Normal else FontWeight.Medium,
                color = if (completed) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp, top = 16.dp, bottom = 16.dp),
            )

            IconButton(
                onClick = onOpenActions,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_menu),
                    tint = colors.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SwipeReveal(direction: SwipeToDismissBoxValue, inBasket: Boolean) {
    val colors = MaterialTheme.colorScheme
    val isAway = direction == SwipeToDismissBoxValue.EndToStart
    val isMove = direction == SwipeToDismissBoxValue.StartToEnd
    if (!isAway && !isMove) return

    // Nothing here is destructive: the left gesture files the item on the shelf, so it stays a
    // neutral surface colour rather than the error red a delete would earn.
    val background = if (isAway) colors.secondaryContainer else colors.primary
    val contentColor = if (isAway) colors.onSecondaryContainer else colors.onPrimary
    val label = when {
        isAway -> stringResource(R.string.swipe_put_away)
        inBasket -> stringResource(R.string.swipe_add_back)
        else -> stringResource(R.string.swipe_got_it)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isAway) Arrangement.End else Arrangement.Start,
    ) {
        if (isAway) {
            Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Archive,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}
