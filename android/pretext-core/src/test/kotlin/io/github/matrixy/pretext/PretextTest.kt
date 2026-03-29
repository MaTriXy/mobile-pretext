package io.github.matrixy.pretext

import io.github.matrixy.pretext.analysis.*
import io.github.matrixy.pretext.linebreak.*
import kotlin.test.*

class FakeTextMeasurer(private val fontSize: Float = 16f) : TextMeasurer {
    override fun measureText(text: String): Float {
        var width = 0f
        for (ch in text) {
            width += when {
                ch == ' ' -> fontSize * 0.33f
                ch == '\t' -> fontSize * 1.32f
                isWideChar(ch.code) -> fontSize
                isPunctuation(ch) -> fontSize * 0.4f
                else -> fontSize * 0.6f
            }
        }
        return width
    }

    private fun isWideChar(code: Int): Boolean {
        return (code in 0x4E00..0x9FFF) || (code in 0x3400..0x4DBF) ||
               (code in 0xF900..0xFAFF) || (code in 0x3000..0x303F) ||
               (code in 0x3040..0x309F) || (code in 0x30A0..0x30FF) ||
               (code in 0xAC00..0xD7AF) || (code in 0xFF00..0xFFEF)
    }

    private fun isPunctuation(ch: Char): Boolean {
        return ch in setOf('.', ',', '!', '?', ';', ':', '%', ')', ']', '}', '\'', '"',
            '\u201C', '\u201D', '\u2018', '\u2019', '\u00BB', '\u203A', '\u2026', '\u2014', '-')
    }
}

class FakeTextSegmenter : TextSegmenter {
    override fun segmentWords(text: String): List<WordSegment> {
        // Simple word segmenter: split on spaces and punctuation boundaries
        val segments = mutableListOf<WordSegment>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == ' ' || ch == '\t' || ch == '\n') {
                segments.add(WordSegment(ch.toString(), i, false))
                i++
            } else {
                val start = i
                while (i < text.length && text[i] != ' ' && text[i] != '\t' && text[i] != '\n') i++
                segments.add(WordSegment(text.substring(start, i), start, true))
            }
        }
        return segments
    }

    override fun segmentGraphemes(text: String): List<String> {
        return text.map { it.toString() }
    }
}

class PretextTest {

    @Test
    fun testWhitespaceNormalization() {
        assertEquals("hello world", normalizeWhitespaceNormal("  hello  world  "))
        assertEquals("hello world", normalizeWhitespaceNormal("hello\n\tworld"))
        assertEquals("hello world", normalizeWhitespaceNormal("hello world"))
    }

    @Test
    fun testPreWrapPreservesSpaces() {
        assertEquals("hello  world", normalizeWhitespacePreWrap("hello  world"))
        assertEquals("hello\nworld", normalizeWhitespacePreWrap("hello\r\nworld"))
    }

    @Test
    fun testCJKDetection() {
        assertTrue(isCJK("\u4F60")) // 你
        assertTrue(isCJK("\u65E5")) // 日
        assertTrue(isCJK("\uD55C")) // 한
        assertFalse(isCJK("a"))
        assertFalse(isCJK("1"))
    }

    @Test
    fun testEmptyTextReturnsZero() {
        val segmenter = FakeTextSegmenter()
        Pretext.setSegmenter(segmenter)
        val measurer = FakeTextMeasurer()
        val prepared = Pretext.prepare("", measurer)
        val result = Pretext.layout(prepared, 100f, 20f)
        assertEquals(0, result.lineCount)
        assertEquals(0f, result.height)
    }

    @Test
    fun testSingleWordFitsOnOneLine() {
        val segmenter = FakeTextSegmenter()
        Pretext.setSegmenter(segmenter)
        val measurer = FakeTextMeasurer()
        val prepared = Pretext.prepare("Hello", measurer)
        val result = Pretext.layout(prepared, 200f, 20f)
        assertEquals(1, result.lineCount)
        assertEquals(20f, result.height)
    }

    @Test
    fun testLayoutLinesConsistency() {
        val segmenter = FakeTextSegmenter()
        Pretext.setSegmenter(segmenter)
        val measurer = FakeTextMeasurer()
        val prepared = Pretext.prepareWithSegments("Hello world this is a test", measurer)
        val simple = Pretext.layout(PreparedText(prepared.core), 100f, 20f)
        val rich = Pretext.layoutWithLines(prepared, 100f, 20f)
        assertEquals(simple.lineCount, rich.lineCount)
    }

    @Test
    fun testMultipleWordsWrap() {
        val segmenter = FakeTextSegmenter()
        Pretext.setSegmenter(segmenter)
        val measurer = FakeTextMeasurer()
        val prepared = Pretext.prepare("Hello world", measurer)
        // "Hello" = 5*9.6 = 48, " " = 5.28, "world" = 5*9.6 = 48
        // Total ~101.28 -- at width 50 should wrap to 2 lines
        val result = Pretext.layout(prepared, 50f, 20f)
        assertEquals(2, result.lineCount)
        assertEquals(40f, result.height)
    }

    @Test
    fun testLineCountWithVeryNarrowWidth() {
        val segmenter = FakeTextSegmenter()
        Pretext.setSegmenter(segmenter)
        val measurer = FakeTextMeasurer()
        val prepared = Pretext.prepare("ab", measurer)
        // Each char is 9.6 wide. Width 10 should fit one char per line -> 2 lines
        val result = Pretext.layout(prepared, 10f, 20f)
        assertEquals(2, result.lineCount)
    }
}
