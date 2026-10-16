package com.example.diary.data.focus

import com.example.diary.data.local.FocusSession

/**
 * 专注守卫的判定规则（纯逻辑，供单测）：
 * 无障碍只上报前台包名，这里决定「这个包该不该被拉回」。
 */
object FocusGuard {

    /**
     * 系统关键包永远放行：打电话、闹钟、信息、设置、无障碍/权限弹窗等。
     * 少了它们，用户在专注中接个电话都会被弹回专注页。
     */
    val DEFAULT_ALLOWED_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.telecom",
        "com.android.mms",
        "com.android.contacts",
        "com.android.providers.telephony",
        "com.android.camera",
        "com.android.camera2",
        "com.android.alarm",
        "com.google.android.deskclock",
        "com.android.emergency",
        "com.android.packageinstaller",
        "com.android.shell",
        "com.google.android.packageinstaller",
        "android",
        "com.miui.securitycenter",
        "com.miui.home",
        "com.miui.player",
        "com.miui.notes"
    )

    const val COOLDOWN_MS = 1500L

    /** 专注段是否正在跑（只有专注段需要守卫，休息段不打扰） */
    fun isGuarding(sessionActive: Boolean, phase: Int?): Boolean =
        sessionActive && phase != null && phase == FocusSession.PHASE_FOCUS

    /** 该不该把当前前台包拉回专注页 */
    fun shouldBlock(
        ownPackage: String,
        foregroundPackage: String?,
        userAllowed: Set<String>,
        guardEnabled: Boolean,
        sessionActive: Boolean,
        phase: Int?
    ): Boolean {
        if (!guardEnabled) return false
        if (!isGuarding(sessionActive, phase)) return false
        val pkg = foregroundPackage ?: return false
        if (pkg == ownPackage) return false
        return !isAllowed(pkg, userAllowed)
    }

    fun isAllowed(foregroundPackage: String, userAllowed: Set<String>): Boolean =
        foregroundPackage in DEFAULT_ALLOWED_PACKAGES || foregroundPackage in userAllowed

    /**
     * 同一次「离开」只拉回一次：冷却期内、或还是上一个包，都不再重复弹回。
     * 否则通知栏、系统弹窗会带来连续 windowStateChanged，把用户反复弹回。
     */
    fun withinCooldown(lastAtElapsed: Long, nowElapsed: Long): Boolean =
        nowElapsed - lastAtElapsed < COOLDOWN_MS

    /**
     * 冷却期内一律不再弹回——通知栏、系统弹窗、页面间跳转都会连着发 windowStateChanged，
     * 不挡一下会被弹回十几遍。冷却过了（含同一个包）才允许下一次。
     */
    fun shouldReact(lastBlockedAtElapsed: Long, nowElapsed: Long): Boolean =
        !withinCooldown(lastBlockedAtElapsed, nowElapsed)
}