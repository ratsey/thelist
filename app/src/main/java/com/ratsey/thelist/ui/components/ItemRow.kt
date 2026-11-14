package com.ratsey.thelist.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.PriceSummary
import com.ratsey.thelist.data.parsePriceBasis
import com.ratsey.thelist.data.priceIn
import com.ratsey.thelist.data.priceWithUnit
import com.ratsey.thelist.ui.formatMoney
import com.ratsey.thelist.ui.LocalCurrency

/**
 * "Walmart · A6", or just the aisle when no shop is set, or null when nothing is known. The shop
 * name is looked up rather than copied onto the row, so renaming a shop reaches every item at once.
 *
 * While you are standing in a shop (a [currentStoreId] chosen in the header) its rows need only the
 * aisle — repeating the shop name on every line is wasted width — and another shop's aisle says
 * nothing about the shop you are in, so only its name survives.
 */
fun placeLabel(
    item: Item,
    storeNames: Map<String, String>,
    currentStoreId: String? = null,
): String? {
    val store = storeNames[item.storeId]
    val aisle = item.location?.takeIf { it.isNotBlank() }
    val parts = when {
        currentStoreId == null || item.storeId == null -> listOfNotNull(store, aisle)
        item.storeId == currentStoreId -> listOfNotNull(aisle)
        else -> listOfNotNull(store)
    }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

/**
 * Everything the row says besides the name, in one string: where it lives, how much to buy, what
 * it should cost, then last and best paid. One line because the list is scanned at arm's length in
 * a shop, where a second line per item is three fewer items on the screen.
 */
@Composable
fun itemMeta(
    item: Item,
    storeNames: Map<String, String>,
    summary: PriceSummary?,
    currentStoreId: String? = null,
): String? {
    // A planned price is typed now, so it is in the app's currency; only what the till recorded
    // keeps its own, which is also what the arrowed best price is shown in.
    val planned = item.price
    val parts = buildList {
        placeLabel(item, storeNames, currentStoreId)?.let { add(it) }
        // What you decided in the kitchen comes before what the till once charged: the amount and
        // the price you planned are the things you need with a trolley in front of you.
        item.amount?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }

        // Money is the widest thing a row carries, so it gets at most two short tokens. A plan is a
        // bare figure, and the best price earns an arrowed one only when it is genuinely cheaper:
        // "last $3.20 · best $2.80" on every line bought no decision and squeezed out the name.
        when {
            planned != null -> {
                // "$1.25/lb" rather than "$1.25": the amount sits before it, so the two facts stay
                // apart and a rate is never mistaken for what the whole thing costs.
                add(priceWithUnit(formatMoney(planned, LocalCurrency.current), item.priceUnit))
                // The best price earns an arrow only when it is per the same thing as the plan: a
                // kilo that merely looks cheaper than a pound is not cheaper.
                val best = summary?.basis?.let { basis ->
                    priceIn(summary.lowestPrice, basis, parsePriceBasis(item.priceUnit))
                }
                if (best != null && best < planned - PRICE_EPSILON) {
                    add(
                        stringResource(
                            R.string.row_low,
                            priceWithUnit(formatMoney(best, LocalCurrency.current), item.priceUnit),
                        ),
                    )
                }
            }

            summary != null -> {
                add(
                    stringResource(
                        R.string.row_last,
                        priceWithUnit(
                            formatMoney(summary.latestPrice, summary.currency),
                            summary.unitLabel,
                        ),
                    ),
                )
                if (summary.lowestPrice < summary.latestPrice - PRICE_EPSILON) {
                    add(
                        stringResource(
                            R.string.row_low,
                            priceWithUnit(
                                formatMoney(summary.lowestPrice, summary.currency),
                                summary.unitLabel,
                            ),
                        ),
                    )
                }
            }
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** A cent, near enough: cheaper by less than this is not a saving worth a symbol. */
private const val PRICE_EPSILON = 0.005

/**
 * One direction of a row's swipe: what it does, how the reveal explains it, and whether it is a
 * neutral move (putting something away) rather than progress.
 */
data class SwipeAction(
    @StringRes val label: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val muted: Boolean = false,
)

/**
 * One row. Swipe right to move it forward through the shop and left to move it back a step; both
 * are reversible, so no swipe here can lose anything.
 *
 * Nothing on the row reacts to a tap. A thumb brushing the list while you shop should never move an
 * item or bury it, so every change of state is a deliberate swipe. A long press shows what you have
 * paid for the thing; the small ⋮ opens the menu — rename, put away, delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemRow(
    item: Item,
    swipeRight: SwipeAction,
    swipeLeft: SwipeAction,
    onOpenPrices: () -> Unit,
    onOpenActions: () -> Unit,
    /** Shop, aisle, last and best paid, already joined; null when the row knows none of it. */
    meta: String? = null,
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
                SwipeToDismissBoxValue.StartToEnd -> swipeRight.onClick()
                SwipeToDismissBoxValue.EndToStart -> swipeLeft.onClick()
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
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeReveal(swipeRight, alignEnd = false)
                SwipeToDismissBoxValue.EndToStart -> SwipeReveal(swipeLeft, alignEnd = true)
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        ItemCard(
            item = item,
            meta = meta,
            onOpenPrices = onOpenPrices,
            onOpenActions = onOpenActions,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(
    item: Item,
    meta: String?,
    onOpenPrices: () -> Unit,
    onOpenActions: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A tap lands nowhere on purpose: a long press shows prices, the ⋮ opens the menu.
                .combinedClickable(onClick = {}, onLongClick = onOpenPrices)
                .heightIn(min = 44.dp)
                .padding(start = 16.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The name, and under it which version of the thing this is: italic, so it reads as a
            // qualifier rather than part of the name, and only when there is one — a row without a
            // type keeps its single line and its 44dp.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.variant?.trim()?.takeIf { it.isNotEmpty() }?.let { variant ->
                    Text(
                        text = variant,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Shop, aisle, last and best paid, all on the same line as the name so an item stays
            // one row tall. When there is not enough width it is this that gives way, not the name.
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 4.dp, top = 10.dp, bottom = 10.dp),
                )
            }

            IconButton(
                onClick = onOpenActions,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_menu),
                    tint = colors.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SwipeReveal(action: SwipeAction, alignEnd: Boolean) {
    val colors = MaterialTheme.colorScheme
    // A neutral move (putting something away) stays a soft surface colour; progress earns the
    // accent. Neither is a delete, so neither is red.
    val background = if (action.muted) colors.secondaryContainer else colors.primary
    val contentColor = if (action.muted) colors.onSecondaryContainer else colors.onPrimary

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        if (alignEnd) {
            Text(
                text = stringResource(action.label),
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(action.label),
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
