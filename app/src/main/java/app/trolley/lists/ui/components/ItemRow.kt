package app.trolley.lists.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trolley.lists.R
import app.trolley.lists.data.Item

/**
 * One row: swipe right to move the item to the other list, swipe left to delete,
 * tap the circle to move it, tap the row for rename/delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemRow(
    item: Item,
    inHistory: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                    true
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    true
                }

                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { distance -> distance * 0.32f },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
        backgroundContent = { SwipeReveal(dismissState.dismissDirection, inHistory) },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        ItemCard(
            item = item,
            inHistory = inHistory,
            onToggle = onToggle,
            onOpenActions = onOpenActions,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(
    item: Item,
    inHistory: Boolean,
    onToggle: () -> Unit,
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
                .combinedClickable(onClick = onOpenActions, onLongClick = onOpenActions)
                .heightIn(min = 62.dp)
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleCircle(completed = completed, onClick = onToggle)

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
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            )

            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = colors.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ToggleCircle(completed: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(if (completed) 1f else 0f, label = "checkProgress")
    val label = stringResource(
        if (completed) R.string.action_add_back else R.string.action_move_to_history,
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(if (completed) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
                .border(
                    width = 1.6.dp,
                    color = if (completed) colors.primary else colors.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier
                        .size(14.dp)
                        .alpha(progress)
                        .scale(0.6f + 0.4f * progress),
                )
            }
        }
    }
}

@Composable
private fun SwipeReveal(direction: SwipeToDismissBoxValue, inHistory: Boolean) {
    val colors = MaterialTheme.colorScheme
    val isDelete = direction == SwipeToDismissBoxValue.EndToStart
    val isMove = direction == SwipeToDismissBoxValue.StartToEnd
    if (!isDelete && !isMove) return

    val background = if (isDelete) colors.error else colors.primary
    val contentColor = if (isDelete) colors.onError else colors.onPrimary
    val label = when {
        isDelete -> stringResource(R.string.swipe_delete)
        inHistory -> stringResource(R.string.swipe_add_back)
        else -> stringResource(R.string.swipe_got_it)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isDelete) Arrangement.End else Arrangement.Start,
    ) {
        if (isDelete) {
            Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Delete,
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
