package com.example.diary.ui.countdown

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.R
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.navigation.glassStroke
import com.example.diary.ui.navigation.glassTint
import com.example.diary.ui.theme.Spacing
import com.example.diary.ui.widget.CountdownWidgetRefresh
import com.example.diary.ui.widget.CountdownWidgetState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownWidgetPickScreen(
    widgetId: Int,
    repository: CountdownRepository,
    onCreate: () -> Unit
) {
    val context = LocalContext.current
    val selection = remember(widgetId) { CountdownWidgetState(context) }
    val selectedId = remember(widgetId) { selection.read(widgetId) }
    val allEvents by remember { repository.observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val today = remember { LocalDate.now() }
    val events = remember(allEvents) {
        allEvents.sortedWith(compareByDescending<CountdownEvent> { it.pinned }.thenBy { it.date })
    }

    fun pick(eventId: Long) {
        if (eventId > 0L) selection.write(widgetId, eventId) else selection.clear(widgetId)
        CountdownWidgetRefresh.updateAll(context)
        context.findActivity()?.finish()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cd_pick_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { context.findActivity()?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.cd_widget_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.l))
                FilledTonalButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.cd_fab))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.l,
                end = Spacing.l,
                top = Spacing.l,
                bottom = Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            item(key = "auto") {
                PickRow(
                    selected = selectedId <= 0L || events.none { it.id == selectedId },
                    title = stringResource(R.string.cd_pick_auto),
                    subtitle = stringResource(R.string.cd_pick_auto_desc),
                    accent = null,
                    onClick = { pick(0L) }
                )
            }
            items(events, key = { it.id }) { event ->
                val state = DateMath.compute(event.date, event.repeatRule, event.plusOne, today)
                PickRow(
                    selected = selectedId == event.id,
                    title = event.name,
                    subtitle = stateLabel(context, state),
                    accent = eventAccent(event.colorIndex, state),
                    onClick = { pick(event.id) }
                )
            }
        }
    }
}

@Composable
private fun PickRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    accent: Color?,
    onClick: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(glassTint(dark), MaterialTheme.shapes.medium)
            .border(1.dp, glassStroke(dark), MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (accent != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(Spacing.m))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
