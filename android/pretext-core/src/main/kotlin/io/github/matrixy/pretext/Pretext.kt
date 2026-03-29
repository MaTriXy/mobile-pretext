package io.github.matrixy.pretext

import io.github.matrixy.pretext.analysis.*
import io.github.matrixy.pretext.bidi.computeSegmentLevels
import io.github.matrixy.pretext.linebreak.*
import io.github.matrixy.pretext.measurement.sharedMeasurementCache

/**
 * Entry point for the Pretext text layout engine.
 *
 * All public layout operations are accessed through this singleton. The typical workflow is:
 *
 * 1. Register a [TextSegmenter] once via [setSegmenter].
 * 2. Call [prepare] (fast path) or [prepareWithSegments] (rich path) to analyze and measure the text.
 * 3. Call [layout], [layoutWithLines], [walkLineRanges], or [layoutNextLine] at any width without
 *    further measurement cost.
 *
 * ```kotlin
 * Pretext.setSegmenter(IcuTextSegmenter())
 * val prepared = Pretext.prepare(text, PaintTextMeasurer(paint))
 * val result = Pretext.layout(prepared, maxWidth = 300f, lineHeight = 20f)
 * ```
 */
object Pretext {
    @Volatile
    private var segmenter: TextSegmenter? = null

    /**
     * Registers the [TextSegmenter] used by all subsequent [prepare] and [prepareWithSegments] calls.
     *
     * Must be called at least once before any preparation call. On Android, use
     * [io.github.matrixy.pretext.android.IcuTextSegmenter].
     *
     * @param segmenter The segmenter implementation to use.
     * @see TextSegmenter
     */
    fun setSegmenter(segmenter: TextSegmenter) {
        this.segmenter = segmenter
    }

    /**
     * Prepares text for fast layout by running the two-phase analysis+measurement pipeline.
     *
     * This is the **opaque fast-path** handle. It normalizes, segments, and measures the text
     * once; the returned [PreparedText] can then be laid out at any width via [layout] with no
     * further string work or canvas calls.
     *
     * Call this once per (text, font) pair and reuse the result across multiple widths.
     *
     * @param text The input string to prepare.
     * @param measurer A [TextMeasurer] configured for the target font and size.
     * @param options Optional [PrepareOptions] (e.g., whitespace mode).
     * @return An opaque [PreparedText] handle for use with [layout].
     * @throws IllegalStateException if [setSegmenter] has not been called.
     * @see layout
     */
    fun prepare(text: String, measurer: TextMeasurer, options: PrepareOptions = PrepareOptions()): PreparedText {
        val seg = requireNotNull(segmenter) { "Call Pretext.setSegmenter() before prepare()" }
        val analysis = analyzeText(text, EngineProfile.DEFAULT, options.whiteSpace, seg)
        return PreparedText(measureAnalysis(analysis, measurer, seg, includeSegments = false).core)
    }

    /**
     * Prepares text with the full segment array retained for rich layout APIs.
     *
     * Like [prepare], but the returned [PreparedTextWithSegments] also carries the segment
     * strings needed by [layoutWithLines], [walkLineRanges], and [layoutNextLine].
     *
     * Use this variant when you need line text content or cursor-based iteration, not just
     * a line count.
     *
     * @param text The input string to prepare.
     * @param measurer A [TextMeasurer] configured for the target font and size.
     * @param options Optional [PrepareOptions].
     * @return A [PreparedTextWithSegments] handle for use with the rich layout APIs.
     * @throws IllegalStateException if [setSegmenter] has not been called.
     * @see layoutWithLines
     * @see walkLineRanges
     * @see layoutNextLine
     */
    fun prepareWithSegments(text: String, measurer: TextMeasurer, options: PrepareOptions = PrepareOptions()): PreparedTextWithSegments {
        val seg = requireNotNull(segmenter) { "Call Pretext.setSegmenter() before prepare()" }
        val analysis = analyzeText(text, EngineProfile.DEFAULT, options.whiteSpace, seg)
        val result = measureAnalysis(analysis, measurer, seg, includeSegments = true)
        return PreparedTextWithSegments(result.core, result.segments ?: emptyArray())
    }

    /**
     * Computes line count and total height for the given width. **This is the resize hot path.**
     *
     * No string work, no canvas calls, no allocations beyond the result object. Safe to call
     * on every frame or constraint change.
     *
     * @param prepared The opaque handle from [prepare].
     * @param maxWidth Available width in pixels.
     * @param lineHeight Line height in pixels (uniform for all lines).
     * @return A [LayoutResult] with line count and total height.
     */
    fun layout(prepared: PreparedText, maxWidth: Float, lineHeight: Float): LayoutResult {
        val lineCount = countPreparedLines(prepared.core, maxWidth)
        return LayoutResult(lineCount, lineCount * lineHeight)
    }

    /**
     * Lays out text and materializes every line's text content, width, and cursor boundaries.
     *
     * Use this when you need the actual string content of each line (e.g., for custom rendering
     * or text selection). For aggregate geometry without string materialization, prefer
     * [walkLineRanges].
     *
     * @param prepared The rich handle from [prepareWithSegments].
     * @param maxWidth Available width in pixels.
     * @param lineHeight Line height in pixels.
     * @return A [LayoutLinesResult] containing all materialized lines.
     * @see walkLineRanges
     */
    fun layoutWithLines(prepared: PreparedTextWithSegments, maxWidth: Float, lineHeight: Float): LayoutLinesResult {
        if (prepared.core.widths.isEmpty()) return LayoutLinesResult(0, 0f, emptyList())
        val lines = mutableListOf<LayoutLine>()
        val lineCount = walkPreparedLines(prepared.core, maxWidth) { line ->
            lines.add(materializeLayoutLine(prepared, line))
        }
        return LayoutLinesResult(lineCount, lineCount * lineHeight, lines)
    }

    /**
     * Walks all lines without materializing their text, invoking [onLine] for each.
     *
     * This is the preferred batch geometry API when you need line widths and cursor
     * boundaries but not the line strings themselves (e.g., shrinkwrap calculations,
     * height pre-computation, or aggregate layout work).
     *
     * @param prepared The rich handle from [prepareWithSegments].
     * @param maxWidth Available width in pixels.
     * @param onLine Callback invoked for each line with its [LayoutLineRange].
     * @return The total number of lines.
     */
    fun walkLineRanges(prepared: PreparedTextWithSegments, maxWidth: Float, onLine: (LayoutLineRange) -> Unit): Int {
        if (prepared.core.widths.isEmpty()) return 0
        return walkPreparedLines(prepared.core, maxWidth) { line ->
            onLine(toLayoutLineRange(line))
        }
    }

    /**
     * Lays out a single line starting from [start] and returns it, or `null` if no content remains.
     *
     * This is the variable-width iterator for custom layout scenarios where each line may have
     * a different available width (e.g., text flowing around obstacles). Advance the cursor by
     * using the returned line's [LayoutLine.end] as the next call's [start].
     *
     * ```kotlin
     * var cursor = LayoutCursor(0, 0)
     * while (true) {
     *     val line = Pretext.layoutNextLine(prepared, cursor, currentWidth) ?: break
     *     // render line
     *     cursor = line.end
     * }
     * ```
     *
     * @param prepared The rich handle from [prepareWithSegments].
     * @param start Cursor position to begin the line from.
     * @param maxWidth Available width in pixels for this line.
     * @return The next [LayoutLine], or `null` if all content has been consumed.
     */
    fun layoutNextLine(prepared: PreparedTextWithSegments, start: LayoutCursor, maxWidth: Float): LayoutLine? {
        val range = layoutNextLineRangePublic(prepared.core, start, maxWidth) ?: return null
        return materializeLine(prepared, toLayoutLineRange(range))
    }

    /**
     * Clears the shared segment-metrics measurement cache.
     *
     * Call this when the font configuration changes or to reclaim memory. The cache is
     * structured as `Map<font, Map<segment, metrics>>` and is shared across all prepared texts.
     */
    fun clearCache() {
        sharedMeasurementCache.clear()
    }

    /**
     * Sets the locale used for future word segmentation and clears the measurement cache.
     *
     * Pass `null` to revert to the runtime default locale. This affects how
     * [TextSegmenter] instances created by the platform binding resolve word boundaries.
     *
     * @param locale The desired locale, or `null` for the system default.
     */
    fun setLocale(locale: java.util.Locale?) {
        localeOverride = locale
        clearCache()
    }

    // Stored locale for segmenter creation
    @Volatile
    internal var localeOverride: java.util.Locale? = null
}

// Internal orchestration
internal data class MeasureResult(val core: PreparedCore, val segments: Array<String>?)

internal fun measureAnalysis(
    analysis: TextAnalysis,
    measurer: TextMeasurer,
    segmenter: TextSegmenter,
    includeSegments: Boolean
): MeasureResult {
    val cache = sharedMeasurementCache
    val profile = EngineProfile.DEFAULT
    val fontKey = measurer // Use the measurer itself as font key

    val hyphenWidth = cache.getMetrics("-", fontKey, measurer).width
    val spaceWidth = cache.getMetrics(" ", fontKey, measurer).width
    val tabStopAdvance = spaceWidth * 8

    if (analysis.len == 0) {
        return MeasureResult(
            PreparedCore(
                FloatArray(0), FloatArray(0), FloatArray(0),
                emptyArray(), true, null,
                emptyArray(), emptyArray(),
                hyphenWidth, tabStopAdvance, emptyArray()
            ),
            if (includeSegments) emptyArray() else null
        )
    }

    val widths = mutableListOf<Float>()
    val lineEndFitAdvances = mutableListOf<Float>()
    val lineEndPaintAdvances = mutableListOf<Float>()
    val kinds = mutableListOf<SegmentBreakKind>()
    var simpleLineWalkFastPath = analysis.chunks.size <= 1
    val segStarts = if (includeSegments) mutableListOf<Int>() else null
    val breakableWidths = mutableListOf<FloatArray?>()
    val breakablePrefixWidths = mutableListOf<FloatArray?>()
    val segments = if (includeSegments) mutableListOf<String>() else null
    val preparedStartByAnalysisIndex = IntArray(analysis.len)
    val preparedEndByAnalysisIndex = IntArray(analysis.len)

    fun pushMeasuredSegment(
        text: String, width: Float, fitAdv: Float, paintAdv: Float,
        kind: SegmentBreakKind, start: Int,
        breakable: FloatArray?, breakablePrefix: FloatArray?
    ) {
        if (kind != SegmentBreakKind.Text && kind != SegmentBreakKind.Space && kind != SegmentBreakKind.ZeroWidthBreak) {
            simpleLineWalkFastPath = false
        }
        widths.add(width)
        lineEndFitAdvances.add(fitAdv)
        lineEndPaintAdvances.add(paintAdv)
        kinds.add(kind)
        segStarts?.add(start)
        breakableWidths.add(breakable)
        breakablePrefixWidths.add(breakablePrefix)
        segments?.add(text)
    }

    for (mi in 0 until analysis.len) {
        preparedStartByAnalysisIndex[mi] = widths.size
        val segText = analysis.texts[mi]
        val segWordLike = analysis.isWordLike[mi]
        val segKind = analysis.kinds[mi]
        val segStart = analysis.starts[mi]

        when (segKind) {
            SegmentBreakKind.SoftHyphen -> {
                pushMeasuredSegment(segText, 0f, hyphenWidth, hyphenWidth, segKind, segStart, null, null)
                preparedEndByAnalysisIndex[mi] = widths.size; continue
            }
            SegmentBreakKind.HardBreak, SegmentBreakKind.Tab -> {
                pushMeasuredSegment(segText, 0f, 0f, 0f, segKind, segStart, null, null)
                preparedEndByAnalysisIndex[mi] = widths.size; continue
            }
            else -> {}
        }

        val segMetrics = cache.getMetrics(segText, fontKey, measurer)

        // CJK grapheme splitting
        if (segKind == SegmentBreakKind.Text && segMetrics.containsCJK) {
            val graphemes = segmenter.segmentGraphemes(segText)
            var unitText = ""
            for (grapheme in graphemes) {
                if (unitText.isEmpty()) { unitText = grapheme; continue }

                val unitFirst = unitText[0]
                val graphFirst = grapheme[0]
                if (kinsokuEnd.contains(unitFirst) || kinsokuStart.contains(graphFirst) ||
                    leftStickyPunctuation.contains(graphFirst) ||
                    (profile.carryCJKAfterClosingQuote && isCJK(grapheme) && endsWithClosingQuote(unitText))) {
                    unitText += grapheme; continue
                }

                val unitMetrics = cache.getMetrics(unitText, fontKey, measurer)
                pushMeasuredSegment(unitText, unitMetrics.width, unitMetrics.width, unitMetrics.width, SegmentBreakKind.Text, segStart, null, null)
                unitText = grapheme
            }
            if (unitText.isNotEmpty()) {
                val unitMetrics = cache.getMetrics(unitText, fontKey, measurer)
                pushMeasuredSegment(unitText, unitMetrics.width, unitMetrics.width, unitMetrics.width, SegmentBreakKind.Text, segStart, null, null)
            }
            preparedEndByAnalysisIndex[mi] = widths.size; continue
        }

        val w = segMetrics.width
        val lineEndFitAdv = if (segKind == SegmentBreakKind.Space || segKind == SegmentBreakKind.PreservedSpace || segKind == SegmentBreakKind.ZeroWidthBreak) 0f else w
        val lineEndPaintAdv = if (segKind == SegmentBreakKind.Space || segKind == SegmentBreakKind.ZeroWidthBreak) 0f else w

        if (segWordLike && segText.length > 1) {
            val gWidths = cache.getGraphemeWidths(segText, fontKey, measurer, segmenter)
            val gPrefixWidths = if (profile.preferPrefixWidthsForBreakableRuns) cache.getGraphemePrefixWidths(segText, fontKey, measurer, segmenter) else null
            pushMeasuredSegment(segText, w, lineEndFitAdv, lineEndPaintAdv, segKind, segStart, gWidths, gPrefixWidths)
        } else {
            pushMeasuredSegment(segText, w, lineEndFitAdv, lineEndPaintAdv, segKind, segStart, null, null)
        }
        preparedEndByAnalysisIndex[mi] = widths.size
    }

    val chunks = mapAnalysisChunksToPreparedChunks(analysis.chunks, preparedStartByAnalysisIndex, preparedEndByAnalysisIndex)
    val segLevels = segStarts?.let { computeSegmentLevels(analysis.normalized, it.toIntArray()) }

    return MeasureResult(
        PreparedCore(
            widths.toFloatArray(), lineEndFitAdvances.toFloatArray(), lineEndPaintAdvances.toFloatArray(),
            kinds.toTypedArray(), simpleLineWalkFastPath, segLevels,
            breakableWidths.toTypedArray(), breakablePrefixWidths.toTypedArray(),
            hyphenWidth, tabStopAdvance, chunks
        ),
        segments?.toTypedArray()
    )
}

private fun mapAnalysisChunksToPreparedChunks(
    chunks: List<AnalysisChunk>,
    startMap: IntArray,
    endMap: IntArray
): Array<PreparedLineChunk> {
    return Array(chunks.size) { i ->
        val chunk = chunks[i]
        val start = if (chunk.startSegmentIndex < startMap.size) startMap[chunk.startSegmentIndex] else (endMap.lastOrNull() ?: 0)
        val end = if (chunk.endSegmentIndex < startMap.size) startMap[chunk.endSegmentIndex] else (endMap.lastOrNull() ?: 0)
        val consumed = if (chunk.consumedEndSegmentIndex < startMap.size) startMap[chunk.consumedEndSegmentIndex] else (endMap.lastOrNull() ?: 0)
        PreparedLineChunk(start, end, consumed)
    }
}

// Line materialization helpers
internal fun toLayoutLineRange(line: InternalLayoutLine): LayoutLineRange {
    return LayoutLineRange(
        line.width,
        LayoutCursor(line.startSegmentIndex, line.startGraphemeIndex),
        LayoutCursor(line.endSegmentIndex, line.endGraphemeIndex)
    )
}

private fun lineHasDiscretionaryHyphen(kinds: Array<SegmentBreakKind>, startSeg: Int, startGrapheme: Int, endSeg: Int): Boolean {
    return endSeg > 0 && kinds[endSeg - 1] == SegmentBreakKind.SoftHyphen && !(startSeg == endSeg && startGrapheme > 0)
}

// Grapheme cluster segmentation using java.text.BreakIterator (JVM-safe, no Android dep)
private fun splitGraphemeClusters(text: String): List<String> {
    val bi = java.text.BreakIterator.getCharacterInstance()
    bi.setText(text)
    val graphemes = mutableListOf<String>()
    var start = bi.first()
    var end = bi.next()
    while (end != java.text.BreakIterator.DONE) {
        graphemes.add(text.substring(start, end))
        start = end
        end = bi.next()
    }
    return graphemes
}

private fun buildLineText(
    segments: Array<String>, kinds: Array<SegmentBreakKind>,
    startSeg: Int, startGrapheme: Int, endSeg: Int, endGrapheme: Int
): String {
    val sb = StringBuilder()
    val hasHyphen = lineHasDiscretionaryHyphen(kinds, startSeg, startGrapheme, endSeg)

    for (i in startSeg until endSeg) {
        if (kinds[i] == SegmentBreakKind.SoftHyphen || kinds[i] == SegmentBreakKind.HardBreak) continue
        if (i == startSeg && startGrapheme > 0) {
            val graphemes = splitGraphemeClusters(segments[i])
            sb.append(graphemes.drop(startGrapheme).joinToString(""))
        } else {
            sb.append(segments[i])
        }
    }

    if (endGrapheme > 0) {
        if (hasHyphen) sb.append('-')
        val graphemes = splitGraphemeClusters(segments[endSeg])
        val from = if (startSeg == endSeg) startGrapheme else 0
        sb.append(graphemes.subList(from, endGrapheme).joinToString(""))
    } else if (hasHyphen) {
        sb.append('-')
    }

    return sb.toString()
}

internal fun materializeLayoutLine(prepared: PreparedTextWithSegments, line: InternalLayoutLine): LayoutLine {
    return LayoutLine(
        buildLineText(prepared.segments, prepared.core.kinds,
            line.startSegmentIndex, line.startGraphemeIndex,
            line.endSegmentIndex, line.endGraphemeIndex),
        line.width,
        LayoutCursor(line.startSegmentIndex, line.startGraphemeIndex),
        LayoutCursor(line.endSegmentIndex, line.endGraphemeIndex)
    )
}

internal fun materializeLine(prepared: PreparedTextWithSegments, range: LayoutLineRange): LayoutLine {
    return LayoutLine(
        buildLineText(prepared.segments, prepared.core.kinds,
            range.start.segmentIndex, range.start.graphemeIndex,
            range.end.segmentIndex, range.end.graphemeIndex),
        range.width,
        range.start,
        range.end
    )
}
