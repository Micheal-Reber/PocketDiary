package com.example.diary.data.focus

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 小米/红米「后台弹出界面」权限探测。
 *
 * MIUI/HyperOS 有个私有 AppOp（opcode 10021，界面上叫「后台弹出界面」），默认关闭。
 * 关着的时候，后台服务（包括 AccessibilityService）调 startActivity() 会被**静默丢弃**——
 * 不抛异常、不打日志，intent 直接消失，于是专注守卫看着像没生效。
 *
 * 思路参考 nsh07/ZenLock 的 MiuiUtils（MIT），非小米设备一律视为允许。
 */
object BackgroundStart {

    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco")
    }

    /** 能否从后台直接拉起界面；false 时必须走全屏 Intent 通知兜底 */
    fun canStartActivityFromBackground(context: Context): Boolean {
        if (!isXiaomiDevice()) return true
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
        return try {
            val check = AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            val mode = check.invoke(appOps, OP_BACKGROUND_START_ACTIVITY, android.os.Process.myUid(), context.packageName)
            !isDeniedByMode(mode as? Int)
        } catch (e: Exception) {
            // 反射失败（可能不是 MIUI 固件）：按允许处理，真被拦了走通知兜底
            Log.d("BackgroundStart", "AppOps check failed: ${e.message}")
            true
        }
    }

    /** 纯判定（供单测）：非小米或读不到状态都按放行，只有明确的拒绝才算被拦 */
    fun isDeniedByMode(mode: Int?): Boolean =
        mode != null && mode != AppOpsManager.MODE_ALLOWED

    /** 跳到小米权限编辑器；不同固件类名不同，按可用性降级到应用详情页 */
    fun permissionEditorIntent(context: Context): Intent = permissionEditorIntents(context).first()

    /**
     * 候选页按顺序尝试：小米权限编辑器两个类名 → 应用详情页。
     * 不用 resolveActivity 预判——包可见性可能让它误判为不存在而错过真正的入口。
     */
    fun permissionEditorIntents(context: Context): List<Intent> {
        val editors = listOf(
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
        ).map { className ->
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                component = ComponentName("com.miui.securitycenter", className)
                putExtra("extra_pkgname", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return editors + Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}