package com.example.diary.ui.editor

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

val TextFieldValueSaver: Saver<TextFieldValue, Any> = listSaver(
    save = { listOf<Any>(it.text, it.selection.start, it.selection.end) },
    restore = {
        TextFieldValue(
            text = it[0] as String,
            selection = TextRange(it[1] as Int, it[2] as Int)
        )
    }
)

private val TASK_LINE = Regex("""^(\s*)([-*+])\s\[[ xX]\](\s*)(.*)$""")
private val ORDERED_LINE = Regex("""^(\s*)(\d+)([.)])(\s+)(.*)$""")
private val BULLET_LINE = Regex("""^(\s*)([-*+])(\s+)(.*)$""")
private val QUOTE_LINE = Regex("""^(\s*>\s?)(.*)$""")

fun applyListContinuation(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
    if (new.composition != null) return null
    if (!old.selection.collapsed) return null
    if (new.text.length != old.text.length + 1) return null
    val caret = old.selection.start
    if (caret < 0 || new.selection.start != caret + 1) return null
    if (new.text.getOrNull(caret) != '\n') return null

    val lineStart = lineStartOf(new.text, caret)
    val lineText = new.text.substring(lineStart, caret)
    val prefix = continuationPrefix(lineText) ?: return null

    if (prefix.isEmpty()) {
        val text = new.text.removeRange(lineStart, caret + 1)
        return TextFieldValue(text = text, selection = TextRange(lineStart.coerceAtMost(text.length)))
    }

    val text = new.text.substring(0, caret + 1) + prefix + new.text.substring(caret + 1)
    return TextFieldValue(text = text, selection = TextRange(caret + 1 + prefix.length))
}

private fun continuationPrefix(line: String): String? {
    TASK_LINE.find(line)?.let { m ->
        val text = m.groupValues[4]
        return if (text.isEmpty()) "" else "${m.groupValues[1]}${m.groupValues[2]} [ ] "
    }
    ORDERED_LINE.find(line)?.let { m ->
        val text = m.groupValues[5]
        if (text.isEmpty()) return ""
        val n = m.groupValues[2].toIntOrNull() ?: return null
        return "${m.groupValues[1]}${n + 1}${m.groupValues[3]}${m.groupValues[4]}"
    }
    BULLET_LINE.find(line)?.let { m ->
        val text = m.groupValues[4]
        return if (text.isEmpty()) "" else "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}"
    }
    QUOTE_LINE.find(line)?.let { m ->
        val text = m.groupValues[2]
        return if (text.isEmpty()) "" else m.groupValues[1]
    }
    return null
}

fun wrapOrUnwrap(value: TextFieldValue, open: String, close: String): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)

    if (start == end) {
        val newText = text.substring(0, start) + open + close + text.substring(end)
        return TextFieldValue(newText, TextRange(start + open.length))
    }

    if (start >= open.length && end + close.length <= text.length &&
        text.substring(start - open.length, start) == open &&
        text.substring(end, end + close.length) == close
    ) {
        val newText = text.substring(0, start - open.length) +
            text.substring(start, end) +
            text.substring(end + close.length)
        val from = start - open.length
        return TextFieldValue(newText, TextRange(from, from + (end - start)))
    }

    val newText = text.substring(0, start) + open + text.substring(start, end) + close + text.substring(end)
    return TextFieldValue(newText, TextRange(start + open.length, end + open.length))
}

fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val caret = value.selection.start.coerceIn(0, text.length)
    val anchor = value.selection.end.coerceIn(0, text.length)
    val from = minOf(caret, anchor)
    val to = maxOf(caret, anchor)

    val firstLine = lineStartOf(text, from)
    val lastLineEnd = text.indexOf('\n', to).let { if (it < 0) text.length else it }
    if (firstLine > lastLineEnd) return value

    val lines = mutableListOf<Triple<Int, Int, String>>()
    var cursor = firstLine
    while (cursor <= lastLineEnd) {
        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        if (lineEnd > lastLineEnd) break
        val line = text.substring(cursor, lineEnd)
        val replacement = if (line.startsWith(prefix)) line.substring(prefix.length) else prefix + line
        lines.add(Triple(cursor, lineEnd, replacement))
        if (lineEnd >= text.length) break
        cursor = lineEnd + 1
    }
    if (lines.isEmpty()) return value

    val newText = buildString {
        var pos = 0
        for ((start, end, replacement) in lines) {
            append(text, pos, start)
            append(replacement)
            pos = end
        }
        append(text, pos, text.length)
    }

    fun map(index: Int): Int {
        var delta = 0
        for ((start, end, replacement) in lines) {
            val oldLen = end - start
            when {
                index <= start -> return index + delta
                index >= end -> delta += replacement.length - oldLen
                else -> return start + delta
            }
        }
        return index + delta
    }

    return TextFieldValue(
        text = newText,
        selection = TextRange(map(from).coerceIn(0, newText.length), map(to).coerceIn(0, newText.length))
    )
}

fun insertAtCursor(value: TextFieldValue, inserted: String, caretOffset: Int = inserted.length): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    val newText = text.substring(0, start) + inserted + text.substring(end)
    return TextFieldValue(newText, TextRange((start + caretOffset).coerceIn(0, newText.length)))
}

fun insertBlockAtCursor(value: TextFieldValue, block: String): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    val before = if (start > 0 && text[start - 1] != '\n') "\n" else ""
    val after = if (end < text.length && text[end] != '\n') "\n" else ""
    val inserted = before + block + after
    val newText = text.substring(0, start) + inserted + text.substring(end)
    return TextFieldValue(newText, TextRange((start + inserted.length).coerceIn(0, newText.length)))
}

private fun lineStartOf(text: String, index: Int): Int {
    if (index <= 0) return 0
    val bounded = index.coerceAtMost(text.length)
    return text.lastIndexOf('\n', bounded - 1) + 1
}
