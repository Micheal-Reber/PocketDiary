package com.example.diary.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPlainTextTest {

    @Test
    fun `plain text passes through untouched`() {
        assertEquals("just a normal diary", markdownToPlainText("just a normal diary"))
    }

    @Test
    fun `inline link keeps only the label`() {
        assertEquals("看这里", markdownToPlainText("[看这里](https://a.com)"))
        assertEquals("see docs now", markdownToPlainText("see [docs](https://a.com) now"))
    }

    @Test
    fun `image link is dropped entirely`() {
        assertEquals("", markdownToPlainText("![alt](https://a.com/x.png)"))
        assertEquals("before  after", markdownToPlainText("before ![alt](u) after"))
    }

    @Test
    fun `img marker is dropped`() {
        assertEquals("a  b", markdownToPlainText("a [img:photo.jpg] b"))
    }

    @Test
    fun `strikethrough markers are dropped`() {
        assertEquals("gone", markdownToPlainText("~~gone~~"))
    }

    @Test
    fun `task markers are dropped but the bullet stays`() {
        assertEquals("· milk", markdownToPlainText("- [ ] milk"))
        assertEquals("· done", markdownToPlainText("- [x] done"))
        assertEquals("· done", markdownToPlainText("* [X] done"))
    }

    @Test
    fun `heading without a space is still stripped`() {
        assertEquals("Title", markdownToPlainText("#Title"))
        assertEquals("Title", markdownToPlainText("# Title"))
    }

    @Test
    fun `bold markers are dropped`() {
        assertEquals("b", markdownToPlainText("**b**"))
        assertEquals("b", markdownToPlainText("__b__"))
    }

    @Test
    fun `italic markers are dropped`() {
        assertEquals("b", markdownToPlainText("*b*"))
    }

    @Test
    fun `inline code markers are dropped`() {
        assertEquals("b", markdownToPlainText("`b`"))
    }

    @Test
    fun `code block is replaced by whitespace`() {
        assertEquals("", markdownToPlainText("```\nfoo\nbar\n```"))
    }

    @Test
    fun `blockquote marker is dropped`() {
        assertEquals("hi", markdownToPlainText("> hi"))
    }

    @Test
    fun `mixed syntax is unwrapped in order`() {
        assertEquals(
            "see link and bold",
            markdownToPlainText("see [link](u) and **bold**")
        )
    }

    @Test
    fun `ordered list numbers are preserved`() {
        assertEquals("1. item", markdownToPlainText("1. item"))
    }

    @Test
    fun `empty string passes through`() {
        assertEquals("", markdownToPlainText(""))
    }
}
