package com.example.diary.data.focus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager

/**
 * 后台保活状态：电池优化白名单 + MIUI 自启。
 *
 * 番茄钟靠前台服务活着，HyperOS/MIUI 上不把这些开掉，进程会被回收、计时随之中断，
 * 所以引导用户去系统里打开（思路参考 ZenLock 的 BatteryOptimizationManager / MiuiUtils）。
 */
object KeepAlive {

    /** 是否已忽略电池优化（等价于在系统里被列为不受限制的后台） */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching {
            power.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(true)
    }

    /** 直接弹出「忽略电池优化」确认框，需要 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限 */
    fun requestIgnoreBatteryOptimizations(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 降级：系统白名单页（无权限也能到） */
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openIgnoreBatteryOptimizations(context: Context): Boolean {
        val direct = requestIgnoreBatteryOptimizations(context)
        if (runCatching { context.startActivity(direct) }.isSuccess) return true
        val list = batteryOptimizationSettingsIntent()
        return runCatching { context.startActivity(list) }.isSuccess
    }

    /** MIUI 自启管理页 */
    fun autostartSettingsIntent(context: Context): Intent =
        Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            putExtra("package_name", context.packageName)
            putExtra("package_label", "Pocket Diary")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun openAutostartSettings(context: Context): Boolean {
        val intent = autostartSettingsIntent(context)
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
        val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    /** 是否是国产 ROM 上常见的「不授权就杀后台」的机型 */
    fun needsAutostartHint(): Boolean =
        Build.MANUFACTURER.lowercase().let {
            it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") ||
                it.contains("huawei") || it.contains("honor") || it.contains("oppo") ||
                it.contains("realme") || it.contains("vivo") || it.contains("meizu") ||
                it.contains("samsung")
        }
}