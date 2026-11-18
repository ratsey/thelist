package com.ratsey.thelist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R

/**
 * What a scan found, as a form to confirm: the name and the pack size the database offered,
 * both editable, because a crowd-sourced row is a suggestion rather than the truth.
 *
 * A code the database does not know opens the same form empty — the scan still saved the
 * trouble of deciding where the row goes, and a second tap of the scan icon is right there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannedProductSheet(
    initialName: String,
    initialAmount: String,
    /** The one-line "what this was" — brands, or the reason nothing was pre-filled. */
    detail: String,
    notFound: Boolean,
    onAdd: (name: String, amount: String?) -> Unit,
    onScanAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialName) }
    var amount by remember { mutableStateOf(initialAmount) }

    val add: () -> Unit = {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) onAdd(trimmed, amount.trim().takeIf { it.isNotEmpty() })
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
                text = stringResource(
                    if (notFound) R.string.scan_not_found_title else R.string.scan_found_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.scan_product_name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                singleLine = true,
                label = { Text(stringResource(R.string.scan_amount)) },
                placeholder = { Text(stringResource(R.string.scan_amount_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = add,
                    enabled = name.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                ) {
                    Text(
                        text = stringResource(R.string.scan_add),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
            if (notFound) {
                TextButton(onClick = onScanAgain) {
                    Text(stringResource(R.string.scan_again))
                }
            }
        }
    }
}
