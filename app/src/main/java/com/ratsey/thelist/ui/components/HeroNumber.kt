package com.ratsey.thelist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * A money field that looks like the number matters.
 *
 * The price is the one thing on these sheets worth reading at arm's length, so it is set large with
 * the currency small beside it and a hairline under it, rather than inside a box the same size as
 * the field for an aisle. It is the same shape as the two offer cards on the comparison sheet.
 */
@Composable
fun HeroNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val colors = MaterialTheme.colorScheme
    val textColor = if (isError) colors.error else colors.onSurface

    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (currency != null) {
                Text(
                    text = currency,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = imeAction,
                ),
                keyboardActions = if (onDone != null) {
                    KeyboardActions(onDone = { onDone() })
                } else {
                    KeyboardActions.Default
                },
                cursorBrush = SolidColor(if (isError) colors.error else colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
            )
        }
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = if (isError) colors.error else colors.outlineVariant)
    }
}
