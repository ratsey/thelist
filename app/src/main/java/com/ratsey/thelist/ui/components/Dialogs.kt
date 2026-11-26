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
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.ratsey.thelist.data.PricePrompt
import com.ratsey.thelist.data.ShelfTag
import com.ratsey.thelist.data.Store
import com.ratsey.thelist.data.formatPriceField
import com.ratsey.thelist.data.MeasureSystem
import com.ratsey.thelist.data.comparisonUnits
import com.ratsey.thelist.data.parsePriceField
import com.ratsey.thelist.data.perFieldHint
import com.ratsey.thelist.ui.LocalCurrency
import com.ratsey.thelist.data.priceWithUnit
import com.ratsey.thelist.ui.formatMoney
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Surface
import com.ratsey.thelist.data.PriceBasis
import com.ratsey.thelist.data.composePerField
import com.ratsey.thelist.data.parsePriceBasis
import com.ratsey.thelist.data.priceIn
import com.ratsey.thelist.data.splitPerField
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import com.ratsey.thelist.data.unitPriceOf
import com.ratsey.thelist.data.unitGroups
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import com.ratsey.thelist.data.ItemNames
import com.ratsey.thelist.ui.VariantPrompt

/** Long-press / tap menu: the calm home for rename and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionsSheet(
    item: Item,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onPlacement: () -> Unit,
    onOpenPrices: () -> Unit,
    onCompare: () -> Unit,
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
                label = stringResource(R.string.action_name_and_type),
                onClick = onRename,
            )
            SheetAction(
                icon = Icons.Rounded.Place,
                label = stringResource(R.string.action_placement),
                onClick = onPlacement,
            )
            // In the basket the long press now sets a price, so the history needs a door of its own.
            SheetAction(
                icon = Icons.Rounded.ReceiptLong,
                label = stringResource(R.string.action_prices),
                onClick = onOpenPrices,
            )
            // The icon on the row is quick; this is the way in you can find by reading.
            SheetAction(
                icon = Icons.Rounded.CompareArrows,
                label = stringResource(R.string.action_compare_prices),
                onClick = onCompare,
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
                label = stringResource(R.string.action_name_and_type),
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
    onConfirm: (name: String, variant: String) -> Unit,
) {
    // The name and the type are the same question — which thing is this? — so they are asked
    // together and saved in one write, wherever the row lives: shelf, list or basket.
    var text by remember { mutableStateOf(item.name) }
    var variant by remember { mutableStateOf(item.variant.orEmpty()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_name_and_type)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = variant,
                    onValueChange = { variant = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rename_variant)) },
                    placeholder = { Text(stringResource(R.string.rename_variant_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (text.isNotBlank()) onConfirm(text, variant) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text, variant) },
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

/** The one thing worth choosing: when the app interrupts to ask what you paid. */
@Composable
fun SettingsDialog(
    pricePrompt: PricePrompt,
    onPricePromptChange: (PricePrompt) -> Unit,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    measureSystem: MeasureSystem,
    onMeasureSystemChange: (MeasureSystem) -> Unit,
    onDismiss: () -> Unit,
) {
    // Typed locally so clearing the field does not fight the stored value: a blank field simply
    // keeps the last currency that was set.
    var draftCurrency by remember { mutableStateOf(currency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_price_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_price_prompt_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PricePrompt.entries.forEach { option ->
                        FilterChip(
                            selected = option == pricePrompt,
                            onClick = { onPricePromptChange(option) },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            PricePrompt.Off -> R.string.price_prompt_off
                                            PricePrompt.WhenAdding -> R.string.price_prompt_when_adding
                                            PricePrompt.AtCheckout -> R.string.price_prompt_at_checkout
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.settings_region),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_region_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draftCurrency,
                    onValueChange = { raw ->
                        val clean = raw.trim().take(3)
                        draftCurrency = clean
                        if (clean.isNotEmpty()) onCurrencyChange(clean)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_currency)) },
                    placeholder = { Text("$") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeasureSystem.entries.forEach { system ->
                        FilterChip(
                            selected = system == measureSystem,
                            onClick = { onMeasureSystemChange(system) },
                            label = {
                                Text(
                                    stringResource(
                                        when (system) {
                                            MeasureSystem.Metric -> R.string.measure_metric
                                            MeasureSystem.Imperial -> R.string.measure_imperial
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = comparisonUnits(measureSystem).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
/**
 * Everything the shopper knows about a row, asked as two questions instead of five equal fields:
 * **where** it lives (shop and aisle) and what it **costs** (how much, and the rate).
 *
 * The price states what it buys in the same card: "$3.00" beside "$1.00/lb", so the conversion is
 * visible while typing rather than discovered afterwards, and the unit it is for is a chip you tap
 * rather than a word you have to spell the way the app expects. Every field is optional: a corner shop has no aisles, a list
 * in the kitchen has no amounts yet, and Clear puts the row back to saying nothing at all.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlacementDialog(
    item: Item,
    stores: List<Store>,
    measureSystem: MeasureSystem,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        storeId: String?,
        newStoreName: String?,
        variant: String,
        location: String,
        amount: String?,
        price: Double?,
        priceUnit: String?,
    ) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(item.name) }
    var selectedStoreId by remember { mutableStateOf(item.storeId) }
    var location by remember { mutableStateOf(item.location.orEmpty()) }
    var amount by remember { mutableStateOf(item.amount.orEmpty()) }
    var price by remember { mutableStateOf(formatPriceField(item.price)) }
    // The unit is picked, not spelt: the chips are the units the app can actually convert.
    val storedPer = remember { splitPerField(item.priceUnit) }
    var packQty by remember { mutableStateOf(storedPer.first) }
    var unitText by remember { mutableStateOf(storedPer.second) }
    var customUnit by remember {
        mutableStateOf(
            storedPer.second.isNotEmpty() && storedPer.second !in comparisonUnits(measureSystem),
        )
    }
    var variant by remember { mutableStateOf(item.variant.orEmpty()) }
    var addingStore by remember { mutableStateOf(false) }
    var newStoreName by remember { mutableStateOf("") }

    val priceUnit = composePerField(packQty, unitText)
    val priceValue = parsePriceField(price)
    val priceValid = price.isBlank() || priceValue != null

    // What it comes to for one unit: the number two offers can actually be held against each other
    // in. A $3 bag of three pounds is $1 a pound, said while you type.
    val unitPrice = remember(priceValue, priceUnit) { unitPriceOf(priceValue, priceUnit) }

    // Clear only means something if the row is currently saying something.
    val hasStored = item.storeId != null || !item.location.isNullOrBlank() ||
        !item.variant.isNullOrBlank() || !item.amount.isNullOrBlank() || item.price != null ||
        !item.priceUnit.isNullOrBlank()

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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            EyebrowText(stringResource(R.string.placement_cost))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
            )
            // Which version of the thing, edited where it is read: italic, like the row's own type.
            BasicTextField(
                value = variant,
                onValueChange = { variant = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                decorationBox = { inner ->
                    Box {
                        if (variant.isBlank()) {
                            Text(
                                text = stringResource(R.string.placement_variant_hint),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontStyle = FontStyle.Italic,
                                ),
                                color = colors.outline,
                            )
                        }
                        inner()
                    }
                },
            )

            Spacer(Modifier.height(20.dp))

            // The cost card: the figure is the loudest thing on the screen, and the unit price is
            // never far from it.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    // A folding phone's cover screen leaves this card about 320dp, where a 45sp
                    // figure and its rate cannot share a line without one of them clipping. Below
                    // that width the figure steps down a size and the rate takes its own line.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val narrow = maxWidth < 300.dp
                        val heroStyle = if (narrow) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.displayMedium
                        }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = LocalCurrency.current,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                                BasicTextField(
                                    value = price,
                                    onValueChange = { price = it },
                                    textStyle = heroStyle.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (priceValid) colors.onSurface else colors.error,
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done,
                                    ),
                                    cursorBrush = SolidColor(
                                        if (priceValid) colors.primary else colors.error,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                if (!narrow) {
                                    unitPrice?.let { one ->
                                        UnitPriceBlock(
                                            value = priceWithUnit(
                                                formatMoney(one.per, LocalCurrency.current),
                                                one.label,
                                            ),
                                            alignEnd = true,
                                        )
                                    }
                                }
                            }
                            if (narrow) {
                                unitPrice?.let { one ->
                                    Spacer(Modifier.height(6.dp))
                                    UnitPriceBlock(
                                        value = priceWithUnit(
                                            formatMoney(one.per, LocalCurrency.current),
                                            one.label,
                                        ),
                                        alignEnd = false,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
                        color = colors.outlineVariant.copy(alpha = 0.5f),
                    )

                    // How much the price covers, and of what.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.placement_for),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = packQty,
                            onValueChange = { typed ->
                                packQty = typed.filter { char -> char.isDigit() || char == '.' }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colors.onSurface,
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            cursorBrush = SolidColor(colors.primary),
                            modifier = Modifier.width(48.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = unitText.ifBlank { stringResource(R.string.placement_per) },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (unitText.isBlank()) colors.outline else colors.onSurface,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Both systems, grouped, scrolling sideways: a vertical scroller inside a
                    // sheet fights the sheet's own drag-to-dismiss, and a scroll meant to look
                    // further down the units should never close the thing you are typing into.
                    Column(Modifier.fillMaxWidth()) {
                        unitGroups(measureSystem).forEach { group ->
                            EyebrowText(groupTitle(group.key))
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                group.units.forEach { unit ->
                                    PillChip(
                                        selected = !customUnit &&
                                            unitText.equals(unit, ignoreCase = true),
                                        label = unit,
                                        onClick = {
                                            customUnit = false
                                            unitText = unit
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            PillChip(
                                selected = customUnit,
                                label = stringResource(R.string.placement_unit_other),
                                onClick = { customUnit = true },
                            )
                        }
                    }

                    if (customUnit) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            BasicTextField(
                                value = unitText,
                                onValueChange = { unitText = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = colors.onSurface,
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                cursorBrush = SolidColor(colors.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // The where: the shop you are standing in, then the two places on a shelf you note.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    EyebrowText(stringResource(R.string.placement_where))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PillChip(
                            selected = !addingStore && selectedStoreId == null,
                            label = stringResource(R.string.placement_no_store),
                            onClick = {
                                addingStore = false
                                selectedStoreId = null
                            },
                        )
                        stores.forEach { store ->
                            PillChip(
                                selected = !addingStore && selectedStoreId == store.id,
                                label = store.name,
                                onClick = {
                                    addingStore = false
                                    selectedStoreId = store.id
                                },
                            )
                        }
                        PillChip(
                            selected = addingStore,
                            label = stringResource(R.string.placement_new_store_chip),
                            onClick = {
                                addingStore = true
                                selectedStoreId = null
                            },
                        )
                    }
                    if (addingStore) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            BasicTextField(
                                value = newStoreName,
                                onValueChange = { newStoreName = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = colors.onSurface,
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                cursorBrush = SolidColor(colors.primary),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    Box {
                                        if (newStoreName.isBlank()) {
                                            Text(
                                                text = stringResource(R.string.placement_new_store_hint),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = colors.outline,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LabeledBox(
                        label = stringResource(R.string.placement_location),
                        value = location,
                        onValueChange = { location = it },
                        placeholder = stringResource(R.string.placement_location_hint),
                    )
                    Spacer(Modifier.height(10.dp))
                    LabeledBox(
                        label = stringResource(R.string.placement_amount),
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = stringResource(R.string.placement_amount_hint),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        onSave(
                            name,
                            if (addingStore) null else selectedStoreId,
                            newStoreName.takeIf { addingStore },
                            variant,
                            location,
                            amount.trim().takeIf { it.isNotEmpty() },
                            priceValue,
                            priceUnit.trim().takeIf { it.isNotEmpty() },
                        )
                    },
                    enabled = name.isNotBlank() && !(addingStore && newStoreName.isBlank()) && priceValid,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onSave(item.name, null, null, "", "", null, null, null) },
                    enabled = hasStored,
                ) {
                    Text(stringResource(R.string.placement_clear))
                }
            }
        }
    }
}

/** The derived rate, labelled and set in the accent: the reason the card has two numbers. */
@Composable
private fun UnitPriceBlock(value: String, alignEnd: Boolean) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        EyebrowText(stringResource(R.string.placement_unit_price))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What a group of units measures, said the way the sheet says things. */
@Composable
private fun groupTitle(key: String): String = stringResource(
    when (key) {
        "weight" -> R.string.unit_group_weight
        "volume" -> R.string.unit_group_volume
        "length" -> R.string.unit_group_length
        else -> R.string.unit_group_each
    },
)

/** A section's name, set small and wide so it reads as a label rather than a heading. */
@Composable
private fun EyebrowText(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A one-tap choice: filled in the accent when chosen, a quiet tone when not. */
@Composable
private fun PillChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A labelled box for a short piece of text: the label above, the field filling it. */
@Composable
private fun LabeledBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            EyebrowText(label)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.outline,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** The two questions this sheet asks, quieter than the fields under them. */
@Composable
private fun DialogLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

/**
 * The "what did it cost?" prompt, shown as something goes into the basket.
 *
 * Both answers are asked at once because both drift while you shop: the figure on the shelf edge
 * changes from week to week, and the thing gets moved to a different aisle. What is already stored
 * is filled in, so a row that is still right is confirmed with one tap on Save. Skipping stays a
 * single tap and leaves the row exactly as it was.
 */
@Composable
fun PricePromptDialog(
    item: Item,
    /** Every shop, so one can be picked when the header is not saying where you are. */
    stores: List<Store>,
    /** The shop picked in the header: the one this price is being typed in. */
    currentStoreId: String?,
    /** Identifies this request, so each ask starts from what is stored at that moment. */
    promptToken: Long,
    /** What the camera read off the shelf label, if it has read one for this ask. */
    scanned: ShelfTag?,
    onScanTag: () -> Unit,
    onSave: (price: Double, location: String, pack: String?, storeId: String?) -> Unit,
    onSkip: () -> Unit,
) {
    // Seeded from the row, or from the tag just read, rather than left blank: the question is "is
    // this still right?", and a figure carried over from the last thing priced would be a wrong
    // answer waiting to be tapped.
    var price by remember(promptToken, scanned) {
        mutableStateOf(formatPriceField(scanned?.price ?: item.price))
    }
    var location by remember(promptToken) { mutableStateOf(item.location.orEmpty()) }
    // Where you are now wins over where the row was filed: the figure being typed is the shelf in
    // front of you, and the row should say so afterwards. With no shop picked anywhere there is
    // nothing to default to, and the prompt offers the shops instead of guessing.
    var storeId by remember(promptToken) { mutableStateOf(currentStoreId ?: item.storeId) }
    val storeName = stores.firstOrNull { it.id == storeId }?.name
    val value = parsePriceField(price)
    // An aisle only means something inside a shop; without one it is a number that never shows.
    val askAisle = storeName != null
    val submit: () -> Unit = {
        value?.let { onSave(it, location, scanned?.pack, storeId) }
    }
    // What the label said besides the figure, so its own arithmetic can be seen beside ours.
    val tagRate = scanned?.rate
    val rateText = if (tagRate != null) {
        stringResource(
            R.string.tag_scan_rate,
            priceWithUnit(formatMoney(tagRate.per, LocalCurrency.current), tagRate.label),
        )
    } else {
        null
    }
    // Where the figure came from, so a number read off the shelf is checked rather than trusted.
    val caption = listOfNotNull(
        scanned?.let { stringResource(R.string.tag_scan_read) },
        scanned?.pack,
        rateText,
    ).joinToString(" · ")
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(promptToken) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.price_prompt_title)) },
        text = {
            Column {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                HeroNumberField(
                    value = price,
                    onValueChange = { price = it },
                    label = stringResource(R.string.placement_price),
                    currency = LocalCurrency.current,
                    isError = price.isNotBlank() && value == null,
                    imeAction = if (askAisle) ImeAction.Next else ImeAction.Done,
                    onDone = if (askAisle) null else submit,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (caption.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (askAisle) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(stringResource(R.string.price_prompt_aisle_at, storeName)) },
                        placeholder = { Text(stringResource(R.string.placement_location_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (stores.isNotEmpty()) {
                    // A price with no shop is a number nobody can compare later, so the cheapest
                    // thing to ask for is the shop itself — one tap, before the aisle even exists.
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.price_prompt_shop),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PillChip(
                            selected = storeId == null,
                            label = stringResource(R.string.placement_no_store),
                            onClick = { storeId = null },
                        )
                        stores.forEach { store ->
                            PillChip(
                                selected = storeId == store.id,
                                label = store.name,
                                onClick = { storeId = store.id },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onScanTag,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tag_scan_action))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = value != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.price_prompt_skip)) }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Where "Cancel" would be evasive, name the choice being declined. */
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel ?: stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Which version of the product the swipe meant.
 *
 * Asked at the gesture, because the shelf holds "Skimmed" and "Full Fat" as separate rows and
 * swiping one of them is a statement about the product, not about the version. Tapping a chip puts
 * that version on the list and closes the sheet: two taps, no detour through the details dialog.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VariantSheet(
    prompt: VariantPrompt,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            EyebrowText(stringResource(R.string.variant_prompt_title))
            Spacer(Modifier.height(6.dp))
            Text(
                text = prompt.item.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.variant_prompt_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            // Wrapping rather than a sideways strip: there are rarely more than three versions,
            // and one that falls off the edge is the one you needed.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                prompt.choices.forEach { choice ->
                    PillChip(
                        selected = ItemNames.key(choice) == ItemNames.key(prompt.item.variant.orEmpty()),
                        label = choice,
                        onClick = { onChoose(choice) },
                    )
                }
            }
        }
    }
}
