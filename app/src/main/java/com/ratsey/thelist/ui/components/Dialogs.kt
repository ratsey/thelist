package com.ratsey.thelist.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.BuildConfig
import com.ratsey.thelist.R
import com.ratsey.thelist.data.Item
import com.ratsey.thelist.data.Store

/** Long-press / tap menu: the calm home for rename and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionsSheet(
    item: Item,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onPlacement: () -> Unit,
    onPutAway: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            SheetAction(
                icon = if (item.completedAt == null) Icons.Rounded.Check else Icons.Rounded.Add,
                label = stringResource(
                    if (item.completedAt == null) R.string.action_move_to_basket else R.string.action_add_back,
                ),
                onClick = onToggle,
            )
            SheetAction(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.action_rename),
                onClick = onRename,
            )
            SheetAction(
                icon = Icons.Rounded.Place,
                label = stringResource(R.string.action_placement),
                onClick = onPlacement,
            )
            SheetAction(
                icon = Icons.Rounded.Archive,
                label = stringResource(R.string.action_put_away),
                onClick = onPutAway,
            )
            SheetAction(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.action_delete),
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}

/**
 * A shelf row's menu. It exists because nothing deletes from a swipe or a tap: the only way to
 * remove a name for good is to open this deliberately and choose Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfActionsSheet(
    item: Item,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onPlacement: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            SheetAction(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.action_add_back),
                onClick = onAdd,
            )
            SheetAction(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.action_rename),
                onClick = onRename,
            )
            SheetAction(
                icon = Icons.Rounded.Place,
                label = stringResource(R.string.action_placement),
                onClick = onPlacement,
            )
            SheetAction(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.action_delete),
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
fun RenameDialog(
    item: Item,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(item.name) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (text.isNotBlank()) onConfirm(text) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The only user choice so far: whether a scanned receipt clears the list as it saves. */
@Composable
fun SettingsDialog(
    scanMovesItems: Boolean,
    onScanMovesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_scan_moves),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_scan_moves_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = scanMovesItems, onCheckedChange = onScanMovesChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
fun AboutDialog(version: String, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { runCatching { uriHandler.openUri(BuildConfig.RELEASES_URL) } },
            ) {
                Text(stringResource(R.string.about_view_releases))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * What the shopper knows about an item: which shop and aisle it lives in, how much to buy and what
 * it should cost. Every field is optional — a corner shop has no aisles, a list in the kitchen has
 * no amounts yet, and "the freezer" is as useful as "A6".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementDialog(
    item: Item,
    stores: List<Store>,
    onDismiss: () -> Unit,
    onSave: (
        storeId: String?,
        newStoreName: String?,
        location: String,
        amount: String?,
        price: Double?,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedStoreId by remember { mutableStateOf(item.storeId) }
    var location by remember { mutableStateOf(item.location.orEmpty()) }
    var amount by remember { mutableStateOf(item.amount.orEmpty()) }
    var price by remember { mutableStateOf(item.price?.let(::formatPriceField).orEmpty()) }
    var addingStore by remember { mutableStateOf(false) }
    var newStoreName by remember { mutableStateOf("") }

    val priceValue = price.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    // A price typed wrong is a price silently lost, so it has to be a number or nothing at all.
    val priceValid = price.isBlank() || priceValue != null

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
                text = stringResource(R.string.placement_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.placement_store),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !addingStore && selectedStoreId == null,
                    onClick = {
                        addingStore = false
                        selectedStoreId = null
                    },
                    label = { Text(stringResource(R.string.placement_no_store)) },
                )
                stores.forEach { store ->
                    FilterChip(
                        selected = !addingStore && selectedStoreId == store.id,
                        onClick = {
                            addingStore = false
                            selectedStoreId = store.id
                        },
                        label = { Text(store.name) },
                    )
                }
                FilterChip(
                    selected = addingStore,
                    onClick = {
                        addingStore = true
                        selectedStoreId = null
                    },
                    label = { Text(stringResource(R.string.placement_new_store)) },
                )
            }
            if (addingStore) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newStoreName,
                    onValueChange = { newStoreName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.placement_new_store_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                singleLine = true,
                label = { Text(stringResource(R.string.placement_location)) },
                placeholder = { Text(stringResource(R.string.placement_location_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.placement_amount)) },
                    placeholder = { Text(stringResource(R.string.placement_amount_hint)) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    singleLine = true,
                    isError = !priceValid,
                    label = { Text(stringResource(R.string.placement_price)) },
                    placeholder = { Text(stringResource(R.string.placement_price_hint)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(
                    onClick = {
                        onSave(
                            if (addingStore) null else selectedStoreId,
                            newStoreName.takeIf { addingStore },
                            location,
                            amount.trim().takeIf { it.isNotEmpty() },
                            priceValue,
                        )
                    },
                    enabled = !(addingStore && newStoreName.isBlank()) && priceValid,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/** The shops themselves. Add one here, or straight from the placement sheet while shopping. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StoresSheet(
    stores: List<Store>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (Store) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Store?>(null) }

    val submit: () -> Unit = {
        if (name.isNotBlank()) {
            onAdd(name)
            name = ""
        }
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
                text = stringResource(R.string.stores_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.stores_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.stores_add_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = submit, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.action_save))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (stores.isEmpty()) {
                Text(
                    text = stringResource(R.string.stores_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    stores.forEach { store ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Nothing here deletes on a tap: hold the row instead.
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { pendingDelete = store },
                                )
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = store.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.stores_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    pendingDelete?.let { store ->
        ConfirmDialog(
            title = stringResource(R.string.stores_delete_title, store.name),
            body = stringResource(R.string.stores_delete_body),
            confirmLabel = stringResource(R.string.stores_delete_confirm),
            onConfirm = {
                onDelete(store)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Prices are edited as typed: 3.5 rather than 3.50, and a whole number stays whole. */
private fun formatPriceField(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
