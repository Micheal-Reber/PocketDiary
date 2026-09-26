package com.example.diary.ui.countdown

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.countdown.DateMath.CountState
import com.example.diary.data.countdown.ShareCardRenderer
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.image.EventImageStore
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.navigation.GlassCapsule
import com.example.diary.ui.navigation.glassBackdrop
import com.example.diary.ui.navigation.rememberGlassBackdrop
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.abs
import java.io.File
import java.time.LocalDate

/**
 * 倒数日详情页：纹理/照片背景 + 超大数字 + 日期脚注 + 动作栏
 * （分享 / 存为图片 / 背景 / 高亮旗标 / 新建），左下相机快捷换背景图。
 */

// ── Helper composables (defined BEFORE main function so they're in scope) ──

@Composable
private fun RowScope.ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = Spacing.xs, vertical = Spacing.s)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 无涟漪点击（纹理缩略图/菜单行用，避免涟漪盖过小面积色块）。 */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

/** 经典全屏内容：支持 fontDark 字色切换（黑/白），纹理背景委托 TextureLibrary。 */
@Composable
private fun ClassicFullscreenContent(
    photoBitmap: ImageBitmap?,
    textureIndex: Int,
    accent: androidx.compose.ui.graphics.Color,
    eventName: String,
    stateLabel: String,
    bigNumber: String,
    dateLine: String,
    endDate: String?,
    time: String?,
    fontDark: Boolean = false
) {
    val textColor = if (fontDark) Color.Black else Color.White
    val subTextColor = if (fontDark) Color(0xFF333333) else Color(0xFFCCCCCC)
    val dateTextColor = if (fontDark) Color(0xFF555555) else Color(0xFFAAAAAA)

    // ── 背景层：照片 > 纹理 > 纯色渐变 ──
    when {
        photoBitmap != null -> {
            Image(
                bitmap = photoBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        textureIndex in 0 until TEXTURE_COUNT -> TextureBackdrop(textureIndex, accent)
        else -> Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.background)
                )
            )
        )
    }

    // ── 内容层 ──
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.6f))
        Text(
            eventName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = textColor
        )
        Spacer(Modifier.height(Spacing.m))
        Text(
            stateLabel,
            style = MaterialTheme.typography.titleMedium,
            color = subTextColor
        )
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            bigNumber,
            fontSize = 140.sp,
            fontWeight = FontWeight.Black,
            color = accent,
            textAlign = TextAlign.Center
        )
        Text(
            if (bigNumber == "今") "" else "天",
            style = MaterialTheme.typography.titleLarge,
            color = subTextColor
        )
        Spacer(Modifier.weight(1f))

        Column(
            Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                dateLine,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = dateTextColor
            )
            if (!endDate.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text("结束 ${endDate}", style = MaterialTheme.typography.bodyMedium,
                    color = dateTextColor)
            }
            if (!time.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(time!!, style = MaterialTheme.typography.bodyMedium,
                    color = dateTextColor)
            }
        }
    }
}

/**
 * 照片卡片内容：三段式——顶=目标名，中=倒数大数字，底=目标日。
 * 有图：整卡铺照片 + scrim，文字全透明叠加；无图：顶栏事件色 / 底栏浅灰实色带。
 */
@Composable
internal fun PhotoCardContent(
    eventId: Long,
    photoBitmap: ImageBitmap?,
    blurRadius: Int,
    fontDark: Boolean,
    photoOffsetY: Float = 0f,
    photoScale: Float = 1f,
    eventName: String,
    bigNumber: String,
    accent: androidx.compose.ui.graphics.Color,
    dateLine: String,
    endDate: String?,
    time: String?,
    textureIndex: Int = -1,
    bottomInset: Dp = 0.dp,
    contentModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val textColor = if (fontDark) Color.Black else Color.White
    val scrimAlpha = if (fontDark) 0.12f else 0.28f
    val bodyFallback = if (fontDark) Color(0xFFF7F7F7) else Color(0xFF1A1A1C)
    val hasPhoto = photoBitmap != null
    val footerBg = Color(0xFFF0F1F3)
    val footerTextNoPhoto = Color(0xFF555555)
    val footerMutedNoPhoto = Color(0xFF777777)

    Box(modifier.fillMaxSize()) {
        when {
            textureIndex in 0 until TEXTURE_COUNT -> TextureBackdrop(textureIndex, accent)
            else -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            )
        }
        if (photoBitmap != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
        }

        Column(
            Modifier.fillMaxSize().padding(bottom = bottomInset),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1.25f)
                    .padding(vertical = Spacing.l)
            ) {
                Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).then(contentModifier)) {
                    if (photoBitmap != null) {
                        BlurCardImage(
                            bitmap = photoBitmap,
                            radiusDp = blurRadius,
                            eventId = eventId,
                            photoOffsetY = photoOffsetY,
                            photoScale = photoScale,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
                    } else {
                        Box(
                            Modifier.fillMaxSize()
                                .background(bodyFallback.copy(alpha = 0.78f))
                        )
                    }

                    Column(Modifier.fillMaxSize()) {
                        // ── 顶：目标名 ──
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (hasPhoto) Modifier
                                    else Modifier.background(accent)
                                )
                                .padding(horizontal = Spacing.m, vertical = Spacing.m),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                eventName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (hasPhoto) textColor else Color.White,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }

                        // ── 中：还有多久大数字 ──
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(Spacing.l)
                            ) {
                                Text(
                                    bigNumber,
                                    fontSize = 96.sp,
                                    lineHeight = 104.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                                if (bigNumber != "今") {
                                    Text(
                                        "天",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = textColor.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        // ── 底：目标日 ──
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (hasPhoto) Modifier
                                    else Modifier.background(footerBg)
                                )
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = Spacing.m,
                                    vertical = Spacing.s
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "目标日: $dateLine",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (hasPhoto) textColor else footerTextNoPhoto,
                                    textAlign = TextAlign.Center
                                )
                                if (!endDate.isNullOrBlank() || !time.isNullOrBlank()) {
                                    val extra = buildList {
                                        if (!endDate.isNullOrBlank()) add("结束 ${endDate}")
                                        if (!time.isNullOrBlank()) add(time!!)
                                    }.joinToString(" · ")
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        extra,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasPhoto) textColor.copy(alpha = 0.75f) else footerMutedNoPhoto
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 照片卡背景入口。模糊、文字颜色和图片位置统一在独立编辑页调整。 */
@Composable
private fun PhotoCardBackgroundSheet(
    hasPhoto: Boolean,
    textureIndex: Int,
    onTextureClick: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onResetPhoto: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            if (hasPhoto) "已设置照片背景" else "尚未设置照片背景",
            style = MaterialTheme.typography.bodyLarge
        )
        OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.padding(end = Spacing.xs))
            Text(if (hasPhoto) "调整图片位置和显示效果" else "选择背景图片")
        }
        Text("内置纹理", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TexturePickerRow(
            selectedIndex = textureIndex,
            onSelect = onTextureClick,
            showNone = true
        )
        if (hasPhoto) {
            TextButton(onClick = onResetPhoto, modifier = Modifier.fillMaxWidth()) {
                Text("恢复默认背景")
            }
        }
    }
}

/** 经典模式背景底表：字色切换 + 纹理选择 + 选图/恢复默认。 */
@Composable
private fun ClassicBackgroundSheet(
    hasPhoto: Boolean,
    textureIndex: Int,
    accent: androidx.compose.ui.graphics.Color,
    fontDarkPreview: Boolean,
    onFontDarkChange: (Boolean) -> Unit,
    onConfirmFontDark: (Boolean) -> Unit,
    onPickPhoto: () -> Unit,
    onResetPhoto: () -> Unit,
    onTextureClick: (Int) -> Unit
) {
    Column {
        // 字色切换（仅影响目前固定白字的经典文字）
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s)
        ) {
            Text("文字颜色")
            androidx.compose.material3.Switch(
                checked = fontDarkPreview,
                onCheckedChange = { newVal ->
                    onFontDarkChange(newVal)
                    onConfirmFontDark(newVal)
                }
            )
        }
        Text(
            if (fontDarkPreview) "黑字（适合浅色背景/纹理）" else "白字（适合深色背景/纹理）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l)
        )

        ListItem(
            headlineContent = { Text("从相册选择照片") },
            leadingContent = { Icon(Icons.Default.PhotoCamera, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickPhoto)
        )
        ListItem(
            headlineContent = { Text("恢复默认（无背景）") },
            leadingContent = { Icon(Icons.Default.Close, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onResetPhoto)
        )
        if (!hasPhoto) {
            Text(
                "内置纹理",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.s)
            )
            TexturePickerRow(
                selectedIndex = textureIndex,
                onSelect = onTextureClick,
                showNone = true
            )
        }
    }
}

// ── Main screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownDetailScreen(
    eventId: Long,
    repository: CountdownRepository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onEditPhoto: () -> Unit,
    onCreate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var activeEventId by rememberSaveable { mutableLongStateOf(eventId) }
    val allEvents by remember { repository.observeAll() }
        .collectAsState(initial = emptyList())

    LaunchedEffect(eventId) { activeEventId = eventId }
    LaunchedEffect(allEvents, activeEventId) {
        if (allEvents.isNotEmpty() && allEvents.none { it.id == activeEventId }) {
            activeEventId = allEvents.first().id
        }
    }

    // remember(activeEventId): 左右滑动切换事件时重建 Flow
    val event by remember(activeEventId) { repository.observeById(activeEventId) }
        .collectAsState(initial = null)
    // 背景图版本号：换图/清背景后自增，驱动重新解码
    var bgVersion by rememberSaveable { mutableIntStateOf(0) }
    var showBackgroundSheet by remember { mutableStateOf(false) }

    if (event == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val e = event!!
    val today = remember { LocalDate.now() }
    val state = DateMath.compute(e.date, e.repeatRule, e.plusOne, today)
    val accent = eventAccent(e.colorIndex, state)
    val anchor = DateMath.resolveAnchor(e.date, e.repeatRule, today)

    fun switchBySwipe(deltaX: Float) {
        if (abs(deltaX) < 72f) return
        val currentIndex = allEvents.indexOfFirst { it.id == e.id }
        if (currentIndex < 0) return
        val targetIndex = if (deltaX < 0f) currentIndex + 1 else currentIndex - 1
        allEvents.getOrNull(targetIndex)?.let { activeEventId = it.id }
    }

    val photoBitmap by produceState<ImageBitmap?>(
        initialValue = null, key1 = e.id, key2 = bgVersion
    ) {
        value = withContext(Dispatchers.IO) {
            val f = EventImageStore.file(context, e.id)
            if (f.exists()) BackgroundImageStore.decode(context, f.absolutePath, maxDim = 1400)
            else null
        }
    }

    fun bigNumberText(): String = when (state) {
        is CountState.Today -> "今"
        is CountState.Countdown -> "${state.days}"
        is CountState.Countup -> "${state.days}"
    }

    suspend fun renderCard(): File? {
        val extra = buildList {
            if (!e.endDate.isNullOrBlank()) add("结束 ${e.endDate}")
            if (!e.time.isNullOrBlank()) add(e.time)
        }
        val bmp = if (e.cardStyle == CountdownEvent.CARD_STYLE_PHOTO_CARD) {
            ShareCardRenderer.renderPhotoCard(
                context = context,
                eventId = e.id,
                eventName = e.name,
                accentArgb = accent.toArgb(),
                bigNumber = bigNumberText(),
                unit = if (state is CountState.Today) "" else "天",
                dateLine = e.date,
                extraLines = extra,
                blurRadius = e.blurRadius,
                fontDark = e.fontDark,
                photoOffsetY = e.photoOffsetY,
                photoScale = e.photoScale
            )
        } else {
            ShareCardRenderer.render(
                name = e.name,
                accentArgb = accent.toArgb(),
                headline = stateLabel(state),
                bigNumber = bigNumberText(),
                unit = if (state is CountState.Today) "" else "天",
                footLines = buildList {
                    add("${e.date} · ${weekdayLabel(anchor)}")
                    addAll(extra)
                },
                fontDark = e.fontDark
            )
        }
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "countdown_${e.id}_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return out
    }

    fun shareCard() {
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { renderCard() }.getOrNull() }
            if (file == null) {
                snackbar.showSnackbar("生成分享图失败", duration = SnackbarDuration.Short)
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享倒数日"))
        }
    }

    fun saveToGallery() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            scope.launch { snackbar.showSnackbar("保存到相册需 Android 10 及以上") }
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val file = renderCard()
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file!!.absolutePath)
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "countdown_${System.currentTimeMillis()}.jpg")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PocketDiary")
                    }
                    val uri = context.contentResolver.insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    )
                    uri?.let { context.contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, os)
                    } }
                    bitmap.recycle()
                    file.delete()
                    uri != null
                }.getOrDefault(false)
            }
            snackbar.showSnackbar(if (ok) "已保存到相册 Pictures/PocketDiary" else "保存失败",
                duration = SnackbarDuration.Short)
        }
    }

    // 照片卡专用：实时模糊预览状态（滑杆拖动时更新，松手写库）
    var blurRadiusPreview by remember(e.id) { mutableStateOf(e.blurRadius) }
    var fontDarkPreview by remember(e.id) { mutableStateOf(e.fontDark) }
    LaunchedEffect(e.id) { showBackgroundSheet = false }

    val backdrop = rememberGlassBackdrop()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "编辑")
                        }
                    }
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onGloballyPositioned { backdrop.contentOriginInRoot = it.boundsInRoot().topLeft }
                    .glassBackdrop(backdrop)
                    .pointerInput(allEvents, e.id) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = { switchBySwipe(totalDragX) },
                            onDragCancel = { totalDragX = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    }
            ) {
                // ── 分支渲染：CLASSIC / PHOTO_CARD（照片卡无图也保留框）──
                if (e.cardStyle == CountdownEvent.CARD_STYLE_PHOTO_CARD) {
                    // ===== 照片卡片：3:2 圆角框，有图则用图+模糊，无图用 TextureLibrary 纹理 =====
                    PhotoCardContent(
                        eventId = e.id,
                        photoBitmap = photoBitmap,
                        blurRadius = blurRadiusPreview,
                        fontDark = fontDarkPreview,
                        photoOffsetY = e.photoOffsetY,
                        photoScale = e.photoScale,
                        eventName = e.name,
                        bigNumber = bigNumberText(),
                        accent = accent,
                        dateLine = e.date,
                        endDate = e.endDate,
                        time = e.time,
                        textureIndex = e.textureIndex,
                        bottomInset = BottomBarContentInset
                    )
                } else {
                    // ===== 经典全屏：支持 fontDark 字色切换 =====
                    ClassicFullscreenContent(
                        photoBitmap = photoBitmap,
                        textureIndex = e.textureIndex,
                        accent = accent,
                        eventName = e.name,
                        stateLabel = stateLabel(state),
                        bigNumber = bigNumberText(),
                        dateLine = "${e.date} · ${weekdayLabel(anchor)}",
                        endDate = e.endDate,
                        time = e.time,
                        fontDark = e.fontDark
                    )
                }

                if (showBackgroundSheet) {
                    ModalBottomSheet(onDismissRequest = { showBackgroundSheet = false }) {
                        Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.xxl)) {
                            // 照片卡模式：模糊滑杆 + 字色切换 + 选图/恢复默认 (+无图时纹理选择器)
                            if (e.cardStyle == CountdownEvent.CARD_STYLE_PHOTO_CARD) {
                                PhotoCardBackgroundSheet(
                                    hasPhoto = photoBitmap != null,
                                    textureIndex = e.textureIndex,
                                    onTextureClick = { idx ->
                                        showBackgroundSheet = false
                                        scope.launch {
                                            repository.save(e.copy(textureIndex = idx))
                                            bgVersion++
                                        }
                                    },
                                    onPickPhoto = { showBackgroundSheet = false; onEditPhoto() },
                                    onResetPhoto = {
                                        showBackgroundSheet = false
                                        scope.launch {
                                            EventImageStore.clear(context, e.id)
                                            clearBlurCache(e.id)
                                            repository.save(e.copy(textureIndex = -1))
                                            bgVersion++
                                        }
                                    }
                                )
                            } else {
                                // 经典模式：字色切换 + 纹理选择 + 选图/恢复默认
                                ClassicBackgroundSheet(
                                    hasPhoto = photoBitmap != null,
                                    textureIndex = e.textureIndex,
                                    accent = accent,
                                    fontDarkPreview = fontDarkPreview,
                                    onFontDarkChange = { fontDarkPreview = it },
                                    onConfirmFontDark = { scope.launch { repository.save(e.copy(fontDark = it)) } },
                                    onPickPhoto = {
                                        showBackgroundSheet = false
                                        scope.launch {
                                            repository.save(e.copy(textureIndex = -1))
                                            onEditPhoto()
                                        }
                                    },
                                    onResetPhoto = {
                                        showBackgroundSheet = false
                                        scope.launch {
                                            EventImageStore.clear(context, e.id)
                                            clearBlurCache(e.id)
                                            repository.save(e.copy(textureIndex = -1))
                                            bgVersion++
                                        }
                                    },
                                    onTextureClick = { idx ->
                                        showBackgroundSheet = false
                                        scope.launch {
                                            repository.save(e.copy(textureIndex = idx))
                                            bgVersion++
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        GlassCapsule(
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Spacing.l)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Spacing.l),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionItem(Icons.Default.IosShare, "分享") { shareCard() }
                ActionItem(Icons.Default.SaveAlt, "存为图片") { saveToGallery() }
                ActionItem(Icons.Default.Texture, "背景") { showBackgroundSheet = true }
                ActionItem(
                    if (e.highlighted) Icons.Filled.Flag else Icons.Outlined.Flag,
                    "高亮"
                ) {
                    scope.launch { repository.save(e.copy(highlighted = !e.highlighted)) }
                }
                ActionItem(Icons.Default.Add, "新建") { onCreate() }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = BottomBarContentInset)
        )
    }
}