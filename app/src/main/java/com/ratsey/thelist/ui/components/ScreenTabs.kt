package com.ratsey.thelist.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R

/**
 * Segmented switch between the four screens. A sliding indicator (rather than Material's
 * TabRow) keeps the header compact and reads instantly as one list seen four ways.
 */
@Composable
fun ScreenTabs(
    selected: Int,
    activeCount: Int,
    basketCount: Int,
    shelfCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(colors.surfaceVariant),
    ) {
        val segmentWidth = maxWidth / 4
        val indicatorOffset by animateDpAsState(
            targetValue = 3.dp + segmentWidth * selected,
            animationSpec = tween(durationMillis = 220),
            label = "tabIndicator",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset, y = 3.dp)
                .width(segmentWidth - 6.dp)
                .fillMaxHeight()
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface),
        )

        Row(Modifier.fillMaxSize()) {
            TabSegment(
                label = stringResource(R.string.tab_to_buy),
                count = activeCount,
                isSelected = selected == 0,
                onClick = { onSelect(0) },
                modifier = Modifier.weight(1f),
            )
            TabSegment(
                label = stringResource(R.string.tab_basket),
                count = basketCount,
                isSelected = selected == 1,
                onClick = { onSelect(1) },
                modifier = Modifier.weight(1f),
            )
            TabSegment(
                label = stringResource(R.string.tab_shelf),
                count = shelfCount,
                isSelected = selected == 2,
                onClick = { onSelect(2) },
                modifier = Modifier.weight(1f),
            )
            TabSegment(
                label = stringResource(R.string.tab_prices),
                count = 0,
                isSelected = selected == 3,
                onClick = { onSelect(3) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabSegment(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
            )
            if (count > 0) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}
