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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text as MdText
import org.commonmark.parser.Parser

private val markdownParser = Parser.builder().build()

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
                else -> MaterialTheme.typography.titleSmall
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

        is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = 10.dp))

        // Unhandled block types (images, tables…) are deliberately skipped.
        else -> Unit
    }
}

@Composable
private fun RenderListItems(list: Node, ordered: Boolean, depth: Int) {
    var number = (list as? OrderedList)?.startNumber ?: 1
    list.forEachChild { item ->
        val prefix = if (ordered) "${number}. " else "• "
        var firstBlock = true
        item.forEachChild { child ->
            if (firstBlock && child is Paragraph) {
                Row(Modifier.padding(start = (depth * 16).dp)) {
                    Text(
                        prefix,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = child.toInlineText(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (child is ListItem) {
                // Nested list item reached directly (loose lists) — keep the
                // same depth bump the list branch applies.
                RenderBlock(child, depth = depth + 1)
            } else {
                RenderBlock(child, depth + 1)
            }
            firstBlock = false
        }
        if (ordered) number++
    }
}

@Composable
private fun CodeBlock(literal: String, indent: Modifier) {
    Surface(
        modifier = indent
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 0.dp),
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
            is Link -> builder.withStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            ) { node.appendInline(builder, codeBackground, linkColor) }
            is SoftLineBreak -> builder.append(' ')
            is HardLineBreak -> builder.append('\n')
            else -> Unit
        }
    }
}

private val plainTextSpecials = charArrayOf('*', '#', '`', '>', '~', '_')

/**
 * Strip markdown syntax down to readable plain text — used by the diary list
 * card preview so `**bold**` doesn't show as literal asterisks. Plain-text
 * diaries pass through untouched via the fast path.
 */
fun markdownToPlainText(markdown: String): String {
    if (markdown.isEmpty() || markdown.none { it in plainTextSpecials }) return markdown
    return markdown
        .replace(Regex("```[\\s\\S]*?(```|$)"), " ")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("\\*([^*\\n]+)\\*"), "$1")
        .replace(Regex("`([^`\\n]+)`"), "$1")
        .replace(Regex("(?m)^>\\s?"), "")
        .replace(Regex("(?m)^[-*+]\\s+"), "· ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
