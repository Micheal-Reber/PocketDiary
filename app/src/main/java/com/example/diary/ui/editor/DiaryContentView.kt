package com.example.diary.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.photo.DiaryPhotoStore

private val imgMarkerRegex = Regex("\\[img:([^\\]]+)\\]")

sealed class ContentSegment {
    data class Text(val text: String) : ContentSegment()
    data class Image(val fileName: String) : ContentSegment()
}

/** Split content into text / image segments by [img:fileName] markers. */
fun splitByImgMarker(content: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    var last = 0
    imgMarkerRegex.findAll(content).forEach { m ->
        if (m.range.first > last) {
            segments += ContentSegment.Text(content.substring(last, m.range.first))
        }
        segments += ContentSegment.Image(m.groupValues[1])
        last = m.range.last + 1
    }
    if (last < content.length) {
        segments += ContentSegment.Text(content.substring(last))
    }
    return segments
}

/**
 * Rendered diary body: text segments (Markdown) interleaved with photo blocks
 * at their [img:…] marker positions. Tapping a photo opens it fullscreen.
 */
@Composable
fun DiaryContentView(
    content: String,
    entryId: Long?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val segments = remember(content) { splitByImgMarker(content) }
    Column(modifier) {
        segments.forEach { seg ->
            when (seg) {
                is ContentSegment.Text -> if (seg.text.isNotBlank()) {
                    MarkdownText(
                        markdown = seg.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
                is ContentSegment.Image -> {
                    var fullscreen by remember { mutableStateOf(false) }
                    val bmp by produceState<ImageBitmap?>(initialValue = null, seg.fileName) {
                        val f = DiaryPhotoStore.resolve(context, entryId, seg.fileName)
                        value = f?.let { BackgroundImageStore.decode(it.absolutePath, maxDim = 1600) }
                    }
                    bmp?.let { imageBitmap ->
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "日记图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.3f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { fullscreen = true }
                        )
                    }
                    if (fullscreen) {
                        Dialog(
                            onDismissRequest = { fullscreen = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .clickable { fullscreen = false },
                                contentAlignment = Alignment.Center
                            ) {
                                bmp?.let { imageBitmap ->
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = "日记图片",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.fillMaxWidth()
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
