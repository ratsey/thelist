package com.ratsey.thelist.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    draft: ReceiptDraft,
    onDraftChange: ((ReceiptDraft) -> ReceiptDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var editingLine by remember { mutableStateOf<DraftLine?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    BackHandler(onBack = onCancel)

    // Only list matches can move; a basket match is already where a swipe would have put it.
    val matchedCount = draft.lines.count {
        it.include && it.matchedItemId != null && !it.matchedFromBasket
    }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.scan_cancel),
                        tint = colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.scan_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReceiptPhotoThumb(path = draft.photoPath)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.storeName,
                                onValueChange = { value -> onDraftChange { it.copy(storeName = value) } },
                                label = { Text(stringResource(R.string.scan_store)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LabeledField(
                                    label = stringResource(R.string.scan_date),
                                    value = formatDraftDate(draft.purchasedAt),
                                    onClick = { pickingDate = true },
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = draft.total?.let { formatPlain(it) } ?: "",
                                    onValueChange = { value ->
                                        onDraftChange { it.copy(total = value.toDoubleOrNull()) }
                                    },
                                    label = { Text(stringResource(R.string.scan_total)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(104.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.scan_items) + " · " +
                            draft.lines.count { it.include },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }

                items(draft.lines, key = { it.id }) { line ->
                    DraftLineRow(
                        line = line,
                        currency = draft.currency,
                        onToggle = { checked ->
                            onDraftChange { current ->
                                current.copy(
                                    lines = current.lines.map {
                                        if (it.id == line.id) it.copy(include = checked) else it
                                    },
                                )
                            }
                        },
                        onEdit = { editingLine = line },
                    )
                }
            }

            Surface(color = colors.background) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = draft.markBought,
                            onCheckedChange = { checked ->
                                onDraftChange { it.copy(markBought = checked) }
                            },
                            enabled = matchedCount > 0,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (matchedCount > 0) {
                                stringResource(R.string.scan_mark_bought_count, matchedCount)
                            } else {
                                stringResource(R.string.scan_mark_bought)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(enabled = draft.lines.any { it.include }, onClick = onSave),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.scan_save),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }

    editingLine?.let { line ->
        EditLineDialog(
            line = line,
            currency = draft.currency,
            onDismiss = { editingLine = null },
            onConfirm = { updated ->
                onDraftChange { current ->
                    current.copy(lines = current.lines.map { if (it.id == updated.id) updated else it })
                }
                editingLine = null
            },
        )
    }

    if (pickingDate) {
        val initialState = rememberDatePickerState(initialSelectedDateMillis = draft.purchasedAt)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        initialState.selectedDateMillis?.let { millis ->
                            onDraftChange { it.copy(purchasedAt = startOfLocalDay(millis)) }
                        }
                        pickingDate = false
                    },
                ) {
                    Text(stringResource(R.string.scan_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) {
                    Text(stringResource(R.string.scan_cancel))
                }
            },
        ) {
            DatePicker(state = initialState)
        }
    }
}

@Composable
private fun ReceiptPhotoThumb(path: String?) {
    val bitmap = remember(path) {
        path?.let { filePath ->
            runCatching {
                android.graphics.BitmapFactory.decodeFile(filePath)
                    ?.let { original ->
                        val maxSide = 360
                        val scale = maxSide.toFloat() / maxOf(original.width, original.height).coerceAtLeast(1)
                        if (scale < 1f) {
                            android.graphics.Bitmap.createScaledBitmap(
                                original,
                                (original.width * scale).toInt().coerceAtLeast(1),
                                (original.height * scale).toInt().coerceAtLeast(1),
                                true,
                            )
                        } else {
                            original
                        }
                    }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(width = 92.dp, height = 128.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DraftLineRow(
    line: DraftLine,
    currency: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = line.include, onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (line.include) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        line.matchedFromBasket ->
                            stringResource(R.string.scan_matched_basket, line.matchedName.orEmpty())

                        line.matchedName != null -> stringResource(R.string.scan_matched, line.matchedName)
                        else -> stringResource(R.string.scan_no_match)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (line.matchedName != null) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney(line.lineTotal, currency),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                if (line.quantity > 1.0) {
                    Text(
                        text = "×${formatPlain(line.quantity)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditLineDialog(
    line: DraftLine,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (DraftLine) -> Unit,
) {
    var name by remember { mutableStateOf(line.name) }
    var quantity by remember { mutableStateOf(formatPlain(line.quantity)) }
    var price by remember { mutableStateOf(formatPlain(line.unitPrice)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_edit_line)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.scan_name)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text(stringResource(R.string.scan_quantity)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text(stringResource(R.string.scan_unit_price) + " ($currency)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = quantity.replace(',', '.').toDoubleOrNull() ?: 1.0
                    val unit = price.replace(',', '.').toDoubleOrNull() ?: line.unitPrice
                    onConfirm(
                        line.copy(
                            name = name.trim(),
                            quantity = qty.coerceAtLeast(1.0),
                            unitPrice = unit,
                            lineTotal = (qty * unit).roundMoney(),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun formatPlain(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.UK, "%.2f", value)

private fun Double.roundMoney(): Double = kotlin.math.round(this * 100) / 100

private fun formatDraftDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "d MMM yyyy")))

private fun startOfLocalDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Looks like an outlined text field but opens a picker instead of a keyboard. */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
