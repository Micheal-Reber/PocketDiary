package com.example.diary.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.location.LocationProvider
import com.example.diary.data.photo.DiaryPhotoStore
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.repository.DiaryRepository
import com.example.diary.data.repository.SaveResult
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Mood & weather presets (inspired by MyDiary → localized)
private val moodPresets = listOf("😊", "😐", "😢", "😡", "😰")
private val moodLabels = listOf("开心", "一般", "难过", "生气", "焦虑")
private val weatherPresets = listOf("☀️", "🌤️", "☁️", "🌧️", "⛈️", "❄️", "🌫️")
private val weatherLabels = listOf("晴", "多云", "阴", "雨", "暴雨", "雪", "雾")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditorScreen(
    initialDate: String?,
    diaryRepository: DiaryRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    // All form fields use rememberSaveable so a config change (rotation, dark
    // mode toggle from another surface, etc.) preserves the user's in-flight
    // edits. Without this, rotating mid-edit would wipe title/content/mood.
    var dateStr by rememberSaveable { mutableStateOf(initialDate ?: today) }
    var content by rememberSaveable { mutableStateOf("") }
    var existingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // 改日期 = 切换到目标日期那篇日记（一天一篇）：暂存目标日期并弹确认框，
    // 防止当前未保存修改被静默丢弃。
    var pendingNewDate by rememberSaveable { mutableStateOf("") }
    var showDateSwitchConfirmDialog by rememberSaveable { mutableStateOf(false) }
    // Markdown preview toggle — editing is raw text (markdown IS text);
    // preview renders the subset renderer. The choice PERSISTS across editor
    // sessions via DataStore: turn preview off, exit, come back → still off.
    // Seeded with a one-shot sync read (same pattern as MainActivity's theme
    // read) so the first frame already shows the remembered state, no flash.
    val themePreferences = remember { ThemePreferences(context) }
    var showPreview by remember {
        mutableStateOf(runBlocking { themePreferences.editorPreview.first() })
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // Tracks the entry as it was last loaded/saved, so we can detect unsaved
    // edits when the user backs out without saving.
    var loadedSnapshot by rememberSaveable(stateSaver = SnapshotSaver) {
        mutableStateOf(Snapshot(dateStr, "", null, null, null, null, null))
    }
    // True only after the initial load completes. Without this, the first frame
    // would compare fresh fields against the empty initial Snapshot and falsely
    // flag the editor as dirty — popping a "discard changes?" dialog the moment
    // the user opens an existing entry.
    var isLoaded by rememberSaveable { mutableStateOf(false) }
    // Which date's DB row these fields were loaded from / last saved under.
    // Non-null after the first load; used to skip reload on recreation and to
    // revert the shown date when a move collides with an existing entry.
    var loadedForDate by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscardChangesDialog by rememberSaveable { mutableStateOf(false) }

    // Tracks the in-flight location request: tap the location button once to
    // start, tap again while the spinner shows to cancel.
    val locationJob = remember { mutableStateOf<Job?>(null) }
    var isLocating by rememberSaveable { mutableStateOf(false) }

    // Diary photos: markers in content reference file names; files live in
    // tmp/ until the entry is saved (then baked into the entry folder).
    val photoNames = remember(content) { DiaryPhotoStore.photoNamesIn(content) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isImportingPhoto by remember { mutableStateOf(false) }
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isImportingPhoto = true
            scope.launch {
                try {
                    DiaryPhotoStore.importFromUri(context, uri)?.let { name ->
                        content = (if (content.endsWith("\n")) content else "$content\n") + DiaryPhotoStore.markerOf(name)
                    } ?: run {
                        snackbarHostState.showSnackbar("图片导入失败", duration = SnackbarDuration.Short)
                    }
                } finally {
                    isImportingPhoto = false
                }
            }
        }
    }

    // Metadata fields
    var mood by rememberSaveable { mutableStateOf<String?>(null) }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lon by rememberSaveable { mutableStateOf<Double?>(null) }
    var locationName by rememberSaveable { mutableStateOf<String?>(null) }
    var weather by rememberSaveable { mutableStateOf<String?>(null) }

    // Load per date: re-dating switches the editor to that day's entry (one
    // diary per day) instead of re-dating THIS row. Every date change reloads
    // the target day — existing entry → edit it, none → blank new. Equal
    // [loadedForDate] after process restore means fields were already restored
    // from rememberSaveable — skip the reload.
    LaunchedEffect(dateStr) {
        if (loadedForDate == dateStr) return@LaunchedEffect
        isLoaded = false
        val entry = diaryRepository.getEntryByDate(dateStr)
        if (entry != null) {
            content = entry.content
            existingId = entry.id
            mood = entry.mood
            lat = entry.latitude
            lon = entry.longitude
            locationName = entry.locationName
            weather = entry.weather
        } else {
            content = ""
            existingId = null
            mood = null
            lat = null
            lon = null
            locationName = null
            weather = null
        }
        loadedSnapshot = Snapshot(dateStr, content, mood, lat, lon, locationName, weather)
        loadedForDate = dateStr
        isLoaded = true
    }

    // True when the editor has changes that differ from the last loaded/saved
    // snapshot. Date counts too: re-dating without saving IS an unsaved change,
    // so backing out still prompts instead of silently dropping the move.
    val isDirty = isLoaded && !loadedSnapshot.matches(dateStr, content, mood, lat, lon, locationName, weather)

    // Location fetch — GPS only (weather is manual via preset chips).
    // GPS fix + reverse geocoding are blocking I/O (Geocoder does a network
    // round-trip) — run on Dispatchers.IO so the UI never freezes, then apply
    // results on the main thread. finally resets the spinner state on every
    // exit path (completion, error, cancellation).
    val fetchLocationAndWeather: () -> Unit = {
        locationJob.value?.cancel()
        isLocating = true
        @Suppress("MissingPermission")
        locationJob.value = scope.launch {
            try {
                val provider = LocationProvider(context)
                val loc = withContext(Dispatchers.IO) { provider.getLastKnown() }
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    // Reverse geocode to human-readable address
                    try {
                        val addresses = withContext(Dispatchers.IO) {
                            @Suppress("DEPRECATION")
                            android.location.Geocoder(context)
                                .getFromLocation(loc.latitude, loc.longitude, 1)
                        }
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            locationName = listOfNotNull(addr.adminArea, addr.locality, addr.subLocality)
                                .distinct().joinToString(" ")
                                .ifEmpty { addr.getAddressLine(0) ?: "" }
                        }
                    } catch (e: Exception) {
                        locationName = "%.4f, %.4f".format(loc.latitude, loc.longitude)
                    }
                } else {
                    snackbarHostState.showSnackbar(
                        message = "暂未获取到位置,请稍后再试或检查定位权限",
                        duration = SnackbarDuration.Short
                    )
                }
            } finally {
                isLocating = false
            }
        }
    }

    // Hardware back with unsaved changes → prompt before discarding.
    BackHandler(enabled = isDirty) {
        showDiscardChangesDialog = true
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            fetchLocationAndWeather()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "需要位置权限才能获取位置和天气",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // Location button is a three-state TOGGLE:
    //   1. spinner showing → tap cancels the in-flight request
    //   2. location already set → tap REMOVES it (lat/lon/address cleared)
    //   3. nothing set → tap fetches (permission flow first if needed)
    val toggleLocation: () -> Unit = {
        when {
            isLocating -> {
                locationJob.value?.cancel()
                locationJob.value = null
                isLocating = false
            }
            lat != null || lon != null || locationName != null -> {
                lat = null
                lon = null
                locationName = null
            }
            else -> {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    fetchLocationAndWeather()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        }
    }

    // Save → move/insert per repository semantics. On date conflict keep every
    // edit, roll the shown date back to the row's real date, and tell the user.
    val saveEntryAction: () -> Unit = {
        scope.launch {
            val entry = DiaryEntry(
                id = existingId ?: 0,
                content = content.trim(),
                date = dateStr,
                mood = mood,
                latitude = lat,
                longitude = lon,
                locationName = locationName,
                weather = weather
            )
            try {
                when (val result = diaryRepository.saveEntry(entry)) {
                    is SaveResult.Success -> {
                        existingId = result.id
                        // Two-phase photo lifecycle (MyDiary-style): migrate
                        // tmp picks into the entry's own folder now that the
                        // row id exists. Markers reference names only, so the
                        // move never breaks them.
                        DiaryPhotoStore.bakeTmp(context, result.id)
                        loadedForDate = dateStr
                        // Sync snapshot (raw values, not trimmed) so isDirty
                        // stays consistent with save-time trim.
                        loadedSnapshot = Snapshot(
                            dateStr, content, mood, lat, lon,
                            locationName, weather
                        )
                        onBack()
                    }
                    SaveResult.DateConflict -> {
                        dateStr = loadedForDate ?: dateStr
                        snackbarHostState.showSnackbar(
                            message = "该日期已有一篇日记",
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    message = "保存失败,请重试",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日记", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) {
                            showDiscardChangesDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showPreview = !showPreview
                        scope.launch { themePreferences.setEditorPreview(showPreview) }
                    }) {
                        Icon(
                            if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPreview) "切换到编辑" else "预览"
                        )
                    }
                    if (existingId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "删除整篇", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(dateStr, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        pickPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isImportingPhoto && photoNames.size < DiaryPhotoStore.MAX_PHOTOS_PER_ENTRY
                ) {
                    if (isImportingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "插入图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Spinner while locating — tap the button again to cancel.
                IconButton(onClick = toggleLocation) {
                    if (isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "获取位置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledIconButton(onClick = saveEntryAction) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "保存",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))

            // Mood selector (scrollable like weather)
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                moodPresets.forEachIndexed { index, icon ->
                    val selected = mood == icon
                    FilterChip(
                        selected = selected,
                        onClick = { mood = if (selected) null else icon },
                        label = { Text("$icon ${moodLabels[index]}", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Weather selector
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                weatherPresets.forEachIndexed { index, icon ->
                    val selected = weather == icon
                    FilterChip(
                        selected = selected,
                        onClick = { weather = if (selected) null else icon },
                        label = { Text("$icon ${weatherLabels[index]}", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Location / weather row (only shown when we have a fix)
            if (lat != null && lon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        locationName ?: "${lat}, ${lon}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    weather?.let { w ->
                        Spacer(Modifier.width(6.dp))
                        Text(w, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (showPreview) {
                // Rendered view of the same content — Markdown text segments
                // interleaved with photo blocks at their [img:…] markers.
                // Chips and date stay editable above; only the body is read.
                if (content.isBlank()) {
                    Text(
                        "（暂无内容）",
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                    )
                } else {
                    DiaryContentView(
                        content = content,
                        entryId = existingId,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp)
                    )
                }
            } else {
                // Borderless writing canvas — journal apps favor an unobstructed
                // page over a boxed field.
                TextField(
                value = content,
                onValueChange = { content = it },
                placeholder = {
                    Text("写下今天的心情...", color = MaterialTheme.colorScheme.outline)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

                // Inserted-photo thumbnail strip (edit mode only — preview
                // renders them inline).
                if (photoNames.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "已插入图片 ${photoNames.size}/${DiaryPhotoStore.MAX_PHOTOS_PER_ENTRY}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                    ) {
                        items(photoNames) { name ->
                            val thumb by produceState<ImageBitmap?>(initialValue = null, name) {
                                val f = DiaryPhotoStore.resolve(context, existingId, name)
                                value = f?.let { BackgroundImageStore.decode(it.absolutePath, maxDim = 400) }
                            }
                            Box(
                                Modifier
                                    .size(88.dp)
                                    .clip(MaterialTheme.shapes.small)
                            ) {
                                thumb?.let {
                                    Image(
                                        bitmap = it,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            DiaryPhotoStore.deletePhoto(context, existingId, name)
                                        }
                                        content = content.replaceFirst("[img:$name]", "")
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除图片",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                LocalDate.parse(dateStr).toEpochDay() * 86400000L
            } catch (e: Exception) { null }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val newDate = datePickerState.selectedDateMillis?.let { millis ->
                        // selectedDateMillis from DatePicker is in UTC; using systemDefault()
                        // can shift the date by ±1 day across DST or far-from-UTC timezones.
                        java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                    if (newDate != null && newDate != dateStr) {
                        // 改日期 = 切换编辑对象到目标日期那篇日记。有未保存修改时
                        // 先弹确认框，防止切换导致当前草稿被静默丢弃。
                        if (isDirty) {
                            pendingNewDate = newDate
                            showDateSwitchConfirmDialog = true
                        } else {
                            dateStr = newDate
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除日记") },
            text = { Text("确定要删除这篇日记吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            existingId?.let { id ->
                                diaryRepository.deleteEntry(id)
                                // Cascade: the entry's photo folder goes with it.
                                DiaryPhotoStore.deleteEntryDir(context, id)
                            }
                            showDeleteDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDateSwitchConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDateSwitchConfirmDialog = false },
            title = { Text("切换日期？") },
            text = { Text("当前修改尚未保存,切换日期后这些修改将丢失。是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showDateSwitchConfirmDialog = false
                    dateStr = pendingNewDate
                    pendingNewDate = ""
                    // 被丢弃的草稿若含 tmp 照片,已成孤儿,一并清掉
                    scope.launch { DiaryPhotoStore.clearTmp(context) }
                }) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { showDateSwitchConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDiscardChangesDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardChangesDialog = false },
            title = { Text("放弃编辑？") },
            text = { Text("当前修改尚未保存,是否放弃并退出？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardChangesDialog = false
                        // Unsaved exit: tmp photo picks are orphans — wipe them.
                        scope.launch { DiaryPhotoStore.clearTmp(context) }
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardChangesDialog = false }) {
                    Text("继续编辑")
                }
            }
        )
    }
}

/**
 * Snapshot of the editor fields used to detect unsaved changes when the user
 * navigates away (back press). Includes the date: re-dating an entry without
 * saving is itself an unsaved change worth protecting.
 */
private data class Snapshot(
    val date: String,
    val content: String,
    val mood: String?,
    val lat: Double?,
    val lon: Double?,
    val locationName: String?,
    val weather: String?
) {
    fun matches(
        date: String, content: String, mood: String?, lat: Double?, lon: Double?,
        locationName: String?, weather: String?
    ): Boolean =
        this.date == date &&
        this.content == content &&
        this.mood == mood &&
        this.lat == lat &&
        this.lon == lon &&
        this.locationName == locationName &&
        this.weather == weather
}

private val SnapshotSaver: Saver<Snapshot, *> = listSaver(
    save = { snap ->
        listOf(
            snap.date,
            snap.content,
            snap.mood,
            snap.lat,
            snap.lon,
            snap.locationName,
            snap.weather
        )
    },
    restore = { raw ->
        Snapshot(
            raw[0] as String,
            raw[1] as String,
            raw[2] as String?,
            raw[3] as Double?,
            raw[4] as Double?,
            raw[5] as String?,
            raw[6] as String?
        )
    }
)
