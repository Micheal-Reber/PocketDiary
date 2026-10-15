package com.example.diary.data.focus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级番茄钟运行态。UI 与前台服务共用同一份 StateFlow，
 * 避免两边各存一份导致进度不同步（对不上时用户会以为卡住了）。
 * 持久化副本走 [FocusSessionStore]，所以进程重建后能从这里恢复。
 */
object FocusRuntime {

    private val _record = MutableStateFlow<FocusRecord?>(null)
    val record: StateFlow<FocusRecord?> = _record.asStateFlow()

    /** 服务是否在前台跑着（对照 Tomato 的 TimerService.isRunning） */
    @Volatile
    var serviceAlive: Boolean = false
        private set

    val isRunning: Boolean get() = _record.value?.running == true

    /** 有会话（哪怕暂停着）——用于在待办 Tab 显示「继续专注」入口 */
    val hasSession: Boolean get() = _record.value != null

    fun publish(value: FocusRecord?) {
        _record.value = value
    }

    fun current(): FocusRecord? = _record.value

    fun setServiceAlive(alive: Boolean) {
        serviceAlive = alive
    }

    /** 就地更新当前记录 */
    fun transform(block: (FocusRecord) -> FocusRecord) {
        _record.value = _record.value?.let(block)
    }
}