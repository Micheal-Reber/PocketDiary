package com.example.diary.data.focus

/**
 * Compose 主题色 → 非 Compose 代码（前台服务 / 通知）的取色桥。
 * 通知的分段进度条要用品牌色（开动态取色时还会随壁纸变），
 * 而 FocusService 不是 Composable 拿不到 MaterialTheme，只能由主题侧 publish 过来。
 */
object FocusColors {

    @Volatile
    var phaseFocus: Int = 0xFFC9B8FF.toInt()

    @Volatile
    var phaseBreak: Int = 0xFFFFB4A2.toInt()

    fun publish(focusArgb: Int, breakArgb: Int) {
        if (focusArgb != 0) phaseFocus = focusArgb
        if (breakArgb != 0) phaseBreak = breakArgb
    }
}