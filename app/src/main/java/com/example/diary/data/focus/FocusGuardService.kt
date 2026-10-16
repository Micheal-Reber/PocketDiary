package com.example.diary.data.focus

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.diary.MainActivity
import com.example.diary.data.preferences.FocusPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 专注守卫：用户在专注段切到别的 App 时把他拉回专注页。
 *
 * 隐私边界：只读 AccessibilityEvent 的**前台包名**（res/xml 里 canRetrieveWindowContent=false，
 * 不取窗口内容、不做 OCR、不装截屏），也不读事件里的文本。
 *
 * 尽力而为：系统或厂商可以随时杀掉服务，用户也可以自己关掉无障碍，
 * 所以这里只做「拉回 + 计数」，不做本段作废这类硬惩罚。
 */
class FocusGuardService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var preferences: FocusPreferences? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtElapsed = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        onConnectionChanged?.invoke()
    }

    override fun onDestroy() {
        isConnected = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        scope.launch { evaluate(foreground) }
    }

    private suspend fun evaluate(foreground: String) {
        val prefs = preferences ?: FocusPreferences(applicationContext).also { preferences = it }
        if (!prefs.guardEnabled.first()) return
        val record = FocusRuntime.current()
        val shouldBlock = FocusGuard.shouldBlock(
            ownPackage = packageName,
            foregroundPackage = foreground,
            userAllowed = prefs.guardAllowedPackages.first(),
            guardEnabled = true,
            sessionActive = record?.running == true,
            phase = record?.phase
        )
        if (!shouldBlock) return

        val now = SystemClock.elapsedRealtime()
        if (!FocusGuard.shouldReact(lastBlockedAtElapsed, now)) return
        lastBlockedPackage = foreground
        lastBlockedAtElapsed = now
        prefs.noteGuardBlock()
        bringBack(record?.taskId)
    }

    private fun bringBack(taskId: Long?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
            putExtra(MainActivity.EXTRA_FOCUS_TASK_ID, taskId ?: -1L)
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        var onConnectionChanged: (() -> Unit)? = null

        fun component(context: Context): ComponentName =
            ComponentName(context, FocusGuardService::class.java)

        /** 用户是否已经在系统里打开了本服务的无障碍开关 */
        fun isEnabled(context: Context): Boolean {
            val expected = component(context)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.split(':').any { entry ->
                val component = ComponentName.unflattenFromString(entry)
                component != null && component == expected
            }
        }

        fun accessibilitySettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setData(android.net.Uri.parse("package:${context.packageName}"))
            }
    }
}