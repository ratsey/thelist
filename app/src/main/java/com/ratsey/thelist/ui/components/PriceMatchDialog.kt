package com.ratsey.thelist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.bestPerStore
import com.ratsey.thelist.data.priceWithUnit
import com.ratsey.thelist.ui.ProductDetail
import com.ratsey.thelist.ui.formatMoney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Comparing, and recording what you just found.
 *
 * The two belong together: the moment you are looking at what each shop charged is the moment you
 * are holding a price in your hand, so the same popup takes it. A recorded price goes onto the row
 * as its planned price and into the history at the row's shop, and the comparison above it
 * recomputes from that history, so the entry you just made shows up in its place immediately.
 */
@Composable
fun PriceMatchDialog(
    item: Item,
    detail: ProductDetail?,
    onCapture: (price: Double, priceUnit: String?) -> Unit,
    onOpenHistory: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val summary = detail?.summary
    val stores = remember(detail) { bestPerStore(detail?.points.orEmpty(), summary?.basis) }

    var amount by remember { mutableStateOf("") }
    var per by remember { mutableStateOf(item.priceUnit.orEmpty()) }
    val parsed = parseAmount(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.variant?.trim()?.takeIf { it.isNotEmpty() }?.let { variant ->
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
                    .heightIn(max = 360.dp)
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
                        modifier = Modifier.padding(top = 8.dp),
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
                                    text = storePrice.store.ifBlank {
                                        stringResource(R.string.price_match_no_shop)
                                    },
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
                                    formatMoney(storePrice.price, summary?.currency.orEmpty()),
                                    summary?.unitLabel,
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    text = stringResource(R.string.price_match_capture),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        singleLine = true,
                        isError = amount.isNotBlank() && parsed == null,
                        label = { Text(stringResource(R.string.placement_price)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = per,
                        onValueChange = { per = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.price_match_per)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (onOpenHistory != null) {
                    TextButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.price_match_history))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = parsed ?: return@TextButton
                    onCapture(price, per.trim().takeIf { it.isNotEmpty() })
                    amount = ""
                },
                enabled = parsed != null,
            ) {
                Text(stringResource(R.string.price_match_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.price_match_done))
            }
        },
    )
}

/** A typed amount: a comma for a decimal point is what most of the world types. */
private fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

private fun shortDay(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM"))
