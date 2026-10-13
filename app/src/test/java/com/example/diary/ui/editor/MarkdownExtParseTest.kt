package com.example.diary.ui.editor

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.Heading
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownExtParseTest {

    private val parser = buildMarkdownParser()

    private fun first(root: Node, pred: (Node) -> Boolean): Node? {
        var child = root.firstChild
        while (child != null) {
            if (pred(child)) return child
            first(child, pred)?.let { return it }
            child = child.next
        }
        return null
    }

    private fun all(root: Node, pred: (Node) -> Boolean): List<Node> {
        val out = mutableListOf<Node>()
        var child = root.firstChild
        while (child != null) {
            if (pred(child)) out += child
            out += all(child, pred)
            child = child.next
        }
        return out
    }

    @Test
    fun `strikethrough node is produced`() {
        val doc = parser.parse("a ~~gone~~ b")
        assertNotNull(first(doc) { it is Strikethrough })
    }

    @Test
    fun `task marker reports unchecked state`() {
        val item = first(parser.parse("- [ ] todo")) { it is ListItem }!!
        val marker = taskMarkerOf(item)
        assertNotNull(marker)
        assertFalse(marker!!.isChecked)
    }

    @Test
    fun `task marker reports checked state`() {
        val item = first(parser.parse("- [x] done")) { it is ListItem }!!
        val marker = taskMarkerOf(item)
        assertNotNull(marker)
        assertTrue(marker!!.isChecked)
    }

    @Test
    fun `task marker is found either before or inside the paragraph`() {
        val item = first(parser.parse("- [X] done")) { it is ListItem }!!
        val direct = item.firstChild
        val paragraphFirst = (direct as? Paragraph)?.firstChild
        assertTrue(direct is TaskListItemMarker || paragraphFirst is TaskListItemMarker)
    }

    @Test
    fun `table parses into head and body rows with alignments`() {
        val doc = parser.parse("| a | b | c |\n| --- | :--: | --: |\n| 1 | 2 | 3 |")
        val table = first(doc) { it is TableBlock }!!
        assertNotNull(first(table) { it is TableHead })
        val cells = all(table) { it is TableCell }
        assertEquals(6, cells.size)
        assertEquals(TableCell.Alignment.CENTER, (cells[1] as TableCell).alignment)
        assertEquals(TableCell.Alignment.RIGHT, (cells[2] as TableCell).alignment)
    }

    @Test
    fun `ordered list keeps its start number`() {
        val doc = parser.parse("3. alpha\n4. beta")
        val list = first(doc) { it is OrderedList } as OrderedList
        assertEquals(3, list.startNumber)
    }

    @Test
    fun `bullets still parse alongside the extensions`() {
        val doc = parser.parse("# Title\n\n- a\n- b")
        assertNotNull(first(doc) { it is Heading })
        assertEquals(2, all(doc) { it is ListItem }.size)
    }
}
