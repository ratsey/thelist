package com.ratsey.thelist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Cheaper
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.PriceBasis
import com.ratsey.thelist.data.bestPerStore
import com.ratsey.thelist.data.compareOffers
import com.ratsey.thelist.data.parsePriceBasis
import com.ratsey.thelist.data.priceIn
import com.ratsey.thelist.data.priceWithUnit
import com.ratsey.thelist.ui.LocalCurrency
import com.ratsey.thelist.ui.ProductDetail
import com.ratsey.thelist.ui.formatMoney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Two offers side by side, and an answer.
 *
 * The left column starts as what the list already says — the price you planned or last paid — and
 * the right is what you are looking at now. Both take a price and what it is for, because "$9.99"
 * and "$12.99" are not comparable until each says whether it buys 20 or 30, a pound or a kilo; the
 * second offer is restated into the first one's unit and the verdict is a percentage either way.
 *
 * Below that is the evidence it came from: every shop's best price, and the link to the full
 * history. Saving applies the right-hand price to the row, where it becomes the plan and the newest
 * observation.
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
    val currency = LocalCurrency.current
    val summary = detail?.summary
    val stores = remember(detail) { bestPerStore(detail?.points.orEmpty(), summary?.basis) }

    val listedPrice = item.price ?: summary?.latestPrice
    val listedUnit = item.priceUnit ?: summary?.unitLabel

    var hereAmount by remember(item.id) { mutableStateOf("") }
    var herePer by remember(item.id) { mutableStateOf(listedUnit.orEmpty()) }
    var otherAmount by remember(item.id) { mutableStateOf("") }
    var otherPer by remember(item.id) { mutableStateOf(listedUnit.orEmpty()) }

    // Prefilled once the row's own figure is known, and only while the field is untouched: the
    // history arrives a frame after the dialog opens, and overwriting typing would be worse.
    LaunchedEffect(listedPrice) {
        if (hereAmount.isBlank() && listedPrice != null) hereAmount = plainAmount(listedPrice)
    }
    LaunchedEffect(listedUnit) {
        val unit = listedUnit.orEmpty()
        if (herePer.isBlank()) herePer = unit
        if (otherPer.isBlank()) otherPer = unit
    }

    val herePrice = parseAmount(hereAmount)
    val otherPrice = parseAmount(otherAmount)
    val hereBasis = remember(herePer) { parsePriceBasis(herePer) }
    val otherBasis = remember(otherPer) { parsePriceBasis(otherPer) }
    val duel = remember(herePrice, hereBasis, otherPrice, otherBasis) {
        if (herePrice == null || otherPrice == null) {
            null
        } else {
            compareOffers(herePrice, hereBasis, otherPrice, otherBasis)
        }
    }

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
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OfferColumn(
                        label = stringResource(R.string.compare_here),
                        amount = hereAmount,
                        onAmount = { hereAmount = it },
                        per = herePer,
                        onPer = { herePer = it },
                        modifier = Modifier.weight(1f),
                    )
                    OfferColumn(
                        label = stringResource(R.string.compare_other),
                        amount = otherAmount,
                        onAmount = { otherAmount = it },
                        per = otherPer,
                        onPer = { otherPer = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Verdict(duel, herePrice, otherPrice, hereBasis, currency)

                if (stores.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                    Text(
                        text = stringResource(R.string.price_match_best),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    stores.forEach { storePrice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = storePrice.store.ifBlank {
                                        stringResource(R.string.price_match_no_shop)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (storePrice.cheapest) {
                                        FontWeight.Medium
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (storePrice.cheapest) colors.primary else colors.onSurface,
                                fontWeight = if (storePrice.cheapest) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        }
                    }
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
                    val price = otherPrice ?: return@TextButton
                    onCapture(price, otherPer.trim().takeIf { it.isNotEmpty() })
                    otherAmount = ""
                },
                enabled = otherPrice != null,
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

/** One side of the comparison: what it costs, and what that buys. */
@Composable
private fun OfferColumn(
    label: String,
    amount: String,
    onAmount: (String) -> Unit,
    per: String,
    onPer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = onAmount,
            singleLine = true,
            isError = amount.isNotBlank() && parseAmount(amount) == null,
            label = { Text(stringResource(R.string.placement_price)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = per,
            onValueChange = onPer,
            singleLine = true,
            label = { Text(stringResource(R.string.price_match_per)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The answer, in one sentence, with the arithmetic under it so it is not a black box. */
@Composable
private fun Verdict(
    duel: com.ratsey.thelist.data.OfferDuel?,
    herePrice: Double?,
    otherPrice: Double?,
    hereBasis: PriceBasis?,
    currency: String,
) {
    val colors = MaterialTheme.colorScheme

    val text: String
    val tint: Color
    val onTint: Color
    when {
        herePrice == null || otherPrice == null -> {
            text = stringResource(R.string.compare_enter_both)
            tint = colors.surfaceVariant
            onTint = colors.onSurfaceVariant
        }

        duel == null -> {
            text = stringResource(R.string.compare_incomparable)
            tint = colors.surfaceVariant
            onTint = colors.error
        }

        duel.cheaper == Cheaper.SAME -> {
            text = stringResource(R.string.compare_same)
            tint = colors.surfaceVariant
            onTint = colors.onSurfaceVariant
        }

        else -> {
            val percent = "${(duel.difference * 100).roundToInt()}%"
            text = stringResource(
                if (duel.cheaper == Cheaper.HERE) {
                    R.string.compare_here_cheaper
                } else {
                    R.string.compare_other_cheaper
                },
                percent,
            )
            tint = colors.primaryContainer
            onTint = colors.onPrimaryContainer
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint,
        contentColor = onTint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            // Both offers in the left one's unit: the same money for the same thing, twice.
            if (duel != null && hereBasis != null) {
                Text(
                    text = priceWithUnit(formatMoney(duel.herePer, currency), hereBasis.label) +
                        "  ·  " +
                        priceWithUnit(formatMoney(duel.otherPer, currency), hereBasis.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = onTint.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** A typed amount: a comma for a decimal point is what most of the world types. */
private fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

private fun plainAmount(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

private fun shortDay(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM"))
