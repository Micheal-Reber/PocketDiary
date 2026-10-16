package com.example.diary.ui.settings

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.example.diary.R
import com.example.diary.data.focus.FocusGuard

/** 可勾选的已安装应用清单；系统关键包（FocusGuard.DEFAULT_ALLOWED_PACKAGES）不列出来，它们始终放行 */
@Composable
internal fun FocusWhitelistDialog(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember { installedLaunchableApps(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_whitelist_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.focus_whitelist_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(apps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(app.packageName) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    app.label.take(1),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                                maxLines = 1
                            )
                            Checkbox(
                                checked = app.packageName in selected,
                                onCheckedChange = { onToggle(app.packageName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_done))
            }
        }
    )
}

private data class InstalledApp(val packageName: String, val label: String)

private fun installedLaunchableApps(context: android.content.Context): List<InstalledApp> {
    val packageManager: PackageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = runCatching {
        packageManager.queryIntentActivities(launcherIntent, 0)
    }.getOrElse { emptyList() }
    return resolved
        .mapNotNull { info ->
            val app: ApplicationInfo = info.activityInfo.applicationInfo
            val pkg = app.packageName
            if (pkg == context.packageName) return@mapNotNull null
            if (pkg in FocusGuard.DEFAULT_ALLOWED_PACKAGES) return@mapNotNull null
            InstalledApp(
                packageName = pkg,
                label = runCatching { packageManager.getApplicationLabel(app).toString() }.getOrDefault(pkg)
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}