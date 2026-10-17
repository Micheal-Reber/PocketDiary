package com.example.diary.data.focus

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
 * 拉回节奏：先等 [FocusGuard.SETTLE_MS] 让目标窗口稳定 → 拉回 → 再过
 * [FocusGuard.VERIFY_MS] 校验；若这期间没收到自己包的前台事件，说明拉回被系统丢弃了，补一次。
 *
 * 尽力而为：系统或厂商可以随时杀掉服务，用户也可以自己关掉无障碍，
 * 所以这里只做「拉回 + 计数」，不做本段作废这类硬惩罚。
 */
class FocusGuardService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val presentRunnable = Runnable { presentBringBack() }
    private val verifyRunnable = Runnable { verifyBringBack() }

    private var preferences: FocusPreferences? = null
    private var lastBlockedAtElapsed = 0L
    private var pendingBringBack = false
    private var pendingTaskId: Long? = null
    private var launcherCache: Pair<String, Boolean>? = null
    private var enabledImePackages: Set<String>? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        onConnectionChanged?.invoke()
    }

    override fun onDestroy() {
        isConnected = false
        clearPending()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        clearPending()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        clearPending()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (foreground == packageName) {
            // 已经回到本 App：拉回成功，取消待办校验并收掉兜底通知
            clearPending()
            return
        }
        scope.launch { evaluate(foreground) }
    }

    private suspend fun evaluate(foreground: String) {
        val prefs = preferences ?: FocusPreferences(applicationContext).also { preferences = it }
        if (!prefs.guardEnabled.first()) {
            clearPending()
            return
        }
        val record = FocusRuntime.current()
        val sessionActive = record?.running == true
        val phase = record?.phase
        if (!FocusGuard.isGuarding(sessionActive, phase) || !shouldEnforce()) {
            clearPending()
            return
        }
        val shouldBlock = FocusGuard.shouldBlock(
            ownPackage = packageName,
            foregroundPackage = foreground,
            userAllowed = prefs.guardAllowedPackages.first(),
            guardEnabled = true,
            sessionActive = sessionActive,
            phase = phase,
            allowLauncher = prefs.guardAllowLauncher.first(),
            isLauncher = isLauncherPackage(foreground),
            isKeyboard = isEnabledKeyboard(foreground)
        )
        if (!shouldBlock) {
            clearPending()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (!FocusGuard.shouldReact(lastBlockedAtElapsed, now)) return
        lastBlockedAtElapsed = now
        prefs.noteGuardBlock()
        requestBringBack(record?.taskId)
    }

    /** 息屏或锁屏时不拉回：拉回没有意义，还会在解锁瞬间误弹 */
    private fun shouldEnforce(): Boolean {
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        if (!power.isInteractive) return false
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return true
        return !keyguard.isKeyguardLocked
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        launcherCache?.let { if (it.first == pkg) return it.second }
        val known = FocusGuard.isKnownLauncher(pkg)
        val resolved = if (known) {
            true
        } else {
            runCatching {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                    .any { it.activityInfo?.packageName == pkg }
            }.getOrDefault(false)
        }
        launcherCache = pkg to resolved
        return resolved
    }

    /**
     * 当前启用的输入法必须恒放行：否则在白名单应用里打字会被当成「切走了」而拉回。
     * 角色向系统查（InputMethodManager），不猜厂商包名；启用列表几乎不变，查一次就缓存。
     */
    private fun isEnabledKeyboard(pkg: String): Boolean {
        val packages = enabledImePackages ?: run {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val resolved = runCatching {
                imm?.enabledInputMethodList.orEmpty().mapNotNull { it.packageName }.toSet()
            }.getOrDefault(emptySet())
            enabledImePackages = resolved
            resolved
        }
        return pkg in packages
    }

    private fun requestBringBack(taskId: Long?) {
        if (pendingBringBack) return
        pendingBringBack = true
        pendingTaskId = taskId
        handler.postDelayed(presentRunnable, FocusGuard.SETTLE_MS)
        handler.postDelayed(verifyRunnable, FocusGuard.SETTLE_MS + FocusGuard.VERIFY_MS)
    }

    private fun presentBringBack() {
        val record = FocusRuntime.current()
        if (!shouldEnforce() || !FocusGuard.isGuarding(record?.running == true, record?.phase)) {
            clearPending()
            return
        }
        val taskId = pendingTaskId
        if (BackgroundStart.canStartActivityFromBackground(this)) {
            if (runCatching { startActivity(focusIntent(taskId)) }.isFailure) {
                FocusNotifier.showGuardBlocked(this, taskId)
            }
        } else {
            // 小米「后台弹出界面」被关：startActivity 会被静默丢弃，改用全屏 Intent 通知（MIUI 不拦它）
            FocusNotifier.showGuardBlocked(this, taskId)
        }
    }

    /** 没收到自己包的前台事件 = 上一次拉回没生效，补一次；不重发校验，避免死循环 */
    private fun verifyBringBack() {
        if (!pendingBringBack) return
        handler.removeCallbacks(presentRunnable)
        presentBringBack()
    }

    private fun clearPending() {
        pendingBringBack = false
        pendingTaskId = null
        handler.removeCallbacks(presentRunnable)
        handler.removeCallbacks(verifyRunnable)
        FocusNotifier.cancelGuardBlocked(this)
    }

    private fun focusIntent(taskId: Long?): Intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
        putExtra(MainActivity.EXTRA_FOCUS_TASK_ID, taskId ?: -1L)
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

        /**
         * 注意别给这个 intent 加 `setData(package:…)`：ACTION_ACCESSIBILITY_SETTINGS 的
         * 过滤器没有 data scheme，带了 data 就匹配不到任何 Activity（HyperOS 上解析到的是
         * com.android.settings.accessibility.MiuiAccessibilitySettingsActivity，同样不收 data），
         * 抛 ActivityNotFoundException 后表现为「点了没反应」。
         */
        fun accessibilitySettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}