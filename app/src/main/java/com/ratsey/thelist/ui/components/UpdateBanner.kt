package com.ratsey.thelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratsey.thelist.R
import com.ratsey.thelist.ui.UpdateUi

/**
 * Contextual update card. It only appears when there is something to say or do, so the
 * shopping flow is never interrupted by a modal.
 */
@Composable
fun UpdateBanner(
    state: UpdateUi,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = state !is UpdateUi.Hidden,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when (state) {
                                is UpdateUi.ReadyToInstall -> Icons.Rounded.CheckCircle
                                is UpdateUi.NeedsPermission -> Icons.Rounded.Info
                                else -> Icons.Rounded.Refresh
                            },
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = titleFor(state),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onPrimaryContainer,
                            )
                            Text(
                                text = bodyFor(state),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onPrimaryContainer.copy(alpha = 0.72f),
                            )
                        }

                        val action = actionFor(state, onDownload, onInstall, onAllowInstall)
                        if (action != null) {
                            TextButton(onClick = action.second) {
                                Text(action.first)
                            }
                        }

                        if (state is UpdateUi.Available || state is UpdateUi.Failed) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.update_action_dismiss),
                                    tint = colors.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    if (state is UpdateUi.Downloading) {
                        Column(Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { state.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = colors.primary,
                                trackColor = colors.primary.copy(alpha = 0.15f),
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun titleFor(state: UpdateUi): String = when (state) {
    UpdateUi.Hidden -> ""
    UpdateUi.Checking -> stringResource(R.string.update_checking_title)
    is UpdateUi.Available -> stringResource(R.string.update_available_title)
    is UpdateUi.Downloading -> stringResource(R.string.update_downloading_title, state.version)
    is UpdateUi.ReadyToInstall -> stringResource(R.string.update_ready_title)
    is UpdateUi.NeedsPermission -> stringResource(R.string.update_permission_title)
    is UpdateUi.Failed -> stringResource(R.string.update_failed_title)
}

@Composable
private fun bodyFor(state: UpdateUi): String = when (state) {
    UpdateUi.Hidden -> ""
    UpdateUi.Checking -> stringResource(R.string.update_checking_body)
    is UpdateUi.Available -> stringResource(R.string.update_available_body, state.version)
    is UpdateUi.Downloading -> stringResource(R.string.update_downloading_body)
    is UpdateUi.ReadyToInstall -> stringResource(R.string.update_ready_body, state.version)
    is UpdateUi.NeedsPermission -> stringResource(R.string.update_permission_body)
    is UpdateUi.Failed -> stringResource(state.messageRes)
}

@Composable
private fun actionFor(
    state: UpdateUi,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstall: () -> Unit,
): Pair<String, () -> Unit>? = when (state) {
    is UpdateUi.Available -> stringResource(R.string.update_action_download) to onDownload
    is UpdateUi.ReadyToInstall -> stringResource(R.string.update_action_install) to onInstall
    is UpdateUi.NeedsPermission -> stringResource(R.string.update_action_allow) to onAllowInstall
    else -> null
}
