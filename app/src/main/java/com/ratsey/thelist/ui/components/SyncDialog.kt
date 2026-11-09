package com.ratsey.thelist.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratsey.thelist.R
import com.ratsey.thelist.ui.SyncState
import com.ratsey.thelist.ui.SyncUi

/**
 * Sharing setup. Kept in a dialog: it is a one-time decision, not a screen people visit.
 */
@Composable
fun SyncDialog(
    syncUi: SyncUi,
    onDismiss: () -> Unit,
    onCreate: (serverUrl: String, name: String) -> Unit,
    onJoin: (serverUrl: String, code: String, name: String) -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var serverUrl by rememberSaveable { mutableStateOf(syncUi.serverUrl) }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var joinMode by rememberSaveable { mutableStateOf(false) }

    val connected = syncUi.code != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (connected) {
                    Text(
                        text = stringResource(R.string.sync_connected, syncUi.serverUrl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.sync_code),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = syncUi.code.orEmpty(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onPrimaryContainer,
                            )
                        }
                    }
                    Text(
                        text = statusLine(syncUi),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (syncUi.state == SyncState.Offline) colors.error else colors.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { shareCode(context, syncUi.code.orEmpty()) }) {
                            Text(stringResource(R.string.sync_share))
                        }
                        TextButton(
                            onClick = onSyncNow,
                            enabled = syncUi.state != SyncState.Syncing,
                        ) {
                            Text(stringResource(R.string.sync_now))
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.sync_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.sync_server)) },
                        placeholder = { Text(stringResource(R.string.sync_server_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.sync_your_name)) },
                        placeholder = { Text(stringResource(R.string.sync_your_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (joinMode) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase().take(8) },
                            label = { Text(stringResource(R.string.sync_code)) },
                            placeholder = { Text(stringResource(R.string.sync_code_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.sync_join_warning),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                connected -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }

                joinMode -> TextButton(
                    onClick = { onJoin(serverUrl, code, name) },
                    enabled = serverUrl.isNotBlank() && code.length >= 6,
                ) {
                    Text(stringResource(R.string.sync_join))
                }

                else -> TextButton(
                    onClick = { onCreate(serverUrl, name) },
                    enabled = serverUrl.isNotBlank(),
                ) {
                    Text(stringResource(R.string.sync_create))
                }
            }
        },
        dismissButton = {
            if (connected) {
                TextButton(onClick = onDisconnect) {
                    Text(stringResource(R.string.sync_disconnect), color = colors.error)
                }
            } else {
                TextButton(onClick = { joinMode = !joinMode }) {
                    Text(
                        stringResource(
                            if (joinMode) R.string.sync_create else R.string.sync_join,
                        ),
                    )
                }
            }
        },
    )
}

@Composable
private fun statusLine(syncUi: SyncUi): String = when (syncUi.state) {
    SyncState.Syncing -> stringResource(R.string.sync_busy)
    SyncState.Offline -> stringResource(R.string.sync_offline)
    else -> if (syncUi.lastSyncedAt > 0L) {
        stringResource(R.string.sync_last_synced, relativeTime(syncUi.lastSyncedAt))
    } else {
        stringResource(R.string.sync_never)
    }
}

@Composable
private fun relativeTime(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}

private fun shareCode(context: Context, code: String) {
    if (code.isBlank()) return
    val text = context.getString(R.string.sync_share_text, code)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
