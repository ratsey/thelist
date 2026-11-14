package com.ratsey.thelist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
 * Two offers, and which one wins.
 *
 * A page rather than a form: the two prices are the biggest things on it, the verdict is next, and
 * the arithmetic that produced the verdict is drawn as a pair of bars, so a glance answers the
 * question before any reading does. Everything else — the shop history, the record button — is
 * evidence, and it sits below the answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceCompareSheet(
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            // The header: what this is about, in the smallest space that still says it.
            Text(
                text = stringResource(R.string.compare_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.variant?.trim()?.takeIf { it.isNotEmpty() }?.let { variant ->
                Text(
                    text = variant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OfferCard(
                    label = stringResource(R.string.compare_here),
                    amount = hereAmount,
                    onAmount = { hereAmount = it },
                    per = herePer,
                    onPer = { herePer = it },
                    currency = currency,
                    winning = duel?.cheaper == Cheaper.HERE,
                    modifier = Modifier.weight(1f),
                )
                OfferCard(
                    label = stringResource(R.string.compare_other),
                    amount = otherAmount,
                    onAmount = { otherAmount = it },
                    per = otherPer,
                    onPer = { otherPer = it },
                    currency = currency,
                    winning = duel?.cheaper == Cheaper.OTHER,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            VerdictCard(
                duel = duel,
                herePrice = herePrice,
                otherPrice = otherPrice,
                hereBasis = hereBasis,
                currency = currency,
            )

            if (stores.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.price_match_best),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                stores.forEach { storePrice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
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
                        if (storePrice.cheapest) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .width(14.dp),
                            )
                        }
                        Text(
                            text = priceWithUnit(
                                formatMoney(storePrice.price, summary?.currency.orEmpty()),
                                summary?.unitLabel,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
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
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(stringResource(R.string.price_match_history))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val price = otherPrice ?: return@Button
                        onCapture(price, otherPer.trim().takeIf { it.isNotEmpty() })
                        otherAmount = ""
                    },
                    enabled = otherPrice != null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.price_match_save))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.price_match_done))
                }
            }
        }
    }
}

/** One side: a big editable price, and what that price buys underneath it. */
@Composable
private fun OfferCard(
    label: String,
    amount: String,
    onAmount: (String) -> Unit,
    per: String,
    onPer: (String) -> Unit,
    currency: String,
    winning: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (winning) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (winning) colors.onPrimaryContainer else colors.onSurface,
        border = if (winning) BorderStroke(1.dp, colors.primary) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (winning) colors.onPrimaryContainer else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))

            // The number is the point of the card, so it is set as such: currency small, figure
            // large, both on the same baseline.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currency,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (winning) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                BasicTextField(
                    value = amount,
                    onValueChange = onAmount,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (winning) colors.onPrimaryContainer else colors.onSurface,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    cursorBrush = SolidColor(if (winning) colors.onPrimaryContainer else colors.primary),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(2.dp))
            HorizontalDivider(
                color = if (winning) {
                    colors.onPrimaryContainer.copy(alpha = 0.3f)
                } else {
                    colors.outlineVariant
                },
            )
            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.price_match_per),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (winning) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = per,
                    onValueChange = onPer,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (winning) colors.onPrimaryContainer else colors.onSurface,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    cursorBrush = SolidColor(if (winning) colors.onPrimaryContainer else colors.primary),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The answer, and the two rates drawn to scale underneath it. */
@Composable
private fun VerdictCard(
    duel: com.ratsey.thelist.data.OfferDuel?,
    herePrice: Double?,
    otherPrice: Double?,
    hereBasis: PriceBasis?,
    currency: String,
) {
    val colors = MaterialTheme.colorScheme

    val settled = duel != null && herePrice != null && otherPrice != null
    val tint = if (settled) colors.primaryContainer else colors.surfaceVariant
    val onTint = if (settled) colors.onPrimaryContainer else colors.onSurfaceVariant
    val headline: String
    val detail: String?

    when {
        herePrice == null || otherPrice == null -> {
            headline = stringResource(R.string.compare_enter_both)
            detail = null
        }

        duel == null -> {
            headline = stringResource(R.string.compare_incomparable)
            detail = null
        }

        duel.cheaper == Cheaper.SAME -> {
            headline = stringResource(R.string.compare_same)
            detail = null
        }

        else -> {
            headline = stringResource(
                if (duel.cheaper == Cheaper.HERE) {
                    R.string.compare_here_cheaper
                } else {
                    R.string.compare_other_cheaper
                },
                "${(duel.difference * 100).roundToInt()}%",
            )
            detail = null
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tint,
        contentColor = onTint,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            detail?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }

            if (duel != null) {
                Spacer(Modifier.height(12.dp))
                val longest = maxOf(duel.herePer, duel.otherPer)
                RateBar(
                    label = stringResource(R.string.compare_here),
                    rate = duel.herePer,
                    longest = longest,
                    unitLabel = hereBasis?.label,
                    currency = currency,
                    emphasise = duel.cheaper == Cheaper.HERE,
                    settled = settled,
                )
                RateBar(
                    label = stringResource(R.string.compare_other),
                    rate = duel.otherPer,
                    longest = longest,
                    // Both bars are drawn in the same unit, which is the left offer's.
                    unitLabel = hereBasis?.label,
                    currency = currency,
                    emphasise = duel.cheaper == Cheaper.OTHER,
                    settled = settled,
                )
            }
        }
    }
}

/** A price drawn to scale against the dearer one, so "cheaper" is a length as well as a number. */
@Composable
private fun RateBar(
    label: String,
    rate: Double,
    longest: Double,
    unitLabel: String?,
    currency: String,
    emphasise: Boolean,
    settled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val fraction = if (longest <= 0.0) 1f else (rate / longest).toFloat().coerceIn(0.04f, 1f)

    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (settled) colors.onPrimaryContainer.copy(alpha = 0.75f) else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = priceWithUnit(formatMoney(rate, currency), unitLabel),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !settled -> colors.onSurfaceVariant
                    emphasise -> colors.onPrimaryContainer
                    else -> colors.onPrimaryContainer.copy(alpha = 0.7f)
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surface.copy(alpha = 0.45f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (emphasise) colors.primary else colors.onPrimaryContainer.copy(alpha = 0.35f),
                    ),
            )
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
