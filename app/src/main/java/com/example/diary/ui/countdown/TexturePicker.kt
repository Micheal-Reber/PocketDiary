package com.example.diary.ui.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.diary.data.countdown.TextureLibrary
import com.example.diary.ui.theme.Spacing

/**
 * 纹理选择器共享组件：4 纹理等分 + “无”选项 + 名称标签
 *
 * 4 项时无需横滑，Row 等分铺满；统一样式与选中态。
 */

// ── 单个预览单元 ──────────────────────────────────────────────────────────

@Composable
private fun TextureCell(
    textureIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = TextureLibrary.getTextureName(textureIndex)
    val shape = MaterialTheme.shapes.small
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .size(64.dp)
                .clip(shape)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    else Modifier
                )
        ) {
            TextureLibrary.TexturePreviewThumb(
                textureIndex = textureIndex,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().size(64.dp).clip(shape)
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun NoneCell(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .size(64.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                )
        ) {
            Icon(
                Icons.Default.Block, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Text(
            "无",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 对外：4 等分选择行 ───────────────────────────────────────────────────

/**
 * @param selectedIndex 当前选中索引，-1 表示“无纹理”
 * @param onSelect 回调：-1=无，0..3=对应纹理
 * @param showNone 是否显示“无”选项
 */
@Composable
fun TexturePickerRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showNone: Boolean = true
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = modifier.fillMaxWidth()
    ) {
        if (showNone) {
            NoneCell(
                selected = selectedIndex == -1,
                onClick = { onSelect(-1) },
                modifier = Modifier.weight(1f)
            )
        }
        for (idx in 0 until TextureLibrary.TEXTURE_COUNT) {
            TextureCell(
                textureIndex = idx,
                selected = selectedIndex == idx,
                onClick = { onSelect(idx) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
