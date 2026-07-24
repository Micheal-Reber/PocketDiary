package com.example.diary.ui.editor.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoodChips(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(MoodPresets.LIST) { emoji ->
            val isSel = emoji == selected
            FilterChip(
                selected = isSel,
                onClick = { onSelect(if (isSel) null else emoji) },
                label = { Text(emoji, fontSize = 18.sp) },
                shape = CircleShape,
                // Keep MinTouchTarget (48dp) but allow growth for emoji glyphs that
                // sit lower in the line metrics — fixed height cropped some chars.
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}
