package com.example.diary.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.location.LocationProvider
import com.example.diary.data.repository.DiaryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // Tracks the entry as it was last loaded/saved, so we can detect unsaved
    // edits when the user switches dates or backs out without saving.
    var loadedSnapshot by rememberSaveable(stateSaver = SnapshotSaver) {
        mutableStateOf(Snapshot("", null, null, null, null, null))
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

    // Tracks the in-flight location request so a new tap can cancel
    // the previous one before it overwrites state with stale data.
    val locationJob = remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // New metadata fields
    var mood by rememberSaveable { mutableStateOf<String?>(null) }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lon by rememberSaveable { mutableStateOf<Double?>(null) }
    var locationName by rememberSaveable { mutableStateOf<String?>(null) }
    var weather by rememberSaveable { mutableStateOf<String?>(null) }
    var weatherLoading by rememberSaveable { mutableStateOf(false) }

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
        if (entry != null) {
            content = entry.content
            existingId = entry.id
            mood = entry.mood
            lat = entry.latitude
            lon = entry.longitude
            locationName = entry.locationName
            weather = entry.weather
        } else {
            // Date has no entry yet — reset to empty so user can start fresh.
            content = ""
            existingId = null
            mood = null
            lat = null
            lon = null
            locationName = null
            weather = null
        }
        // After loading, record this as the clean baseline so subsequent edits
        // can be detected as dirty and protected from accidental overwrite.
        loadedSnapshot = Snapshot(
            content, mood, lat, lon, locationName, weather
        )
        loadedForDate = dateStr
        isLoaded = true
    }

    // True when the editor has changes that differ from the last loaded/saved snapshot.
    // Gate on isLoaded so we don't pop a discard-changes dialog during the initial
    // async load (fields are non-empty while loadedSnapshot is still the placeholder).
    val isDirty = isLoaded && !loadedSnapshot.matches(content, mood, lat, lon, locationName, weather)

    // Location fetch — GPS only (weather is now manual via preset chips)
    val fetchLocationAndWeather: () -> Unit = {
        locationJob.value?.cancel()
        @Suppress("MissingPermission")
        locationJob.value = scope.launch {
            val provider = LocationProvider(context)
            val loc = provider.getLastKnown()
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                // Reverse geocode to human-readable address
                try {
                    val geocoder = android.location.Geocoder(context)
                    val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
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
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "暂未获取到位置,请稍后再试或检查定位权限",
                        duration = SnackbarDuration.Short
                    )
                }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日记", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) {
                            // No pending date — popBackStack via the discard dialog.
                            pendingDate = null
                            showDiscardChangesDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
        bottomBar = {
            EditorToolbar(
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
                            // Capture the row id so subsequent operations (e.g. "delete
                            // entire entry" after a first save) can target the right row.
                            val savedId = diaryRepository.saveEntry(entry)
                            existingId = savedId
                            // After save, sync snapshot so subsequent "unsaved?" checks are clean.
                            // Use the raw state values (not trimmed) so isDirty comparison
                            // remains consistent — the trim only happens at save time.
                            loadedSnapshot = Snapshot(
                                content, mood, lat, lon,
                                locationName, weather
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

            // Mood selector (scrollable like weather)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            Spacer(Modifier.height(4.dp))

            // Weather selector
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            existingId?.let { diaryRepository.deleteEntry(it) }
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
    val content: String,
    val mood: String?,
    val lat: Double?,
    val lon: Double?,
    val locationName: String?,
    val weather: String?
) {
    fun matches(
        content: String, mood: String?, lat: Double?, lon: Double?,
        locationName: String?, weather: String?
    ): Boolean =
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
            snap.content,
            snap.mood,
            snap.lat,
            snap.lon,
            snap.locationName,
            snap.weather
        )
    },
    restore = { raw ->
        val content = raw[0] as String
        val mood = raw[1] as String?
        val lat = raw[2] as Double?
        val lon = raw[3] as Double?
        val locationName = raw[4] as String?
        val weather = raw[5] as String?
        Snapshot(content, mood, lat, lon, locationName, weather)
    }
)
