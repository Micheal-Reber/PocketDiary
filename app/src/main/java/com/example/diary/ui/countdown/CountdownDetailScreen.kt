package com.example.diary.ui.countdown

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.io.File
import java.time.LocalDate

/**
 * 倒数日详情页：纹理/照片背景 + 超大数字 + 日期脚注 + 动作栏
 * （分享 / 存为图片 / 背景 / 高亮旗标 / 新建），左下相机快捷换背景图。
 */

// ── Helper composables (defined BEFORE main function so they're in scope) ──

@Composable
private fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

/** 照片卡片内容：3:2 圆角卡 + 模糊背景 + 实时滑杆预览 + 黑白字切换。
 * 无图时：Header(事件色) + Body(TextureLibrary 纹理/渐变) + Footer 三段式框。 */
@Composable
private fun PhotoCardContent(
    photoBitmap: ImageBitmap?,
    blurRadius: Int,
    fontDark: Boolean,
    eventName: String,
    stateLabel: String,
    bigNumber: String,
    accent: androidx.compose.ui.graphics.Color,
    dateLine: String,
    endDate: String?,
    time: String?,
    onBlurChange: (Int) -> Unit,
    onFontDarkChange: (Boolean) -> Unit,
    onConfirmBlur: (Int) -> Unit,
    onConfirmFontDark: (Boolean) -> Unit,
    textureIndex: Int = -1
) {
    val textColor = if (fontDark) Color.Black else Color.White
    val scrimAlpha = if (fontDark) 0.15f else 0.25f // 黑字时浅一点，白字时深一点保对比
    val placeholderBg = if (fontDark) Color(0xFFF2F2F2) else Color(0xFF1E1E1E)
    val footerBg = if (fontDark) Color(0xFFEAEAEA) else Color(0xFF2B2B2E)
    val dateColor = if (fontDark) Color(0xFF555555) else Color(0xFFCCCCCC)
    val extraColor = if (fontDark) Color(0xFF777777) else Color(0xFFAAAAAA)

    // 外层全屏容器：纹理始终作为全屏背景，照片仅在卡片内部显示
    Box(Modifier.fillMaxSize()) {
        // ── 外层背景层：纹理始终渲染，照片卡模式下从卡片四周透出 ──
        when {
            textureIndex in 0 until TEXTURE_COUNT -> TextureBackdrop(textureIndex, accent)
            else -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            )
        }
        // 有图时加一层薄 scrim 提升卡片文字对比度（卡片外的纹理不受影响）
        if (photoBitmap != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
        }

        // ── 卡片层：92% 宽，1.38 比例，居中 ──
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1.38f)
                    .padding(vertical = Spacing.l)
            ) {
                if (photoBitmap != null) {
                    // 有图：全铺横图 + 模糊 + scrim，文字居中叠加
                    Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)) {
                        BlurCardImage(
                            bitmap = photoBitmap,
                            radiusDp = blurRadius,
                            eventId = 0,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
                        Column(
                            Modifier.fillMaxSize().padding(Spacing.l),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(eventName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = textColor, maxLines = 1)
                            Spacer(Modifier.height(Spacing.s))
                            Text(stateLabel, style = MaterialTheme.typography.titleMedium, color = textColor.copy(alpha = 0.9f))
                            Spacer(Modifier.height(Spacing.m))
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                                Text(bigNumber, fontSize = 82.sp, lineHeight = 94.sp, fontWeight = FontWeight.Black, color = textColor, textAlign = TextAlign.Center, maxLines = 1)
                            }
                            if (bigNumber != "今") {
                                Text("天", style = MaterialTheme.typography.titleLarge, color = textColor.copy(alpha = 0.8f))
                            }
                            Spacer(Modifier.height(Spacing.l))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dateLine, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = textColor.copy(alpha = 0.85f))
                                if (!endDate.isNullOrBlank()) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text("结束 ${endDate}", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.75f))
                                }
                                if (!time.isNullOrBlank()) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(time!!, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.75f))
                                }
                            }
                        }
                    }
                } else {
                    // 无图：三段式框，Body 改为半透明（外层纹理透出）
                    Column(Modifier.fillMaxSize()) {
                        // Header - 事件色
                        Box(
                            Modifier.fillMaxWidth().height(56.dp).background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(eventName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Spacing.l))
                        }
                        // Body - 半透明承载文字（纹理在外层全屏）
                        Box(
                            Modifier.weight(1f).fillMaxWidth()
                                .background(placeholderBg.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(stateLabel, style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.85f))
                                Spacer(Modifier.height(Spacing.s))
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)) {
                                    Text(bigNumber, fontSize = 82.sp, lineHeight = 94.sp, fontWeight = FontWeight.Black, color = textColor, textAlign = TextAlign.Center, maxLines = 1)
                                }
                                if (bigNumber != "今") {
                                    Text("天", style = MaterialTheme.typography.titleSmall, color = textColor.copy(alpha = 0.75f))
                                }
                            }
                        }
                        // Footer - 日期
                        Box(
                            Modifier.fillMaxWidth().height(48.dp).background(footerBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dateLine, style = MaterialTheme.typography.bodyMedium, color = dateColor, textAlign = TextAlign.Center)
                                if (!endDate.isNullOrBlank() || !time.isNullOrBlank()) {
                                    val extra = buildList {
                                        if (!endDate.isNullOrBlank()) add("结束 ${endDate}")
                                        if (!time.isNullOrBlank()) add(time!!)
                                    }.joinToString(" · ")
                                    Text(extra, style = MaterialTheme.typography.bodySmall, color = extraColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 照片卡模式背景底表：模糊滑杆 + 字色切换 + 选图/恢复默认。
 * 无图时额外显示纹理选择器（复用 TexturePickerRow）。 */
@Composable
private fun PhotoCardBackgroundSheet(
    blurRadiusPreview: Int,
    onBlurChange: (Int) -> Unit,
    onConfirmBlur: (Int) -> Unit,
    fontDarkPreview: Boolean,
    onFontDarkChange: (Boolean) -> Unit,
    onConfirmFontDark: (Boolean) -> Unit,
    hasPhoto: Boolean,
    textureIndex: Int,
    onTextureClick: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onResetPhoto: () -> Unit
) {
    Column {
        // 模糊滑杆（实时预览，松手确认）
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.l)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("背景模糊")
                Text("${blurRadiusPreview}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            androidx.compose.material3.Slider(
                value = blurRadiusPreview.toFloat(),
                onValueChange = { value -> onBlurChange(value.roundToInt()) },
                onValueChangeFinished = { onConfirmBlur(blurRadiusPreview) },
                valueRange = 0f..25f,
                steps = 25,
                modifier = Modifier.fillMaxWidth()
            )
            Text("拖动调整背景模糊强度（0 = 无模糊，25 = 强模糊）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 字色切换
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l)
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
        Text(if (fontDarkPreview) "黑字（适合浅色照片）" else "白字（适合深色照片）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l)
        )

        // 内置纹理选择器：无论是否有照片都显示，纹理与照片是独立图层
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
        Spacer(Modifier.height(Spacing.l))

        // 选图 / 恢复默认
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasPhoto) "更换背景图" else "选择背景图")
            if (hasPhoto) {
                TextButton(onClick = onResetPhoto) { Text("恢复默认") }
            }
            OutlinedButton(onClick = onPickPhoto) {
                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.padding(end = Spacing.xs))
                Text("相册")
            }
        }
    }
}

/** 经典模式背景底表：字色切换 + 纹理选择 + 选图/恢复默认。 */
@Composable
private fun ClassicBackgroundSheet(
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

// ── Main screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownDetailScreen(
    eventId: Long,
    repository: CountdownRepository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCreate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val event by repository.observeById(eventId).collectAsState(initial = null)
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

    val photoBitmap by produceState<ImageBitmap?>(
        initialValue = null, key1 = e.id, key2 = bgVersion
    ) {
        value = withContext(Dispatchers.IO) {
            val f = EventImageStore.file(context, e.id)
            if (f.exists()) BackgroundImageStore.decode(f.absolutePath, maxDim = 1400)
            else null
        }
    }

    fun bigNumberText(): String = when (state) {
        is CountState.Today -> "今"
        is CountState.Countdown -> "${state.days}"
        is CountState.Countup -> "${state.days}"
    }

    suspend fun renderCard(): File? {
        val foot = buildList {
            add("${e.date} · ${weekdayLabel(anchor)}")
            if (!e.endDate.isNullOrBlank()) add("结束 ${e.endDate}")
            if (!e.time.isNullOrBlank()) add(e.time)
        }
        val bmp = if (e.cardStyle == CountdownEvent.CARD_STYLE_PHOTO_CARD) {
            ShareCardRenderer.renderPhotoCard(
                context = context,
                eventId = e.id,
                eventName = e.name,
                accentArgb = accent.toArgb(),
                headline = stateLabel(state),
                bigNumber = bigNumberText(),
                unit = if (state is CountState.Today) "" else "天",
                footLines = foot,
                blurRadius = e.blurRadius,
                fontDark = e.fontDark
            )
        } else {
            ShareCardRenderer.render(
                name = e.name,
                accentArgb = accent.toArgb(),
                headline = stateLabel(state),
                bigNumber = bigNumberText(),
                unit = if (state is CountState.Today) "" else "天",
                footLines = foot,
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

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                EventImageStore.importFromUri(context, uri, e.id)
                bgVersion++
            }
        }
    }

    // 照片卡专用：实时模糊预览状态（滑杆拖动时更新，松手写库）
    var blurRadiusPreview by remember { mutableStateOf(e.blurRadius) }
    var fontDarkPreview by remember { mutableStateOf(e.fontDark) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
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
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.s),
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
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── 分支渲染：CLASSIC / PHOTO_CARD（照片卡无图也保留框）──
            if (e.cardStyle == CountdownEvent.CARD_STYLE_PHOTO_CARD) {
                // ===== 照片卡片：3:2 圆角框，有图则用图+模糊，无图用 TextureLibrary 纹理 =====
                PhotoCardContent(
                    photoBitmap = photoBitmap,
                    blurRadius = blurRadiusPreview,
                    fontDark = fontDarkPreview,
                    eventName = e.name,
                    stateLabel = stateLabel(state),
                    bigNumber = bigNumberText(),
                    accent = accent,
                    dateLine = "${e.date} · ${weekdayLabel(anchor)}",
                    endDate = e.endDate,
                    time = e.time,
                    onBlurChange = { blurRadiusPreview = it },
                    onFontDarkChange = { fontDarkPreview = it },
                    onConfirmBlur = { scope.launch { repository.save(e.copy(blurRadius = it)) } },
                    onConfirmFontDark = { scope.launch { repository.save(e.copy(fontDark = it)) } },
                    textureIndex = e.textureIndex
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
                                blurRadiusPreview = blurRadiusPreview,
                                onBlurChange = { blurRadiusPreview = it },
                                onConfirmBlur = { scope.launch { repository.save(e.copy(blurRadius = it)) } },
                                fontDarkPreview = fontDarkPreview,
                                onFontDarkChange = { fontDarkPreview = it },
                                onConfirmFontDark = { scope.launch { repository.save(e.copy(fontDark = it)) } },
                                hasPhoto = photoBitmap != null,
                                textureIndex = e.textureIndex,
                                onTextureClick = { idx ->
                                    showBackgroundSheet = false
                                    scope.launch {
                                        repository.save(e.copy(textureIndex = idx))
                                        bgVersion++
                                    }
                                },
                                onPickPhoto = {
                                    showBackgroundSheet = false
                                    pickPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onResetPhoto = {
                                    showBackgroundSheet = false
                                    scope.launch {
                                        EventImageStore.clear(context, e.id)
                                        repository.save(e.copy(textureIndex = -1))
                                        bgVersion++
                                    }
                                }
                            )
                        } else {
                            // 经典模式：字色切换 + 纹理选择 + 选图/恢复默认
                            ClassicBackgroundSheet(
                                textureIndex = e.textureIndex,
                                accent = accent,
                                fontDarkPreview = fontDarkPreview,
                                onFontDarkChange = { fontDarkPreview = it },
                                onConfirmFontDark = { scope.launch { repository.save(e.copy(fontDark = it)) } },
                                onPickPhoto = {
                                    showBackgroundSheet = false
                                    pickPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onResetPhoto = {
                                    showBackgroundSheet = false
                                    scope.launch {
                                        EventImageStore.clear(context, e.id)
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
}