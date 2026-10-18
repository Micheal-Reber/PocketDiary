package com.example.diary.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.R
import com.example.diary.data.preferences.AppLockPreferences
import com.example.diary.ui.lock.AppLockScreen
import com.example.diary.ui.lock.LockMode
import com.example.diary.ui.navigation.BottomBarContentInset
import kotlinx.coroutines.launch

@Composable
fun PrivacySettingsScreen(
    appLockPreferences: AppLockPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val lockEnabled by appLockPreferences.lockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val hasLockPassword by appLockPreferences.hasPassword.collectAsStateWithLifecycle(initialValue = false)
    val lockMode by appLockPreferences.lockMode.collectAsStateWithLifecycle(initialValue = AppLockPreferences.MODE_EVERY_APP)
    val lockPinLength by appLockPreferences.pinLength.collectAsStateWithLifecycle(initialValue = 4)

    // 全屏设置密码页（开启时若无密码 / 修改密码）
    var showLockSetup by remember { mutableStateOf(false) }
    // 关闭密码锁前的全屏验证
    var showLockVerify by remember { mutableStateOf(false) }
    var showLockModeDialog by remember { mutableStateOf(false) }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_section_privacy),
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroupCard {
                SettingsRow(
                    headline = stringResource(R.string.settings_lock),
                    supporting = stringResource(R.string.settings_lock_desc),
                    leading = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Switch(
                            checked = lockEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !hasLockPassword) {
                                    showLockSetup = true
                                } else if (!checked && hasLockPassword && lockEnabled) {
                                    showLockVerify = true
                                } else {
                                    scope.launch { appLockPreferences.setLockEnabled(checked) }
                                }
                            }
                        )
                    }
                )

                if (lockEnabled && hasLockPassword) {
                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_change_pin),
                        supporting = stringResource(R.string.settings_change_pin_desc),
                        leading = {
                            Icon(
                                Icons.Default.Password,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { showLockSetup = true }
                    )

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_lock_mode),
                        supporting = when (lockMode) {
                            AppLockPreferences.MODE_EVERY_DIARY -> stringResource(R.string.settings_lock_mode_every_diary)
                            AppLockPreferences.MODE_DAILY -> stringResource(R.string.settings_lock_mode_daily)
                            else -> stringResource(R.string.settings_lock_mode_every_app)
                        },
                        leading = {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { showLockModeDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(BottomBarContentInset))
        }
    }

    // 验证时机选择
    if (showLockModeDialog) {
        AlertDialog(
            onDismissRequest = { showLockModeDialog = false },
            title = { Text(stringResource(R.string.settings_lock_mode)) },
            text = {
                Column {
                    listOf(
                        AppLockPreferences.MODE_EVERY_APP to R.string.settings_lock_mode_every_app_full,
                        AppLockPreferences.MODE_EVERY_DIARY to R.string.settings_lock_mode_every_diary,
                        AppLockPreferences.MODE_DAILY to R.string.settings_lock_mode_daily
                    ).forEach { (mode, descRes) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { appLockPreferences.setLockMode(mode) }
                                    showLockModeDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = lockMode == mode,
                                onClick = {
                                    scope.launch { appLockPreferences.setLockMode(mode) }
                                    showLockModeDialog = false
                                }
                            )
                            Text(stringResource(descRes), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLockModeDialog = false }) { Text(stringResource(R.string.common_done)) }
            }
        )
    }

    // 全屏设置/修改密码（玻璃数字键盘）：首设 SetPin，修改 Change（先验旧密码）
    if (showLockSetup) {
        AppLockScreen(
            mode = if (hasLockPassword) LockMode.Change else LockMode.SetPin,
            lockPreferences = appLockPreferences,
            title = if (hasLockPassword) stringResource(R.string.settings_change_pin) else stringResource(R.string.settings_set_pin),
            pinLength = lockPinLength,
            onVerified = {},
            onPinSet = { pin ->
                scope.launch {
                    appLockPreferences.setPassword(pin)
                    if (!lockEnabled) appLockPreferences.setLockEnabled(true)
                }
                showLockSetup = false
                showToast(
                    context,
                    if (hasLockPassword) context.getString(R.string.settings_pin_changed)
                    else context.getString(R.string.settings_lock_enabled)
                )
            },
            onCancel = { showLockSetup = false }
        )
    }

    // 关闭密码锁前先验证（防绕过日记锁直接进设置关锁）
    if (showLockVerify) {
        AppLockScreen(
            mode = LockMode.Unlock,
            lockPreferences = appLockPreferences,
            title = stringResource(R.string.settings_lock_verify_title),
            pinLength = lockPinLength,
            onVerified = {
                scope.launch { appLockPreferences.setLockEnabled(false) }
                showLockVerify = false
                showToast(context, context.getString(R.string.settings_lock_disabled))
            },
            onPinSet = {},
            onCancel = { showLockVerify = false }
        )
    }
}