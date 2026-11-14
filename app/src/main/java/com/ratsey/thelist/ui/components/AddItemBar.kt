package com.ratsey.thelist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import androidx.compose.material3.IconButton

/**
 * Thumb-reachable add bar pinned above the keyboard. Adding keeps focus and the keyboard
 * up, because a shopping list is filled in bursts.
 */
@Composable
fun AddItemBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    focusRequester: FocusRequester,
    /** Whether the shelf panel is showing, and how much there is on the shelf to show. */
    shelfFilterOn: Boolean,
    shelfCount: Int,
    onToggleShelfFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.background, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // A filter rather than a hint that may or may not appear: it shows what the shelf
            // already has for this word, and with nothing typed it shows the shelf itself, so
            // there is always a way to reach last week's row instead of typing it again.
            IconButton(onClick = onToggleShelfFilter, enabled = shelfCount > 0) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = stringResource(
                        if (shelfFilterOn) {
                            R.string.add_shelf_filter_hide
                        } else {
                            R.string.add_shelf_filter_show
                        },
                    ),
                    tint = when {
                        shelfFilterOn -> colors.primary
                        shelfCount > 0 -> colors.onSurfaceVariant
                        else -> colors.outline
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceVariant,
                modifier = Modifier.weight(1f),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.add_item_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            FilledIconButton(
                onClick = onAdd,
                enabled = value.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.surfaceVariant,
                    disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.size(54.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_item),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
