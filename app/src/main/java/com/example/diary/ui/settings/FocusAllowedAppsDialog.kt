package com.example.diary.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.example.diary.R
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SelectableApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

/**
 * 列出可勾选的应用：按「系统 / 已安装」分标签 + 搜索 + 每行真实应用图标。
 *
 * 枚举方式特意不用 queryIntentActivities(MAIN/LAUNCHER)：实测在 HyperOS 上它只回 31 条，
 * 而系统实际有 130 个可启动应用——很多应用（含「文件管理」com.miui.huanji）的桌面入口是
 * activity-alias，隐式查询漏掉它们。改成遍历已安装包 + getLaunchIntentForPackage 逐个判定，
 * 两条路径互为校验，取并集最稳。QUERY_ALL_PACKAGES 保证 getInstalledPackages 能拿到全量。
 */
internal suspend fun loadSelectableApps(context: Context): List<SelectableApp> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val found = LinkedHashMap<String, ApplicationInfo>()

        runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_DEFAULT_ONLY
            )
        }.getOrDefault(emptyList()).forEach { info ->
            val pkg = info.activityInfo?.packageName ?: return@forEach
            runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()?.let { found.putIfAbsent(pkg, it) }
        }

        val installed = runCatching {
            pm.getInstalledApplications(0)
        }.getOrDefault(emptyList())
        installed.forEach { appInfo ->
            val pkg = appInfo.packageName
            if (pkg == context.packageName || found.containsKey(pkg)) return@forEach
            val launchable = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() != null
            if (launchable) found[pkg] = appInfo
        }
        val installedPkgs = runCatching {
            pm.getInstalledPackages(0)
        }.getOrDefault(emptyList())
        installedPkgs.forEach { pkgInfo ->
            val pkg = pkgInfo.packageName
            if (pkg == context.packageName || found.containsKey(pkg)) return@forEach
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@forEach
            val launchable = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() != null
            if (launchable) found[pkg] = appInfo
        }

        found
            .filterKeys { it != context.packageName }
            .map { (pkg, appInfo) ->
                SelectableApp(
                    packageName = pkg,
                    label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg),
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
            .also {
                Log.d(
                    "FocusAllowedApps",
                    "applications=${installed.size} packages=${installedPkgs.size} " +
                        "listed=${it.size} system=${it.count { a -> a.isSystem }}"
                )
            }
    }

/** 图标按行懒加载：一次把上百个应用图标全读出来既慢又占内存 */
@Composable
private fun rememberAppIcon(context: Context, packageName: String) = produceState<Drawable?>(
    initialValue = null,
    key1 = packageName
) {
    value = withContext(Dispatchers.IO) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }
}

@Composable
internal fun FocusAllowedAppsDialog(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps by produceState<List<SelectableApp>?>(initialValue = null) {
        value = runCatching { loadSelectableApps(context) }.getOrDefault(emptyList())
    }
    var systemTab by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }

    val visible = remember(apps, systemTab, query) {
        val keyword = query.trim().lowercase()
        apps.orEmpty()
            .filter { it.isSystem == systemTab }
            .filter { keyword.isEmpty() || it.label.lowercase().contains(keyword) || it.packageName.contains(keyword) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchVisible) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.focus_allowed_search_hint)) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        stringResource(R.string.focus_allowed_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = { searchVisible = !searchVisible }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.focus_allowed_search))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
            }

            TabRow(selectedTabIndex = if (systemTab) 0 else 1) {
                Tab(
                    selected = systemTab,
                    onClick = { systemTab = true },
                    text = { Text(stringResource(R.string.focus_allowed_tab_system)) }
                )
                Tab(
                    selected = !systemTab,
                    onClick = { systemTab = false },
                    text = { Text(stringResource(R.string.focus_allowed_tab_installed)) }
                )
            }

            Text(
                stringResource(R.string.focus_allowed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)
            )

            when {
                apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.focus_allowed_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(Modifier.weight(1f)) {
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in selected,
                            onToggle = { onToggle(app.packageName) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 64.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Spacing.l),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.focus_allowed_done, selected.size))
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: SelectableApp, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val icon by rememberAppIcon(context, app.packageName)
    val bitmap = remember(app.packageName, icon) {
        runCatching { icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        app.label.take(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.l)
        )
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}