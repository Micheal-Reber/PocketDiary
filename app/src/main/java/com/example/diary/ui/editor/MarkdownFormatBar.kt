package com.example.diary.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.diary.R
import com.example.diary.ui.theme.Spacing

enum class MarkdownAction { Bold, Italic, Heading, Bullet, Number, Quote, Code, Link, Image }

@Composable
fun MarkdownFormatBar(
    onAction: (MarkdownAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            FormatButton(Icons.Default.FormatBold, R.string.editor_md_bold) { onAction(MarkdownAction.Bold) }
            FormatButton(Icons.Default.FormatItalic, R.string.editor_md_italic) { onAction(MarkdownAction.Italic) }
            FormatButton(Icons.Default.Title, R.string.editor_md_heading) { onAction(MarkdownAction.Heading) }
            FormatButton(Icons.Default.FormatListBulleted, R.string.editor_md_list) { onAction(MarkdownAction.Bullet) }
            FormatButton(Icons.Default.FormatListNumbered, R.string.editor_md_list_ordered) { onAction(MarkdownAction.Number) }
            FormatButton(Icons.Default.FormatQuote, R.string.editor_md_quote) { onAction(MarkdownAction.Quote) }
            FormatButton(Icons.Default.Code, R.string.editor_md_code) { onAction(MarkdownAction.Code) }
            FormatButton(Icons.Default.Link, R.string.editor_md_link) { onAction(MarkdownAction.Link) }
            FormatButton(Icons.Default.PhotoCamera, R.string.editor_insert_photo) { onAction(MarkdownAction.Image) }
        }
    }
}

@Composable
private fun FormatButton(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            icon,
            contentDescription = stringResource(labelRes),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
