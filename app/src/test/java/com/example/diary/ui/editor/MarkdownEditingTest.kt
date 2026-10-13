package com.example.diary.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownEditingTest {

    private fun value(text: String, start: Int, end: Int = start) =
        TextFieldValue(text, TextRange(start, end))

    private fun pressEnter(old: TextFieldValue): TextFieldValue? {
        val caret = old.selection.start
        val new = TextFieldValue(
            text = old.text.substring(0, caret) + "\n" + old.text.substring(caret),
            selection = TextRange(caret + 1)
        )
        return applyListContinuation(old, new)
    }

    @Test
    fun `continuation keeps bullet prefix`() {
        val out = pressEnter(value("- item", 6))!!
        assertEquals("- item\n- ", out.text)
        assertEquals(TextRange(9), out.selection)
    }

    @Test
    fun `continuation keeps indentation`() {
        val out = pressEnter(value("  - item", 8))!!
        assertEquals("  - item\n  - ", out.text)
        assertEquals(TextRange(13), out.selection)
    }

    @Test
    fun `continuation increments ordered list`() {
        val out = pressEnter(value("1. item", 7))!!
        assertEquals("1. item\n2. ", out.text)
        assertEquals(TextRange(11), out.selection)
    }

    @Test
    fun `continuation keeps quote marker`() {
        val out = pressEnter(value("> hi", 4))!!
        assertEquals("> hi\n> ", out.text)
        assertEquals(TextRange(7), out.selection)
    }

    @Test
    fun `continuation keeps task marker`() {
        val out = pressEnter(value("- [ ] todo", 10))!!
        assertEquals("- [ ] todo\n- [ ] ", out.text)
        assertEquals(TextRange(17), out.selection)
    }

    @Test
    fun `continuation exits empty bullet`() {
        val out = pressEnter(value("- ", 2))!!
        assertEquals("", out.text)
        assertEquals(TextRange(0), out.selection)
    }

    @Test
    fun `continuation exits empty ordered item`() {
        val out = pressEnter(value("3. ", 3))!!
        assertEquals("", out.text)
    }

    @Test
    fun `continuation exits empty task`() {
        val out = pressEnter(value("- [ ] ", 6))!!
        assertEquals("", out.text)
    }

    @Test
    fun `continuation exits empty quote`() {
        val out = pressEnter(value("> ", 2))!!
        assertEquals("", out.text)
    }

    @Test
    fun `continuation is skipped outside lists`() {
        assertNull(pressEnter(value("hello", 5)))
    }

    @Test
    fun `continuation is skipped while composing`() {
        val old = value("- item", 6)
        val new = TextFieldValue("- item\n", TextRange(7), TextRange(0, 7))
        assertNull(applyListContinuation(old, new))
    }

    @Test
    fun `continuation is skipped for multi char edits`() {
        val old = value("ab", 1)
        val new = value("abXYZ", 4)
        assertNull(applyListContinuation(old, new))
    }

    @Test
    fun `continuation is skipped on backspace`() {
        val old = value("- a", 3)
        val new = value("- ", 2)
        assertNull(applyListContinuation(old, new))
    }

    @Test
    fun `continuation is skipped without moving caret`() {
        val old = value("- item", 6)
        val new = value("- item\n", 6)
        assertNull(applyListContinuation(old, new))
    }

    @Test
    fun `wrap inserts markers with caret between`() {
        val out = wrapOrUnwrap(value("hello", 2), "**", "**")
        assertEquals("he****llo", out.text)
        assertEquals(TextRange(4), out.selection)
    }

    @Test
    fun `wrap wraps a selection`() {
        val out = wrapOrUnwrap(value("hello", 1, 4), "**", "**")
        assertEquals("h**ell**o", out.text)
        assertEquals(TextRange(3, 6), out.selection)
    }

    @Test
    fun `wrap unwraps an already wrapped selection`() {
        val out = wrapOrUnwrap(value("h**ell**o", 3, 6), "**", "**")
        assertEquals("hello", out.text)
        assertEquals(TextRange(1, 4), out.selection)
    }

    @Test
    fun `wrap unwraps single char markers`() {
        val out = wrapOrUnwrap(value("a*be*c", 2, 4), "*", "*")
        assertEquals("abec", out.text)
        assertEquals(TextRange(1, 3), out.selection)
    }

    @Test
    fun `toggle prefix adds to a line`() {
        val out = toggleLinePrefix(value("alpha", 0, 5), "- ")
        assertEquals("- alpha", out.text)
        assertEquals(TextRange(0, 7), out.selection)
    }

    @Test
    fun `toggle prefix removes an existing prefix`() {
        val out = toggleLinePrefix(value("- alpha", 0, 7), "- ")
        assertEquals("alpha", out.text)
        assertEquals(TextRange(0, 5), out.selection)
    }

    @Test
    fun `toggle prefix applies to every selected line`() {
        val out = toggleLinePrefix(value("a\nb", 0, 3), "- ")
        assertEquals("- a\n- b", out.text)
        assertEquals(TextRange(0, 7), out.selection)
    }

    @Test
    fun `toggle prefix only touches lines the selection touches`() {
        val out = toggleLinePrefix(value("a\nb\nc", 2, 3), "- ")
        assertEquals("a\n- b\nc", out.text)
        assertEquals(TextRange(2, 5), out.selection)
    }

    @Test
    fun `insert puts caret inside brackets`() {
        val out = insertAtCursor(value("say", 3), "[]()", 1)
        assertEquals("say[]()", out.text)
        assertEquals(TextRange(4), out.selection)
    }

    @Test
    fun `insert replaces an active selection`() {
        val out = insertAtCursor(value("abc", 1, 2), "X")
        assertEquals("aXc", out.text)
        assertEquals(TextRange(2), out.selection)
    }

    @Test
    fun `block insert on empty text has no separators`() {
        val out = insertBlockAtCursor(value("", 0), "[img:1.jpg]")
        assertEquals("[img:1.jpg]", out.text)
        assertEquals(TextRange(11), out.selection)
    }

    @Test
    fun `block insert separates an existing line`() {
        val out = insertBlockAtCursor(value("abc", 1), "[img:1.jpg]")
        assertEquals("a\n[img:1.jpg]\nbc", out.text)
        assertEquals(TextRange(14), out.selection)
    }

    @Test
    fun `block insert at line start skips the leading separator`() {
        val out = insertBlockAtCursor(value("a\nb", 2), "[img:1.jpg]")
        assertEquals("a\n[img:1.jpg]\nb", out.text)
        assertEquals(TextRange(14), out.selection)
    }

    @Test
    fun `block insert at end skips the trailing separator`() {
        val out = insertBlockAtCursor(value("abc", 3), "[img:1.jpg]")
        assertEquals("abc\n[img:1.jpg]", out.text)
        assertEquals(TextRange(15), out.selection)
    }
}
