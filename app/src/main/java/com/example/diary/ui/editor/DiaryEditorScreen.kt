package com.example.diary.ui.editor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.location.LocationProvider
import com.example.diary.data.photo.PhotoStore
import com.example.diary.data.repository.DiaryRepository
import com.example.diary.data.weather.WeatherProvider
import com.example.diary.ui.editor.mood.MoodChips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiaryEditorScreen(
    initialDate: String?,
    diaryRepository: DiaryRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoStore = remember { PhotoStore(context) }
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    // All form fields use rememberSaveable so a config change (rotation, dark
    // mode toggle from another surface, etc.) preserves the user's in-flight
    // edits. Without this, rotating mid-edit would wipe title/content/mood.
    var dateStr by rememberSaveable { mutableStateOf(initialDate ?: today) }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var existingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // Tracks the entry as it was last loaded/saved, so we can detect unsaved
    // edits when the user switches dates or backs out without saving.
    var loadedSnapshot by rememberSaveable(stateSaver = SnapshotSaver) {
        mutableStateOf(Snapshot("", "", null, null, null, null, null, emptyList()))
    }
    // True only after the initial load completes. Without this, the first frame
    // would compare fresh fields against the empty initial Snapshot and falsely
    // flag the editor as dirty — popping a "discard changes?" dialog the moment
    // the user opens an existing entry.
    var isLoaded by rememberSaveable { mutableStateOf(false) }
    // Which date the current fields were loaded from. Saved across config
    // changes so the LaunchedEffect below can tell "this is a fresh load
    // because dateStr changed" from "this is a reload because Activity
    // recreated" — the latter must NOT clobber the user's unsaved edits.
    var loadedForDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscardChangesDialog by rememberSaveable { mutableStateOf(false) }
    // True when the camera permission has been permanently denied — drives the
    // Snackbar that points users to system settings.
    //
    // Intentionally `remember`, NOT `rememberSaveable`: a recreated Composable
    // would otherwise re-display the snackbar on every rotation while the
    // permission is still permanently denied. The next "take photo" tap will
    // re-trigger the launcher callback and re-set this flag if still denied.
    var cameraPermanentlyDenied by remember { mutableStateOf(false) }

    // Tracks the in-flight location/weather request so a new tap can cancel
    // the previous one before it overwrites state with stale data.
    val locationJob = remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // New metadata fields
    var mood by rememberSaveable { mutableStateOf<String?>(null) }
    // Photos are saved as absolutePath strings — File itself isn't Parcelable.
    // On restore we re-File() each path and filter to .exists() so the user
    // doesn't see ghost thumbnails for files the OS has cleaned up.
    var photos by rememberSaveable(stateSaver = PhotosSaver) {
        mutableStateOf<List<File>>(emptyList())
    }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lon by rememberSaveable { mutableStateOf<Double?>(null) }
    var locationName by rememberSaveable { mutableStateOf<String?>(null) }
    var weather by rememberSaveable { mutableStateOf<String?>(null) }
    var weatherLoading by rememberSaveable { mutableStateOf(false) }

    // Photo selection mode (entered via long-press, exited via toolbar "cancel" or empty selection).
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedFiles by rememberSaveable(stateSaver = SelectedFilesSaver) {
        mutableStateOf<Set<File>>(emptySet())
    }

    // Auto-exit selection mode if we just deleted the last selected photo
    // (handled inline at the delete action).

    // Re-load entry every time the user picks a different date, so the editor
    // reflects the contents of the new date rather than stale data.
    //
    // Skip-on-equal: if `loadedForDate` already equals `dateStr`, this effect
    // is restarting because the Activity was recreated (e.g. rotation), not
    // because the user picked a new date. In that case the form fields were
    // already restored from rememberSaveable — re-running the DB load would
    // clobber any in-flight edits with the old persisted values. Without
    // this guard, rotating mid-edit silently reverts the user's changes.
    LaunchedEffect(dateStr) {
        if (loadedForDate == dateStr) {
            // Recreated Activity / composition restart — keep current state.
            return@LaunchedEffect
        }
        // Reset loaded flag for the duration of the (potentially async) reload,
        // so isDirty stays false during the in-between frame and we don't pop a
        // spurious discard-changes dialog.
        isLoaded = false
        val entry = diaryRepository.getEntryByDate(dateStr)
        // Always clear transient photo-selection state when navigating dates
        isSelectionMode = false
        selectedFiles = emptySet()
        if (entry != null) {
            title = entry.title
            content = entry.content
            existingId = entry.id
            mood = entry.mood
            lat = entry.latitude
            lon = entry.longitude
            locationName = entry.locationName
            weather = entry.weather
            // Filter out paths that point to no-longer-existing files (e.g. cleaned up
            // by OS or manually deleted). Only keep live files.
            photos = PhotoStore.parsePaths(entry.photoPaths)
                .map { File(it) }
                .filter { it.exists() }
        } else {
            // Date has no entry yet — reset to empty so user can start fresh.
            title = ""
            content = ""
            existingId = null
            mood = null
            lat = null
            lon = null
            locationName = null
            weather = null
            photos = emptyList()
        }
        // After loading, record this as the clean baseline so subsequent edits
        // can be detected as dirty and protected from accidental overwrite.
        loadedSnapshot = Snapshot(
            title, content, mood, lat, lon, locationName, weather, photos
        )
        loadedForDate = dateStr
        isLoaded = true
    }

    val pendingCameraFile = rememberSaveable(stateSaver = PendingFileSaver) {
        mutableStateOf<File?>(null)
    }

    // True when the editor has changes that differ from the last loaded/saved snapshot.
    // Gate on isLoaded so we don't pop a discard-changes dialog during the initial
    // async load (fields are non-empty while loadedSnapshot is still the placeholder).
    val isDirty = isLoaded && !loadedSnapshot.matches(title, content, mood, lat, lon, locationName, weather, photos)

    // Shared location+weather fetch, used by both the permission launcher callback
    // (after the user grants) and the toolbar button (when permission is already
    // granted — RequestMultiplePermissions would no-op otherwise).
    val fetchLocationAndWeather: () -> Unit = {
        // Best-effort: read last known and fetch weather for the entry date.
        // Cancel any in-flight request so the previous coroutine can't race
        // in and overwrite the new request's results.
        locationJob.value?.cancel()
        @Suppress("MissingPermission") // checked via hasPermission helper
        locationJob.value = scope.launch {
            val provider = LocationProvider(context)
            val loc = provider.getLastKnown()
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                locationName = "%.4f, %.4f".format(loc.latitude, loc.longitude)
                weatherLoading = true
                // Capture the date at request time so a date change mid-flight
                // doesn't cause the result to land in the wrong entry.
                val dateAtRequest = dateStr
                val parsedDate = try {
                    LocalDate.parse(dateAtRequest.trim())
                } catch (e: Exception) { LocalDate.now() }
                try {
                    val w = WeatherProvider.fetchEmoji(loc.latitude, loc.longitude, parsedDate)
                    // Only apply the result if the user is still on the same date.
                    if (dateStr == dateAtRequest) {
                        weather = w
                    }
                } finally {
                    // Always release the spinner — even if the coroutine was
                    // cancelled mid-flight (e.g., a new tap cancelled this one,
                    // or the screen disposed). Without this, weatherLoading stays
                    // true forever and the spinner spins indefinitely.
                    if (dateStr == dateAtRequest) {
                        weatherLoading = false
                    }
                }
            } else {
                // No fix yet — system might be cold, permission just granted, or
                // the device has no GPS/NETWORK/PASSIVE providers. Surface to the
                // user instead of silently no-oping, so the button doesn't feel
                // broken on first launch.
                snackbarHostState.showSnackbar(
                    message = "暂未获取到位置,请稍后再试或检查定位权限",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // Hardware Back in selection mode → exit selection. Otherwise, if there are
    // unsaved changes, prompt before discarding.
    BackHandler(enabled = isSelectionMode || isDirty) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedFiles = emptySet()
        } else if (isDirty) {
            showDiscardChangesDialog = true
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Copying a photo from the picker is real I/O; do it off the main thread.
            scope.launch {
                val file = withContext(Dispatchers.IO) { photoStore.importPicked(uri) }
                if (file != null) photos = photos + file
            }
        }
    }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile.value
        if (success && file != null && file.exists()) {
            photos = photos + file
        } else if (file != null) {
            // Either the user cancelled, the camera app refused, or the camera
            // reported success but never wrote to the placeholder (vendor bug).
            // In all three cases the file is no longer going into `photos` —
            // delete the empty stub so filesDir/photos/ doesn't accumulate
            // 0-byte artifacts.
            runCatching { file.delete() }
        }
        pendingCameraFile.value = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = photoStore.newCameraOutputUri()
            pendingCameraFile.value = file
            takePhotoLauncher.launch(uri)
        } else {
            // User denied. If we can't show the rationale, the user picked "Don't
            // ask again" — surface a Snackbar pointing to system settings.
            cameraPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                Manifest.permission.CAMERA
            )
        }
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

    LaunchedEffect(cameraPermanentlyDenied) {
        if (cameraPermanentlyDenied) {
            val result = snackbarHostState.showSnackbar(
                message = "相机权限已被禁用,请前往系统设置开启",
                actionLabel = "去设置",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                val intent = Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            // Reset so a re-launch (e.g. user denies a second time) triggers
            // a fresh effect. Without this, the second denial would re-set
            // the same true value, which is a no-op for `LaunchedEffect`'s
            // key, and the snackbar would never show again.
            cameraPermanentlyDenied = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) "已选 ${selectedFiles.size} 张" else "日记",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedFiles = emptySet()
                        } else if (isDirty) {
                            // No pending date — popBackStack via the discard dialog.
                            pendingDate = null
                            showDiscardChangesDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSelectionMode) "取消选择" else "返回"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        TextButton(
                            onClick = {
                                // Order matters here: persist the new photoPaths CSV
                                // BEFORE physically deleting the on-disk files. If we
                                // delete first and the save throws, the on-disk file
                                // is gone but photoPaths still points to it — a
                                // dangling reference. With save-then-delete, a save
                                // failure leaves the files in place (the user can
                                // retry) and a delete failure just leaves orphan
                                // bytes (the next load filters them via .exists()).
                                val toDelete = selectedFiles
                                val kept = photos.filterNot { it in toDelete }
                                scope.launch {
                                    val csv = kept.joinToString(";") { it.absolutePath }
                                    val savedId = diaryRepository.saveEntry(
                                        DiaryEntry(
                                            id = existingId ?: 0,
                                            title = title.trim(),
                                            content = content.trim(),
                                            date = dateStr,
                                            mood = mood,
                                            latitude = lat,
                                            longitude = lon,
                                            locationName = locationName,
                                            weather = weather,
                                            photoPaths = csv
                                        )
                                    )
                                    // Disk delete only after the DB row no longer
                                    // references these paths.
                                    withContext(Dispatchers.IO) {
                                        toDelete.forEach { runCatching { it.delete() } }
                                    }
                                    existingId = savedId
                                    photos = kept
                                    selectedFiles = emptySet()
                                    isSelectionMode = false
                                    // Reset the dirty baseline so a subsequent back-press
                                    // doesn't re-prompt for changes that are now persisted.
                                    loadedSnapshot = Snapshot(
                                        title, content, mood, lat, lon,
                                        locationName, weather, photos
                                    )
                                }
                            },
                            enabled = selectedFiles.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("删除") }
                    } else if (existingId != null) {
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
        bottomBar = {
            // Hide toolbar during photo-selection mode so user can't accidentally tap
            // "save" before committing/exiting the photo delete flow.
            if (!isSelectionMode) {
                EditorToolbar(
                    hasPhotos = photos.isNotEmpty(),
                    onPickPhoto = {
                        pickPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onTakePhoto = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val (file, uri) = photoStore.newCameraOutputUri()
                            pendingCameraFile.value = file
                            takePhotoLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onLocation = {
                        // If permission is already granted, RequestMultiplePermissions
                        // is a no-op (no callback fires) so we must trigger directly.
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
                    },
                    onSave = {
                        scope.launch {
                            val csv = photos.joinToString(";") { it.absolutePath }
                            val entry = DiaryEntry(
                                id = existingId ?: 0,
                                title = title.trim(),
                                content = content.trim(),
                                date = dateStr,
                                mood = mood,
                                latitude = lat,
                                longitude = lon,
                                locationName = locationName,
                                weather = weather,
                                photoPaths = csv
                            )
                            try {
                                // Capture the row id so subsequent operations (e.g. "delete
                                // entire entry" after a first save) can target the right row.
                                val savedId = diaryRepository.saveEntry(entry)
                                existingId = savedId
                                // After save, sync snapshot so subsequent "unsaved?" checks are clean.
                                // Use the raw state values (not trimmed) so isDirty comparison
                                // remains consistent — the trim only happens at save time.
                                loadedSnapshot = Snapshot(
                                    title, content, mood, lat, lon,
                                    locationName, weather, photos
                                )
                                onBack()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                // Composable was disposed while we were saving —
                                // structured concurrency requires us to propagate
                                // cancellation, not swallow it. The DB write either
                                // completed (in which case the entry is saved) or
                                // didn't (the in-flight suspend was cancelled), but
                                // either way there's no user-facing error to show.
                                throw e
                            } catch (e: Exception) {
                                // Don't leave the user stuck in the editor if the DB
                                // write fails (disk full, db locked, etc.). Surface
                                // the error and let them retry — their input is still
                                // in the form state, so nothing is lost. Long duration
                                // so the user has time to read it before it dismisses.
                                snackbarHostState.showSnackbar(
                                    message = "保存失败,请重试",
                                    duration = SnackbarDuration.Long
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showDatePicker = true }
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(dateStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))

            // Mood chips
            MoodChips(selected = mood, onSelect = { mood = it })
            Spacer(Modifier.height(4.dp))

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
                    if (weatherLoading) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else weather?.let { w ->
                        Spacer(Modifier.width(6.dp))
                        Text(w, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            Spacer(Modifier.height(12.dp))

            // Photo strip (when any photos attached)
            if (photos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(photos, key = { it.absolutePath }) { file ->
                        val isSelected = file in selectedFiles
                        val borderColor = if (isSelected)
                            MaterialTheme.colorScheme.error
                        else
                            Color.Transparent
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 2.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            // Tap = toggle. If unchecking the last selected photo,
                                            // exit selection mode (matches Photos / Gallery convention).
                                            if (isSelected) {
                                                val next = selectedFiles - file
                                                if (next.isEmpty()) {
                                                    isSelectionMode = false
                                                    selectedFiles = emptySet()
                                                } else {
                                                    selectedFiles = next
                                                }
                                            } else {
                                                selectedFiles = selectedFiles + file
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedFiles = setOf(file)
                                        } else {
                                            // Long-press any photo = make it the lone selection
                                            // (handy for picking a different photo without tapping).
                                            selectedFiles = setOf(file)
                                        }
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Selection-mode-only check overlay (subtle: top-left small circle).
                            if (isSelectionMode) {
                                val ringColor = if (isSelected) Color.Transparent
                                else Color.Black.copy(alpha = 0.5f)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .border(1.5.dp, ringColor, CircleShape)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected)
                                                MaterialTheme.colorScheme.error
                                            else
                                                Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选",
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("写下今天的心情...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
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
                        if (isDirty) {
                            // Defer the date change until the user confirms discarding.
                            pendingDate = newDate
                            showDiscardChangesDialog = true
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
                            // Read photoPaths from the DB before deleting so we
                            // delete exactly what the row referenced — not what
                            // the UI happens to render. This catches the case
                            // where the UI's `photos` state is stale.
                            val photoPaths = existingId
                                ?.let { diaryRepository.deleteEntryAndReturnPhotoPaths(it) }
                                ?: emptyList()
                            withContext(Dispatchers.IO) {
                                photoPaths.map(::File).forEach { runCatching { it.delete() } }
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

    if (showDiscardChangesDialog) {
        AlertDialog(
            // Dismissal paths (back press, scrim tap) intentionally preserve
            // pendingDate so the user's date choice survives a "dismiss then
            // re-decide" loop. Only the "放弃" (confirm) path consumes it.
            onDismissRequest = { showDiscardChangesDialog = false },
            title = { Text("放弃编辑？") },
            text = { Text("当前修改尚未保存,是否放弃并继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardChangesDialog = false
                        val target = pendingDate
                        pendingDate = null
                        // Files the user added or selected during this session
                        // that aren't in the persisted snapshot will never be
                        // referenced again once we throw the edits away. Clean
                        // them up so filesDir/photos/ doesn't accumulate
                        // orphans every time someone bails out of the editor.
                        val snapshotPaths = loadedSnapshot.photos.toSet()
                        val orphans = photos.filterNot { it in snapshotPaths }
                        if (orphans.isNotEmpty()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    orphans.forEach { runCatching { it.delete() } }
                                }
                            }
                        }
                        if (target != null) {
                            // Apply the pending date change.
                            dateStr = target
                        } else {
                            // No pending date → user came from Back; leave the screen.
                            onBack()
                        }
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

@Composable
private fun EditorToolbar(
    hasPhotos: Boolean,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onLocation: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPickPhoto) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "从相册选图")
            }
            IconButton(onClick = onTakePhoto) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "拍照")
            }
            IconButton(onClick = onLocation) {
                Icon(Icons.Default.LocationOn, contentDescription = "获取位置和天气")
            }
            FilledIconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "保存", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/**
 * Snapshot of the editor fields used to detect unsaved changes when the user
 * navigates away (date picker, back press).
 */
private data class Snapshot(
    val title: String,
    val content: String,
    val mood: String?,
    val lat: Double?,
    val lon: Double?,
    val locationName: String?,
    val weather: String?,
    val photos: List<File>
) {
    fun matches(
        title: String, content: String, mood: String?, lat: Double?, lon: Double?,
        locationName: String?, weather: String?, photos: List<File>
    ): Boolean =
        this.title == title &&
        this.content == content &&
        this.mood == mood &&
        this.lat == lat &&
        this.lon == lon &&
        this.locationName == locationName &&
        this.weather == weather &&
        // Order-insensitive photo comparison (UI may reorder; only set equality matters).
        this.photos.toSet() == photos.toSet()
}

// File is not Parcelable, so all three list/set states below go through
// savers that round-trip via absolutePath. On restore, files that no longer
// exist are dropped (parallels the live .exists() filter applied during
// normal loads — see LaunchedEffect(dateStr) in DiaryEditorScreen).

private val PhotosSaver: Saver<List<File>, *> = listSaver(
    save = { it.map { f -> f.absolutePath } },
    restore = { paths ->
        paths.map(::File).filter { it.exists() }
    }
)

private val PendingFileSaver: Saver<File?, *> = Saver(
    save = { it?.absolutePath ?: "" },
    restore = { raw ->
        val path = raw as String
        if (path.isEmpty() || !File(path).exists()) null else File(path)
    }
)

private val SelectedFilesSaver: Saver<Set<File>, *> = Saver(
    save = { it.map { f -> f.absolutePath } },
    restore = { paths ->
        (paths as List<String>).map(::File).filter { it.exists() }.toSet()
    }
)

private val SnapshotSaver: Saver<Snapshot, *> = listSaver(
    save = { snap ->
        listOf(
            snap.title,
            snap.content,
            snap.mood,
            snap.lat,
            snap.lon,
            snap.locationName,
            snap.weather,
            snap.photos.map { it.absolutePath }
        )
    },
    restore = { raw ->
        @Suppress("UNCHECKED_CAST")
        val title = raw[0] as String
        @Suppress("UNCHECKED_CAST")
        val content = raw[1] as String
        val mood = raw[2] as String?
        val lat = raw[3] as Double?
        val lon = raw[4] as Double?
        val locationName = raw[5] as String?
        val weather = raw[6] as String?
        @Suppress("UNCHECKED_CAST")
        val photoPaths = (raw[7] as List<String>).map(::File).filter { it.exists() }
        Snapshot(title, content, mood, lat, lon, locationName, weather, photoPaths)
    }
)
