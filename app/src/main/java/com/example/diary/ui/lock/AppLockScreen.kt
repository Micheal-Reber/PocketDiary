package com.example.diary.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.data.preferences.AppLockPreferences
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.navigation.glassStroke
import com.example.diary.ui.navigation.glassTint
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LockMode { Unlock, SetPin, Change }

private const val MAX_FAILS = 5
private const val COOLDOWN_SECONDS = 30

@Composable
fun AppLockScreen(
    mode: LockMode,
    lockPreferences: AppLockPreferences,
    title: String,
    pinLength: Int = 4,
    onVerified: () -> Unit,
    onPinSet: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    // SetPin: new→confirm；Change: old→new→confirm
    var stage by remember {
        mutableStateOf(if (mode == LockMode.Change) "old" else if (mode == LockMode.SetPin) "new" else "entry")
    }
    var hint by remember {
        mutableStateOf(
            when (stage) {
                "old" -> "输入当前密码"
                "new" -> "输入 4-6 位新密码"
                else -> null
            }
        )
    }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var fails by remember { mutableIntStateOf(0) }
    var cooldownLeft by remember { mutableIntStateOf(0) }
    var verifying by remember { mutableStateOf(false) }

    LaunchedEffect(cooldownLeft) {
        if (cooldownLeft > 0) {
            delay(1000)
            cooldownLeft -= 1
        }
    }

    fun onWrongPassword() {
        entry = ""
        fails += 1
        if (fails >= MAX_FAILS) {
            fails = 0
            cooldownLeft = COOLDOWN_SECONDS
            errorText = "尝试次数过多，${COOLDOWN_SECONDS} 秒后可重试"
        } else {
            errorText = "密码错误"
        }
    }

    // 校验当前已输入的密码（解锁 / 修改密码验旧密码共用，含冷却）
    fun verifyCurrent(onSuccess: () -> Unit) {
        if (verifying || cooldownLeft > 0) return
        if (entry.length !in 4..6) {
            errorText = "请输入 4-6 位密码"
            entry = ""
            return
        }
        verifying = true
        val attempt = entry
        scope.launch {
            if (lockPreferences.verify(attempt)) {
                entry = ""
                errorText = null
                onSuccess()
            } else {
                onWrongPassword()
            }
            verifying = false
        }
    }

    fun handleConfirm() {
        if (cooldownLeft > 0 || verifying) return
        when {
            mode == LockMode.Unlock -> verifyCurrent(onVerified)
            stage == "old" -> verifyCurrent {
                stage = "new"
                hint = "输入 4-6 位新密码"
            }
            else -> {
                if (entry.length < 4) {
                    errorText = "至少 4 位"
                    return
                }
                val first = firstPin
                if (first == null) {
                    firstPin = entry
                    entry = ""
                    errorText = null
                    hint = "再次输入确认"
                    stage = "confirm"
                } else if (entry == first) {
                    onPinSet(entry)
                } else {
                    firstPin = null
                    entry = ""
                    errorText = "两次输入不一致，请重新设置"
                    hint = "输入 4-6 位新密码"
                    stage = "new"
                }
            }
        }
    }

    fun onDigit(d: String) {
        if (cooldownLeft > 0 || verifying) return
        if (entry.length >= 6) return
        entry += d
        errorText = null
        // 解锁模式：输满位数自动校验；验旧密码同理
        if ((mode == LockMode.Unlock || stage == "old") && entry.length == pinLength) {
            handleConfirm()
        }
    }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.xl)
            .padding(bottom = BottomBarContentInset + Spacing.xl + Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    text = if (cooldownLeft > 0) "尝试次数过多" else title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    text = if (cooldownLeft > 0) "${COOLDOWN_SECONDS} 秒后可重试（$cooldownLeft）"
                    else errorText ?: hint.orEmpty(),
                    color = if (errorText != null && cooldownLeft == 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    val dotCount = if (mode == LockMode.Unlock || stage == "old") pinLength else 6
                    repeat(dotCount) { i ->
                        if (i < entry.length) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        val keyEnabled = cooldownLeft == 0 && !verifying
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    row.forEach { d ->
                        GlassKey(enabled = keyEnabled, dark = dark, onClick = { onDigit(d) }) {
                            Text(d, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                GlassKey(
                    enabled = keyEnabled && entry.isNotEmpty(),
                    dark = dark,
                    onClick = { if (entry.isNotEmpty()) entry = entry.dropLast(1) }
                ) {
                    Icon(Icons.Outlined.Backspace, contentDescription = "删除", modifier = Modifier.size(30.dp))
                }
                GlassKey(enabled = keyEnabled, dark = dark, onClick = { onDigit("0") }) {
                    Text("0", fontSize = 30.sp, fontWeight = FontWeight.Medium)
                }
                GlassKey(enabled = keyEnabled, dark = dark, onClick = { handleConfirm() }) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "确定",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        // 固定高度槽位：有取消按钮时显示，无取消（日记解锁）时留同高空白，
        // 保证两种场景键盘/内容垂直位置完全一致
        Box(
            Modifier
                .height(Spacing.l + 36.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (onCancel != null) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onCancel() }
                        .padding(horizontal = Spacing.xl, vertical = Spacing.s)
                )
            }
        }
    }
}

@Composable
private fun GlassKey(
    enabled: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(glassTint(dark), CircleShape)
            .border(1.dp, glassStroke(dark), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (enabled) content()
            else CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)) {
                content()
            }
        }
    }
}
