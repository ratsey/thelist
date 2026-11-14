package com.ratsey.thelist.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.formatPriceField
import com.ratsey.thelist.data.parsePriceField

/**
 * The end of a shop.
 *
 * Everything in the basket is listed with a price field beside it, so the till total can be typed in
 * one pass rather than one item at a time — and a line can still be held to fix its shop, aisle or
 * amount first. Confirming empties the basket onto the shelf, which is what makes next week's list a
 * row of swipes instead of a page of typing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CheckoutSheet(
    items: List<Item>,
    storeName: String?,
    onDismiss: () -> Unit,
    onOpenDetails: (Item) -> Unit,
    onFinish: (Map<String, Double?>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Typed prices live here until the shop is finished; a row that already has one starts filled,
    // so the sheet is a review of what you planned as much as a place to correct it.
    var typed by remember {
        mutableStateOf(items.associate { it.id to formatPriceField(it.price) })
    }

    val parsed = items.associate { it.id to parsePriceField(typed[it.id].orEmpty()) }
    val invalid = items.any { item ->
        val text = typed[item.id].orEmpty()
        text.isNotBlank() && parsed[item.id] == null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.checkout_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.checkout_body, items.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (storeName != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.checkout_at_shop, storeName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEach { item ->
                    val text = typed[item.id].orEmpty()
                    val rowInvalid = text.isNotBlank() && parsed[item.id] == null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Nothing here acts on a tap: hold the line to change where it lives.
                            .combinedClickable(onClick = {}, onLongClick = { onOpenDetails(item) })
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val detail = listOfNotNull(
                                item.variant?.takeIf { it.isNotBlank() }?.trim(),
                                item.amount?.takeIf { it.isNotBlank() }?.trim(),
                                item.priceUnit?.takeIf { it.isNotBlank() }?.let { "per ${it.trim()}" },
                            ).joinToString(" · ")
                            if (detail.isNotEmpty()) {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = text,
                            onValueChange = { value -> typed = typed + (item.id to value) },
                            singleLine = true,
                            isError = rowInvalid,
                            label = { Text(stringResource(R.string.placement_price)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(112.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // No basket total on purpose: this app keeps what a thing costs, and adding a
            // per-pound rate to a per-each price would be a number that means nothing.
            Text(
                text = stringResource(R.string.checkout_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (invalid) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.checkout_invalid_price),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(
                    onClick = { onFinish(parsed) },
                    enabled = !invalid && items.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.checkout_confirm))
                }
            }
        }
    }
}
