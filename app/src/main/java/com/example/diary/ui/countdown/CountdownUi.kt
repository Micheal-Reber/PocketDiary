package com.example.diary.ui.countdown

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.diary.R
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.countdown.DateMath.CountState
import com.example.diary.data.countdown.TextureLibrary
import java.time.LocalDate

/** 倒数日 UI 共享件——色板 / 徽章配色 / 过程式纹理背景（统一使用 TextureLibrary）。 */

/**
 * 预设色板：下标 0 = 自动（倒数蓝/正数橙），1..8 为显式色。
 * 与 Habit.colorIndex 的「下标存库」惯例一致。
 */
object CountdownPalette {
    const val AUTO = 0
    val colors: List<Color> = listOf(
        Color(0xFFE53935), // 1 红
        Color(0xFFF2994A), // 2 橙（自动-正数同色）
        Color(0xFFF2C94C), // 3 黄
        Color(0xFF27AE60), // 4 绿
        Color(0xFF26C6DA), // 5 青
        Color(0xFF4A90D9), // 6 蓝（自动-倒数同色）
        Color(0xFF9B51E0), // 7 紫
        Color(0xFFEB5FA7)  // 8 粉
    )
}

private val AutoCountdownBlue = CountdownPalette.colors[5]
private val AutoCountupOrange = CountdownPalette.colors[1]

/** 徽章/详情主色：colorIndex==0 按状态自动，否则取显式色板色。 */
fun eventAccent(colorIndex: Int, state: DateMath.CountState): Color = when {
    colorIndex == CountdownPalette.AUTO -> if (state is CountState.Countup) AutoCountupOrange else AutoCountdownBlue
    // 编辑页存的是 1..8（色板展示序），数组下标从 0 起——必须 -1 对齐，否则整体错一位
    colorIndex in 1..CountdownPalette.colors.size -> CountdownPalette.colors[colorIndex - 1]
    else -> AutoCountdownBlue
}

/** 星期短标（周一..周日）。 */
fun weekdayLabel(context: Context, date: LocalDate): String =
    context.resources.getStringArray(R.array.weekday_full)[date.dayOfWeek.value - 1]

/** 卡片/详情副标题短语（不带事件名）：还有 N 天 / 已经 N 天 / 就是今天。 */
fun stateLabel(context: Context, state: DateMath.CountState): String = when (state) {
    is CountState.Today -> context.getString(R.string.cd_state_today)
    is CountState.Countdown -> context.getString(R.string.cd_state_remaining, state.days)
    is CountState.Countup -> context.getString(R.string.cd_state_elapsed, state.days)
}

/** 照片卡顶栏短语（日历页风，不含天数）：还有 / 已经 / 今天。 */
fun headerStatePhrase(context: Context, state: DateMath.CountState): String = when (state) {
    is CountState.Today -> context.getString(R.string.cd_header_today)
    is CountState.Countdown -> context.getString(R.string.cd_header_remaining)
    is CountState.Countup -> context.getString(R.string.cd_header_elapsed)
}

/** 内置过程式纹理数量（详情页背景选项），同步 TextureLibrary.TEXTURE_COUNT。 */
const val TEXTURE_COUNT = 4

/**
 * 过程式纹理背景——委托给 TextureLibrary（4 种 Shader 纹理）。
 * 保持原有签名兼容，内部实现已迁移至 TextureLibrary。
 */
@Composable
fun TextureBackdrop(textureIndex: Int, accent: Color, modifier: Modifier = Modifier) {
    TextureLibrary.TextureBackdrop(textureIndex, accent, modifier)
}
