package com.example.diary.ui.todo

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.TodoItem
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoAlarmNotifier
import com.example.diary.data.todo.TodoReminderScheduler
import com.example.diary.ui.theme.DiaryTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 待办闹钟响铃页：锁屏全屏拉起，循环闹钟铃声 + 震动直到用户处理。
 * 完成=打勾并取消闹钟；稍后提醒=5 分钟后再响；关闭=停铃保留待办。
 */
class TodoRingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TODO_ID = "todo_id"
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentItem: TodoItem? = null
    private lateinit var repository: TodoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        if (todoId == -1L) { finish(); return }

        // 锁屏/熄屏下也能拉起并亮屏（minSdk 26：API 27+ 用方法，26 用窗口 flag）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

        // 通知已被全屏 Intent 拉起 → 收掉，避免压在响铃页上
        TodoAlarmNotifier.cancel(this, todoId)

        repository = TodoRepository(AppDatabase.getInstance(this).todoDao(), this)
        setContent {
            DiaryTheme(darkTheme = true, dynamicColor = false) {
                RingContent(
                    repository = repository,
                    todoId = todoId,
                    onReady = { currentItem = it },
                    onFinish = { completed -> finishWithAction(completed) }
                )
            }
        }
        startRinging()
    }

    /** completed: true=完成待办（save 内同步取消闹钟）/ false=仅停铃 */
    private fun finishWithAction(completed: Boolean?) {
        val item = currentItem
        if (completed != null && item != null) {
            lifecycleScope.launch {
                try {
                    repository.save(item.copy(done = completed))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 落库失败也要退出；闹钟兜底取消
                    if (completed) TodoReminderScheduler.cancel(this@TodoRingActivity, item.id)
                }
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        currentItem?.let { TodoAlarmNotifier.cancel(this, it.id) }
    }

    override fun onStop() {
        super.onStop()
        // 退到后台但未销毁：静默补一条通知作为回入口（不响第二次铃）
        if (!isFinishing) {
            currentItem?.let { TodoAlarmNotifier.show(this, it, silent = true) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }

    private fun startRinging() {
        // 铃声：系统闹钟铃声，循环播放
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (uri != null) {
                player = MediaPlayer().apply {
                    setDataSource(this@TodoRingActivity, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            }
        } catch (_: Exception) {
            player = null // 无闹钟铃声/资源占用 → 仅震动兜底
        }
        // 震动：循环波形
        @Suppress("DEPRECATION")
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        } catch (_: Exception) {
        }
    }

    private fun stopRinging() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }
}

@Composable
private fun RingContent(
    repository: TodoRepository,
    todoId: Long,
    onReady: (TodoItem) -> Unit,
    onFinish: (Boolean?) -> Unit
) {
    val item by produceState<TodoItem?>(initialValue = null, todoId) {
        value = withContext(Dispatchers.IO) {
            repository.get(todoId)?.also { loaded -> onReady(loaded) }
        }
    }
    val context = LocalContext.current

    BackHandler { onFinish(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("⏰", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = item?.text ?: "待办提醒",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                item == null -> ""
                item!!.done -> "已完成"
                else -> "到点了"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onFinish(true) },
            enabled = item != null && !item!!.done,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("完成", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                item?.let { TodoReminderScheduler.snooze(context.applicationContext, it) }
                onFinish(null)
            },
            enabled = item != null,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("稍后提醒（5 分钟）", fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onFinish(null) },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("关闭铃声", fontSize = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
