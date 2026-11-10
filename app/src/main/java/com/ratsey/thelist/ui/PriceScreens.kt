package com.ratsey.thelist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ratsey.thelist.R
import com.ratsey.thelist.data.prettyReceiptName
import com.ratsey.thelist.data.PricePoint
import com.ratsey.thelist.data.PriceSummary
import com.ratsey.thelist.ui.components.SectionLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the detail screen needs, derived from the price rows. */
data class ProductDetail(
    val summary: PriceSummary,
    val points: List<PricePoint>,
    val purchases: List<com.ratsey.thelist.data.PriceRow>,
)

fun formatMoney(value: Double, currency: String): String =
    currency + String.format(Locale.getDefault(), "%.2f", value)

private fun formatDay(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM yyyy"))

@Composable
fun ProductDetailScreen(detail: ProductDetail, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.onSurfaceVariant,
                )
            }
            Text(
                text = detail.summary.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LatestPriceCard(detail)
            }
            item {
                PriceChartCard(detail)
            }
            item {
                SectionLabel(text = stringResource(R.string.prices_history))
            }
            items(detail.purchases) { purchase ->
                PurchaseRow(
                    name = purchase.displayName,
                    price = formatMoney(purchase.unitPrice, purchase.currency),
                    quantity = purchase.quantity,
                    store = purchase.storeName,
                    date = formatDay(purchase.purchasedAt),
                    sourceText = purchase.sourceText,
                )
            }
        }
    }
}

@Composable
private fun LatestPriceCard(detail: ProductDetail) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.prices_latest),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatMoney(detail.summary.latestPrice, detail.summary.currency),
                style = MaterialTheme.typography.displaySmall,
                color = colors.onPrimaryContainer,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.prices_last_at,
                    detail.summary.latestStore,
                    formatDay(detail.summary.latestAt),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MiniStat(
                    label = stringResource(R.string.prices_best),
                    value = formatMoney(detail.summary.lowestPrice, detail.summary.currency),
                )
                MiniStat(
                    label = stringResource(R.string.prices_average),
                    value = formatMoney(detail.summary.averagePrice, detail.summary.currency),
                )
                MiniStat(
                    label = stringResource(R.string.prices_history),
                    value = if (detail.summary.timesBought == 1) {
                        "1"
                    } else {
                        detail.summary.timesBought.toString()
                    },
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PriceChartCard(detail: ProductDetail) {
    val colors = MaterialTheme.colorScheme
    val points = detail.points

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (points.size < 2) {
                Text(
                    text = stringResource(R.string.prices_chart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                PriceChart(
                    points = points,
                    currency = detail.summary.currency,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = formatDay(points.first().purchasedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = formatDay(points.last().purchasedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Hand-drawn price line: no chart library, so it can match the palette exactly. */
@Composable
fun PriceChart(points: List<PricePoint>, currency: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val min = remember(points) { points.minOf { it.unitPrice } }
    val max = remember(points) { points.maxOf { it.unitPrice } }
    val padding = ((max - min) * 0.2).takeIf { it > 0.05 } ?: (max * 0.15 + 0.2)

    BoxWithConstraints(modifier = modifier.height(150.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val low = (min - padding).toFloat()
        val high = (max + padding).toFloat()
        val range = (high - low).takeIf { it > 0.0001f } ?: 1f
        val count = points.size

        fun xFor(index: Int): Float =
            if (count <= 1) widthPx / 2f else widthPx * index / (count - 1).toFloat()

        fun yFor(value: Double): Float =
            heightPx - ((value.toFloat() - low) / range) * heightPx

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            for (line in 0..2) {
                val y = heightPx * line / 2f
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(0f, y),
                    end = Offset(widthPx, y),
                    strokeWidth = 1f,
                )
            }

            val line = Path()
            val area = Path()
            points.forEachIndexed { index, point ->
                val x = xFor(index)
                val y = yFor(point.unitPrice)
                if (index == 0) {
                    line.moveTo(x, y)
                    area.moveTo(x, heightPx)
                    area.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    area.lineTo(x, y)
                }
            }
            area.lineTo(xFor(count - 1), heightPx)
            area.close()

            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(colors.primary.copy(alpha = 0.20f), Color.Transparent),
                ),
            )
            drawPath(
                path = line,
                color = colors.primary,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            points.forEachIndexed { index, point ->
                val center = Offset(xFor(index), yFor(point.unitPrice))
                drawCircle(colors.surface, radius = 5.dp.toPx(), center = center)
                drawCircle(colors.primary, radius = 3.dp.toPx(), center = center)
            }
        }
    }
}

@Composable
private fun PurchaseRow(
    name: String,
    price: String,
    quantity: Double,
    store: String,
    date: String,
    sourceText: String?,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // The brand is what tells two purchases of the same thing apart: "coffee" at £1.40
                // and "coffee" at £3.50 are different jars, and only the receipt remembers which.
                Text(
                    text = sourceText?.let(::prettyReceiptName)?.takeIf { it.isNotBlank() } ?: name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // A price typed in has no shop, so the second line starts at the date rather
                    // than at a gap where a shop name would be.
                    text = listOfNotNull(
                        store.takeIf { it.isNotBlank() },
                        date,
                        if (quantity > 1.0) "×${quantity.toInt()}" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = price,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
        }
    }
}
