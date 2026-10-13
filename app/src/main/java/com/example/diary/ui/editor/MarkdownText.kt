package com.example.diary.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text as MdText
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser

internal fun buildMarkdownParser(): Parser = Parser.builder()
    .extensions(
        listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create()
        )
    )
    .build()

private val markdownParser = buildMarkdownParser()

private inline fun Node.forEachChild(block: (Node) -> Unit) {
    var child = firstChild
    while (child != null) {
        val next = child.next
        block(child)
        child = next
    }
}

/**
 * Lightweight Markdown renderer for diary content — supports the subset a
 * personal journal actually uses: headings, bold/italic, inline code, fenced/
 * indented code blocks, nested lists, block quotes and horizontal rules.
 * Parsed with commonmark-java, rendered with our M3 tokens (no library
 * styling to fight with).
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val document = remember(markdown) { markdownParser.parse(markdown) }
    Column(modifier = modifier) {
        document.forEachChild { block ->
            RenderBlock(block, depth = 0)
        }
    }
}

@Composable
private fun RenderBlock(node: Node, depth: Int) {
    val indent = Modifier.padding(start = (depth * 16).dp)
    when (node) {
        is Heading -> Text(
            text = node.toInlineText(),
            style = when (node.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                3 -> MaterialTheme.typography.titleSmall
                4 -> MaterialTheme.typography.labelLarge
                5 -> MaterialTheme.typography.labelMedium
                else -> MaterialTheme.typography.labelSmall
            }.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = indent.padding(top = 6.dp, bottom = 2.dp)
        )

        is Paragraph -> Text(
            text = node.toInlineText(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = indent.padding(vertical = 2.dp)
        )

        is BulletList -> RenderListItems(node, ordered = false, depth)
        is OrderedList -> RenderListItems(node, ordered = true, depth)

        is BlockQuote -> Row(
            Modifier
                .padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp)
                .height(IntrinsicSize.Min)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            )
            Column(Modifier.padding(start = 12.dp)) {
                node.forEachChild { RenderBlock(it, depth = 0) }
            }
        }

        is FencedCodeBlock -> CodeBlock(node.literal, indent)
        is IndentedCodeBlock -> CodeBlock(node.literal, indent)

        is TableBlock -> RenderTable(node, depth)

        is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = 10.dp))

        // Unhandled block types (images…) are deliberately skipped.
        else -> Unit
    }
}

@Composable
private fun RenderTable(table: TableBlock, depth: Int) {
    val headRows = mutableListOf<Node>()
    val bodyRows = mutableListOf<Node>()
    table.forEachChild { section ->
        val target = if (section is TableHead) headRows else bodyRows
        section.forEachChild { target += it }
    }
    if (headRows.isEmpty() && bodyRows.isEmpty()) return
    Surface(
        modifier = Modifier.padding(start = (depth * 16).dp, top = 4.dp, bottom = 6.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            headRows.forEach { RenderTableRow(it, header = true) }
            if (headRows.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 2.dp), thickness = 1.dp)
            }
            bodyRows.forEach { RenderTableRow(it, header = false) }
        }
    }
}

@Composable
private fun RenderTableRow(row: Node, header: Boolean) {
    val cells = mutableListOf<Node>()
    row.forEachChild { cells += it }
    if (cells.isEmpty()) return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cells.forEach { cell ->
            val alignment = (cell as? TableCell)?.alignment ?: TableCell.Alignment.LEFT
            Text(
                text = cell.toInlineText(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Bold else null,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = when (alignment) {
                    TableCell.Alignment.CENTER -> TextAlign.Center
                    TableCell.Alignment.RIGHT -> TextAlign.End
                    TableCell.Alignment.LEFT -> TextAlign.Start
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun RenderListItems(list: Node, ordered: Boolean, depth: Int) {
    var number = (list as? OrderedList)?.startNumber ?: 1
    list.forEachChild { item ->
        val marker = taskMarkerOf(item)
        var firstBlock = true
        item.forEachChild { child ->
            when {
                child is TaskListItemMarker -> Unit
                firstBlock && child is Paragraph -> {
                    Row(Modifier.padding(start = (depth * 16).dp)) {
                        if (marker != null) {
                            Checkbox(
                                checked = marker.isChecked,
                                onCheckedChange = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Text(
                                if (ordered) "${number}. " else "• ",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = child.toInlineText(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    firstBlock = false
                }
                else -> {
                    RenderBlock(child, depth + 1)
                    firstBlock = false
                }
            }
        }
        if (ordered) number++
    }
}

internal fun taskMarkerOf(item: Node): TaskListItemMarker? {
    (item.firstChild as? TaskListItemMarker)?.let { return it }
    return (item.firstChild as? Paragraph)?.firstChild as? TaskListItemMarker
}

@Composable
private fun CodeBlock(literal: String, indent: Modifier) {
    Surface(
        modifier = indent
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = literal.trimEnd('\n'),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun Node.toInlineText(): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        appendInline(this, codeBackground, linkColor)
    }
}

// The builder is passed explicitly: inside forEachChild's plain lambda the
// nearest implicit receiver would be Node, shadowing the AnnotatedString
// builder receiver and breaking append/withStyle resolution.
private fun Node.appendInline(
    builder: AnnotatedString.Builder,
    codeBackground: Color,
    linkColor: Color
) {
    forEachChild { node ->
        when (node) {
            is MdText -> builder.append(node.literal)
            is StrongEmphasis -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                node.appendInline(builder, codeBackground, linkColor)
            }
            is Emphasis -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.appendInline(builder, codeBackground, linkColor)
            }
            is Code -> builder.withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            ) { builder.append(node.literal) }
            is Link -> {
                builder.pushLink(LinkAnnotation.Url(node.destination))
                builder.withStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                ) { node.appendInline(builder, codeBackground, linkColor) }
                builder.pop()
            }
            is Strikethrough -> builder.withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            ) { node.appendInline(builder, codeBackground, linkColor) }
            is TaskListItemMarker -> Unit
            // 单回车（软换行）按手机日记直觉直接换行，等同 Typora/GFM breaks 行为；
            // CommonMark 标准的「渲染成空格」不符合纯文本编辑器的用户预期
            is SoftLineBreak -> builder.append('\n')
            is HardLineBreak -> builder.append('\n')
            else -> Unit
        }
    }
}

private val plainTextSpecials = charArrayOf('*', '#', '`', '>', '~', '_', '[', '|')

// Precompiled once — constructing ~10 Regex per card preview was per-recomposition work.
private val codeBlockRe = Regex("```[\\s\\S]*?(```|$)")
private val imgMarkerRe = Regex("\\[img:[^\\]]+\\]")
private val imageLinkRe = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
private val linkRe = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")
private val headingRe = Regex("(?m)^#{1,6}\\s*")
private val boldAsteriskRe = Regex("\\*\\*([^*]+)\\*\\*")
private val boldUnderscoreRe = Regex("__(.+?)__")
private val italicAsteriskRe = Regex("\\*([^*\\n]+)\\*")
private val strikethroughRe = Regex("~~([^~]+)~~")
private val inlineCodeRe = Regex("`([^`\\n]+)`")
private val taskRe = Regex("(?m)^([-*+]\\s+)\\[[ xX]\\]\\s")
private val tableSepRe = Regex("(?m)^\\s*\\|[\\s:|-]+\\|\\s*$\\n?")
private val tableRowRe = Regex("(?m)^\\s*\\|(.+?)\\|\\s*$")
private val blockquoteRe = Regex("(?m)^>\\s?")
private val bulletRe = Regex("(?m)^[-*+]\\s+")
private val multiBlankRe = Regex("\\n{3,}")

/**
 * Strip markdown syntax down to readable plain text — used by the diary list
 * card preview so `**bold**` doesn't show as literal asterisks. Plain-text
 * diaries pass through untouched via the fast path.
 */
fun markdownToPlainText(markdown: String): String {
    if (markdown.isEmpty() || markdown.none { it in plainTextSpecials } && !markdown.contains("[img:")) {
        return markdown
    }
    return markdown
        .replace(codeBlockRe, " ")
        .replace(imgMarkerRe, "")          // 图片标记 → 移除（图片不进文本预览）
        .replace(imageLinkRe, "")
        .replace(linkRe, "$1")
        .replace(headingRe, "")
        .replace(boldAsteriskRe, "$1")
        .replace(boldUnderscoreRe, "$1")
        .replace(italicAsteriskRe, "$1")
        .replace(strikethroughRe, "$1")
        .replace(inlineCodeRe, "$1")
        .replace(taskRe, "$1")
        .replace(tableSepRe, "")
        .replace(tableRowRe) { m ->
            m.groupValues[1].split('|').joinToString(" · ") { it.trim() }
        }
        .replace(blockquoteRe, "")
        .replace(bulletRe, "· ")
        .replace(multiBlankRe, "\n\n")
        .trim()
}
