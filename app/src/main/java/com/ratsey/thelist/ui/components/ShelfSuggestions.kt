package com.ratsey.thelist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item

/**
 * Shelf rows that already answer for what is being typed, offered for a swipe instead of a second
 * "Milk". Nothing here reacts to a tap, for the same reason nothing else does: a thumb travelling
 * to the keyboard should not change the list. Swiping right puts the existing row back with its
 * history, its shop and its type intact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfSuggestions(
    matches: List<Item>,
    onRevive: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (matches.isEmpty()) return
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.add_shelf_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        matches.forEach { item ->
            key(item.id) {
                val state = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.StartToEnd) onRevive(item)
                        // The row leaves when the database says so, not before: a box left
                        // dismissed would be a blank gap that looks like a swipe that did nothing.
                        false
                    },
                    positionalThreshold = { distance -> distance * 0.32f },
                )

                SwipeToDismissBox(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    backgroundContent = {
                        if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.primary)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                Text(
                                    text = stringResource(R.string.add_shelf_reveal),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onPrimary,
                                )
                            }
                        }
                    },
                    enableDismissFromEndToStart = false,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            item.variant?.trim()?.takeIf { it.isNotEmpty() }?.let { variant ->
                                Text(
                                    text = " · $variant",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
