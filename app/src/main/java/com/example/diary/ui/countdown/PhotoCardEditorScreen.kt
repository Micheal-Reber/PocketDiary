package com.example.diary.ui.countdown

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.image.EventImageStore
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.repository.CountdownRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PhotoCardEditorScreen(
    eventId: Long,
    repository: CountdownRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val event by remember(eventId) { repository.observeById(eventId) }
        .collectAsState(initial = null)
    var imageVersion by remember { mutableIntStateOf(0) }
    var blurRadius by remember { mutableIntStateOf(0) }
    var fontDark by remember { mutableStateOf(false) }
    var photoOffsetY by remember { mutableFloatStateOf(0f) }
    var photoScale by remember { mutableFloatStateOf(1f) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var pickerOpened by remember { mutableStateOf(false) }
    // 编辑会话标记：旋转/进程重建后为 true（保留草稿）；新进入为 false（清掉上次遗留草稿）
    var editingSession by rememberSaveable { mutableStateOf(false) }

    val photoBitmap by produceState<ImageBitmap?>(null, eventId, imageVersion, editingSession) {
        value = withContext(Dispatchers.IO) {
            if (!editingSession) return@withContext null
            // 草稿优先：选图未点完成时预览草稿，正式文件保持原样
            val file = EventImageStore.draftFile(context, eventId).takeIf { it.exists() }
                ?: EventImageStore.file(context, eventId)
            if (file.exists()) BackgroundImageStore.decode(context, file.absolutePath, maxDim = 1400)
            else null
        }
    }

    fun currentLimit(): Float {
        val bmp = photoBitmap ?: return 0f
        return photoOffsetLimitY(
            contentSize.width.toFloat(), contentSize.height.toFloat(),
            bmp.width, bmp.height, photoScale
        )
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // 只写草稿：点「完成」才覆盖正式背景，直接退出则丢弃
                EventImageStore.importToDraft(context, uri, eventId)
                clearBlurCache(eventId)
                photoOffsetY = 0f
                photoScale = 1f
                imageVersion++
            }
        }
    }

    LaunchedEffect(eventId) {
        if (!editingSession) {
            EventImageStore.deleteDraft(context, eventId) // 上次异常退出遗留的草稿
            editingSession = true
        }
        repository.get(eventId)?.let { eventValue ->
            blurRadius = eventValue.blurRadius
            fontDark = eventValue.fontDark
            photoOffsetY = eventValue.photoOffsetY
            photoScale = eventValue.photoScale.coerceAtLeast(1f)
            if (!EventImageStore.exists(context, eventId) &&
                !EventImageStore.draftFile(context, eventId).exists() && !pickerOpened
            ) {
                pickerOpened = true
                pickPhotoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }

    // 返回（图标/手势）= 放弃本次编辑：丢弃草稿，正式背景与 DB 保持原样
    fun discardAndBack() {
        EventImageStore.deleteDraft(context, eventId)
        onBack()
    }

    BackHandler { discardAndBack() }

    val currentEvent = event ?: return
    val state = DateMath.compute(
        currentEvent.date,
        currentEvent.repeatRule,
        currentEvent.plusOne,
        java.time.LocalDate.now()
    )
    val bigNumber = when (state) {
        is DateMath.CountState.Today -> "今"
        is DateMath.CountState.Countdown -> "${state.days}"
        is DateMath.CountState.Countup -> "${state.days}"
    }
    val accent = eventAccent(currentEvent.colorIndex, state)

    fun saveAndBack() {
        scope.launch {
            // 点「完成」才把草稿转正：覆盖正式背景文件（失败则草稿保留、本次不应用）
            val promoted = EventImageStore.promoteDraft(context, eventId)
            if (promoted != null) clearBlurCache(eventId)
            repository.save(
                currentEvent.copy(
                    blurRadius = blurRadius,
                    fontDark = fontDark,
                    photoOffsetY = photoOffsetY.coerceIn(-currentLimit(), currentLimit()),
                    photoScale = photoScale.coerceIn(1f, 3f),
                    textureIndex = if (EventImageStore.exists(context, eventId)) -1
                    else currentEvent.textureIndex
                )
            )
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调整背景图片") },
                navigationIcon = {
                    IconButton(onClick = { discardAndBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { saveAndBack() }) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.size(4.dp))
                        Text("完成")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("背景模糊", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = blurRadius.toFloat(),
                        onValueChange = { blurRadius = it.roundToInt() },
                        valueRange = 0f..CountdownEvent.BLUR_MAX.toFloat(),
                        steps = CountdownEvent.BLUR_MAX - 1
                    )
                    Text("图片缩放", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = photoScale,
                        onValueChange = {
                            photoScale = it.coerceIn(1f, 3f)
                            photoOffsetY = photoOffsetY.coerceIn(-currentLimit(), currentLimit())
                        },
                        valueRange = 1f..3f,
                        steps = 19
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (fontDark) "黑字" else "白字", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { fontDark = !fontDark }) {
                            Text("切换文字颜色")
                        }
                    }
                    Button(
                        onClick = {
                            pickPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (photoBitmap == null) "选择背景图片" else "更换背景图片")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                "拖动图片调整位置，双指缩放图片",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(photoBitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            photoScale = (photoScale * zoom).coerceIn(1f, 3f)
                            val limit = currentLimit()
                            photoOffsetY = (photoOffsetY + pan.y / size.height).coerceIn(-limit, limit)
                        }
                    }
            ) {
                PhotoCardContent(
                    eventId = currentEvent.id,
                    photoBitmap = photoBitmap,
                    blurRadius = blurRadius,
                    fontDark = fontDark,
                    photoOffsetY = photoOffsetY,
                    photoScale = photoScale,
                    eventName = currentEvent.name,
                    bigNumber = bigNumber,
                    accent = accent,
                    dateLine = currentEvent.date,
                    endDate = currentEvent.endDate,
                    time = currentEvent.time,
                    textureIndex = currentEvent.textureIndex,
                    modifier = Modifier.fillMaxSize(),
                    contentModifier = Modifier.onSizeChanged { contentSize = it }
                )
            }
        }
    }
}
