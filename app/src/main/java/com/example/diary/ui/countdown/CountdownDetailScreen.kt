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
import java.io.File
import java.time.LocalDate

/**
 * 倒数日详情页：纹理/照片背景 + 超大数字 + 日期脚注 + 动作栏
 * （分享 / 存为图片 / 背景 / 高亮旗标 / 新建），左下相机快捷换背景图。
 */
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

    fun renderCard(): File? {
        val foot = buildList {
            add("${e.date} · ${weekdayLabel(anchor)}")
            if (!e.endDate.isNullOrBlank()) add("结束 ${e.endDate}")
            if (!e.time.isNullOrBlank()) add(e.time)
        }
        val bmp = ShareCardRenderer.render(
            name = e.name,
            accentArgb = accent.toArgb(),
            headline = stateLabel(state),
            bigNumber = bigNumberText(),
            unit = if (state is CountState.Today) "" else "天",
            footLines = foot
        )
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
                // 换照片时退出纹理模式，让照片可见
                repository.save(e.copy(textureIndex = -1))
                bgVersion++
            }
        }
    }

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
                e.textureIndex in 0 until TEXTURE_COUNT -> TextureBackdrop(e.textureIndex, accent)
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
                    e.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.m))
                Text(
                    stateLabel(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xxl))
                Text(
                    bigNumberText(),
                    fontSize = 140.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (state is CountState.Today) "" else "天",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))

                Column(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${e.date} · ${weekdayLabel(anchor)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (!e.endDate.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text("结束 ${e.endDate}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!e.time.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(e.time!!, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

}

        if (showBackgroundSheet) {
            ModalBottomSheet(onDismissRequest = { showBackgroundSheet = false }) {
                Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.xxl)) {
                    ListItem(
                        headlineContent = { Text("从相册选择照片") },
                        leadingContent = { Icon(Icons.Default.PhotoCamera, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickableNoRipple {
                            showBackgroundSheet = false
                            pickPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    ListItem(
                        headlineContent = { Text("恢复默认（无背景）") },
                        leadingContent = { Icon(Icons.Default.Close, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickableNoRipple {
                            showBackgroundSheet = false
                            scope.launch {
                                EventImageStore.clear(context, e.id)
                                repository.save(e.copy(textureIndex = -1))
                                bgVersion++
                            }
                        }
                    )
                    Text(
                        "内置纹理",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        repeat(TEXTURE_COUNT) { idx ->
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(
                                        width = if (e.textureIndex == idx) 2.dp else 0.dp,
                                        color = if (e.textureIndex == idx) accent else Color.Transparent,
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .clickableNoRipple {
                                        showBackgroundSheet = false
                                        scope.launch {
                                            EventImageStore.clear(context, e.id)
                                            repository.save(e.copy(textureIndex = idx))
                                            bgVersion++
                                        }
                                    }
                            ) {
                                TextureBackdrop(idx, accent)
                                if (e.textureIndex == idx) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = accent,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs)
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
