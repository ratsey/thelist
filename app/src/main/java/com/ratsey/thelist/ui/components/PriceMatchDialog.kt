package com.ratsey.thelist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.bestPerStore
import com.ratsey.thelist.data.priceWithUnit
import com.ratsey.thelist.ui.ProductDetail
import com.ratsey.thelist.ui.formatMoney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What each shop last charged, cheapest first, for the thing on the list.
 *
 * The question behind it is "should I buy this here or wait", so it is a comparison in one unit —
 * the union of the unit the last price was per, and every price restated into it — and a shop that
 * cannot be restated is left out rather than lined up as a raw number.
 */
@Composable
fun PriceMatchDialog(
    detail: ProductDetail,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val summary = detail.summary
    val stores = remember(detail) { bestPerStore(detail.points, summary.basis) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = summary.displayName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                summary.variant?.trim()?.takeIf { it.isNotEmpty() }?.let { variant ->
                    Text(
                        text = variant,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.price_match_best),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )

                if (stores.isEmpty()) {
                    Text(
                        text = stringResource(R.string.price_match_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    if (stores.size == 1) {
                        Text(
                            text = stringResource(R.string.price_match_one_shop),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    stores.forEach { storePrice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = storePrice.store,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (storePrice.cheapest) {
                                        FontWeight.Medium
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // When, and how often: a price from March is evidence, not a quote.
                                Text(
                                    text = buildString {
                                        append(shortDay(storePrice.at))
                                        if (storePrice.purchases > 1) {
                                            append(" · ×")
                                            append(storePrice.purchases)
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = priceWithUnit(
                                    formatMoney(storePrice.price, summary.currency),
                                    summary.unitLabel,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (storePrice.cheapest) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                color = if (storePrice.cheapest) colors.primary else colors.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenHistory) {
                Text(stringResource(R.string.price_match_history))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.price_match_done))
            }
        },
    )
}

private fun shortDay(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM"))
