package com.example.diary.ui.countdown

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.diary.R
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.image.EventImageStore
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicFullscreenEditorScreen(
    eventId: Long,
    repository: CountdownRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val event by remember(eventId) { repository.observeById(eventId) }
        .collectAsState(initial = null)
    var imageVersion by remember { mutableIntStateOf(0) }
    var pickerOpened by remember { mutableStateOf(false) }
    var editingSession by rememberSaveable { mutableStateOf(false) }

    val photoBitmap by produceState<ImageBitmap?>(null, eventId, imageVersion, editingSession) {
        value = withContext(Dispatchers.IO) {
            if (!editingSession) return@withContext null
            val file = EventImageStore.draftFile(context, eventId).takeIf { it.exists() }
                ?: EventImageStore.file(context, eventId)
            if (file.exists()) BackgroundImageStore.decode(context, file.absolutePath, maxDim = 1400)
            else null
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                EventImageStore.importToDraft(context, uri, eventId)
                imageVersion++
            }
        }
    }

    LaunchedEffect(eventId) {
        if (!editingSession) {
            EventImageStore.deleteDraft(context, eventId)
            editingSession = true
        }
        repository.get(eventId)?.let {
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
        LocalDate.now()
    )
    val bigNumber = when (state) {
        is DateMath.CountState.Today -> stringResource(R.string.cd_badge_today)
        is DateMath.CountState.Countdown -> "${state.days}"
        is DateMath.CountState.Countup -> "${state.days}"
    }

    fun saveAndBack() {
        scope.launch {
            EventImageStore.promoteDraft(context, eventId)
            repository.save(
                currentEvent.copy(
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
                title = { Text(stringResource(R.string.cd_pc_title)) },
                navigationIcon = {
                    IconButton(onClick = { discardAndBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = { saveAndBack() }) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.size(Spacing.xs))
                        Text(stringResource(R.string.common_done))
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                    Button(
                        onClick = {
                            pickPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.size(Spacing.s))
                        Text(
                            if (photoBitmap == null) stringResource(R.string.cd_choose_bg)
                            else stringResource(R.string.cd_change_bg)
                        )
                    }
                }
            }
        }
    ) { padding ->
        val today = LocalDate.now()
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ClassicFullscreenContent(
                photoBitmap = photoBitmap,
                textureIndex = currentEvent.textureIndex,
                accent = eventAccent(currentEvent.colorIndex, state),
                eventName = currentEvent.name,
                stateLabel = stateLabel(context, state),
                bigNumber = bigNumber,
                dateLine = "${currentEvent.date} · ${weekdayLabel(context, DateMath.resolveAnchor(currentEvent.date, currentEvent.repeatRule, today))}",
                endDate = currentEvent.endDate,
                time = currentEvent.time,
                fontDark = currentEvent.fontDark
            )
        }
    }
}
