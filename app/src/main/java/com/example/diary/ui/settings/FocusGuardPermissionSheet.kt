package com.example.diary.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.diary.R
import com.example.diary.data.focus.BackgroundStart
import com.example.diary.data.focus.FocusGuardService
import com.example.diary.ui.theme.Spacing

private enum class GuardPermission { ACCESSIBILITY, BACKGROUND_POPUP, NOTIFICATIONS }

/**
 * 打开「专注时锁定其他应用」前的权限清单：必需项没齐就不让继续，
 * 每项各带一个跳转按钮；从系统设置跳回来会重新检测。
 */
@Composable
internal fun FocusGuardPermissionSheet(
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var accessibilityGranted by remember { mutableStateOf(FocusGuardService.isEnabled(context)) }
    var backgroundBlocked by remember {
        mutableStateOf(!BackgroundStart.canStartActivityFromBackground(context))
    }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityGranted = FocusGuardService.isEnabled(context)
                backgroundBlocked = !BackgroundStart.canStartActivityFromBackground(context)
                notificationsGranted = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rows = buildList {
        add(
            PermissionRowState(
                permission = GuardPermission.ACCESSIBILITY,
                granted = accessibilityGranted,
                required = true,
                title = stringResource(R.string.settings_focus_guard_perm_a11y),
                reason = stringResource(R.string.settings_focus_guard_perm_a11y_reason)
            )
        )
        if (backgroundBlocked) {
            add(
                PermissionRowState(
                    permission = GuardPermission.BACKGROUND_POPUP,
                    granted = false,
                    required = true,
                    title = stringResource(R.string.settings_focus_guard_perm_background),
                    reason = stringResource(R.string.settings_focus_guard_perm_background_reason)
                )
            )
        }
        if (!notificationsGranted) {
            add(
                PermissionRowState(
                    permission = GuardPermission.NOTIFICATIONS,
                    granted = false,
                    required = false,
                    title = stringResource(R.string.settings_focus_guard_perm_notify),
                    reason = stringResource(R.string.settings_focus_guard_perm_notify_reason)
                )
            )
        }
    }

    val missingRequired = rows.any { it.required && !it.granted }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_focus_guard_perms_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(
                    stringResource(R.string.settings_focus_guard_perms_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rows.forEach { row ->
                    PermissionRow(
                        row = row,
                        onGrant = {
                            when (row.permission) {
                                GuardPermission.ACCESSIBILITY -> runCatching {
                                    context.startActivity(FocusGuardService.accessibilitySettingsIntent(context))
                                }

                                GuardPermission.BACKGROUND_POPUP -> runCatching {
                                    context.startActivity(BackgroundStart.permissionEditorIntent(context))
                                }

                                GuardPermission.NOTIFICATIONS -> runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !missingRequired,
                onClick = onContinue
            ) {
                Text(stringResource(R.string.settings_focus_guard_perms_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private data class PermissionRowState(
    val permission: GuardPermission,
    val granted: Boolean,
    val required: Boolean,
    val title: String,
    val reason: String
)

@Composable
private fun PermissionRow(row: PermissionRowState, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when {
                        row.granted -> stringResource(R.string.settings_focus_guard_perm_granted)
                        row.required -> stringResource(R.string.settings_focus_guard_perm_required)
                        else -> stringResource(R.string.settings_focus_guard_perm_optional)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(start = Spacing.s)
                )
            }
            if (!row.granted) {
                Text(
                    row.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!row.granted) {
            TextButton(onClick = onGrant, modifier = Modifier.padding(start = 4.dp)) {
                Text(stringResource(R.string.settings_focus_guard_perm_enable))
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}