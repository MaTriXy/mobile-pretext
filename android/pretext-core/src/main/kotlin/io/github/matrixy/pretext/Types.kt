package io.github.matrixy.pretext

// MARK: - Public Types

/**
 * Controls how whitespace is handled during text preparation and layout.
 *
 * @see PrepareOptions
 */
enum class WhiteSpaceMode {
    /** Standard CSS `white-space: normal` behavior. Collapses runs of spaces and wraps at word boundaries. */
    Normal,
    /** CSS `pre-wrap`-like behavior. Preserves ordinary spaces, tab stops, and hard line breaks. */
    PreWrap
}

/**
 * Classifies the break behavior and rendering role of a prepared text segment.
 *
 * The layout engine distinguishes eight segment kinds so that whitespace collapsing,
 * line-fit accounting, and soft-hyphen visibility can each be resolved correctly.
 */
sealed interface SegmentBreakKind {
    /** Ordinary visible text (words, punctuation, CJK graphemes). */
    data object Text : SegmentBreakKind
    /** Collapsible inter-word space (consumed at line end under [WhiteSpaceMode.Normal]). */
    data object Space : SegmentBreakKind
    /** Preserved space that contributes width even at line end (used with [WhiteSpaceMode.PreWrap]). */
    data object PreservedSpace : SegmentBreakKind
    /** Tab character, advanced to the next tab stop during layout. */
    data object Tab : SegmentBreakKind
    /** Non-breaking glue (NBSP, NNBSP, word-joiner runs). Prevents wrapping at this position. */
    data object Glue : SegmentBreakKind
    /** Zero-width break opportunity (ZWSP). Allows a line break without consuming any width. */
    data object ZeroWidthBreak : SegmentBreakKind
    /** Soft hyphen. Invisible when unbroken; renders a trailing `-` if the line breaks here. */
    data object SoftHyphen : SegmentBreakKind
    /** Hard line break (`\n`). Forces a new line unconditionally. */
    data object HardBreak : SegmentBreakKind
}

/**
 * Options that configure the text-analysis phase of [Pretext.prepare] and [Pretext.prepareWithSegments].
 *
 * @property whiteSpace Whitespace handling mode. Defaults to [WhiteSpaceMode.Normal].
 */
data class PrepareOptions(
    val whiteSpace: WhiteSpaceMode = WhiteSpaceMode.Normal
)

/**
 * Result of the fast [Pretext.layout] path: line count and total height.
 *
 * @property lineCount Number of visual lines the text occupies at the given width.
 * @property height Total height in pixels (`lineCount * lineHeight`).
 */
data class LayoutResult(val lineCount: Int, val height: Float)

/**
 * A position within the prepared segment array, used as a line boundary marker.
 *
 * @property segmentIndex Index into the prepared segment array.
 * @property graphemeIndex Grapheme offset within the segment (0 when the boundary falls on a segment edge).
 */
data class LayoutCursor(val segmentIndex: Int, val graphemeIndex: Int)

/**
 * A materialized line of text produced by [Pretext.layoutWithLines] or [Pretext.layoutNextLine].
 *
 * Contains the visible text content and its measured width, plus cursor boundaries
 * that can be used for hit-testing or custom rendering.
 *
 * @property text The visible text of this line (soft hyphens are rendered as `-` when they win the break).
 * @property width The measured pixel width of the line content.
 * @property start Cursor marking the beginning of this line in the segment array.
 * @property end Cursor marking the end of this line in the segment array.
 */
data class LayoutLine(
    val text: String,
    val width: Float,
    val start: LayoutCursor,
    val end: LayoutCursor
)

/**
 * A non-materializing line range produced by [Pretext.walkLineRanges].
 *
 * Carries line width and cursor boundaries without building the line's [String].
 * Use this for aggregate layout work (e.g., shrinkwrap) where the text content is not needed.
 *
 * @property width The measured pixel width of the line.
 * @property start Cursor marking the beginning of this line.
 * @property end Cursor marking the end of this line.
 */
data class LayoutLineRange(
    val width: Float,
    val start: LayoutCursor,
    val end: LayoutCursor
)

/**
 * Full result of [Pretext.layoutWithLines], including every materialized line.
 *
 * @property lineCount Number of visual lines.
 * @property height Total height in pixels (`lineCount * lineHeight`).
 * @property lines The materialized [LayoutLine] objects in visual order.
 */
data class LayoutLinesResult(
    val lineCount: Int,
    val height: Float,
    val lines: List<LayoutLine>
)

/**
 * Opaque handle returned by [Pretext.prepare].
 *
 * This is the fast-path preparation result: it stores all segment widths and break metadata
 * needed by [Pretext.layout], but does not retain the original segment strings. Pass it to
 * [Pretext.layout] for line-count and height calculations at any width without further
 * DOM reads, canvas calls, or string work.
 *
 * Instances are not reusable across different text or font configurations; call
 * [Pretext.prepare] again if either changes.
 *
 * @see Pretext.prepare
 * @see Pretext.layout
 */
class PreparedText internal constructor(internal val core: PreparedCore)

/**
 * Rich preparation handle returned by [Pretext.prepareWithSegments].
 *
 * Like [PreparedText], but also retains the segment string array so that line text can be
 * materialized by [Pretext.layoutWithLines], [Pretext.walkLineRanges], and
 * [Pretext.layoutNextLine].
 *
 * @property segments The ordered array of segment strings produced during preparation.
 * @see Pretext.prepareWithSegments
 * @see Pretext.layoutWithLines
 */
class PreparedTextWithSegments internal constructor(
    internal val core: PreparedCore,
    val segments: Array<String>
)

// Internal parallel-array storage
class PreparedCore(
    val widths: FloatArray,
    val lineEndFitAdvances: FloatArray,
    val lineEndPaintAdvances: FloatArray,
    val kinds: Array<SegmentBreakKind>,
    val simpleLineWalkFastPath: Boolean,
    val segLevels: ByteArray?,
    val breakableWidths: Array<FloatArray?>,
    val breakablePrefixWidths: Array<FloatArray?>,
    val discretionaryHyphenWidth: Float,
    val tabStopAdvance: Float,
    val chunks: Array<PreparedLineChunk>
)

data class PreparedLineChunk(
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
    val consumedEndSegmentIndex: Int
)

// Internal types
data class SegmentMetrics(
    var width: Float,
    var containsCJK: Boolean,
    var emojiCount: Int? = null,
    var graphemeWidths: FloatArray? = null,
    var graphemePrefixWidths: FloatArray? = null
)

data class InternalLayoutLine(
    val startSegmentIndex: Int,
    val startGraphemeIndex: Int,
    val endSegmentIndex: Int,
    val endGraphemeIndex: Int,
    val width: Float
)

data class WhiteSpaceProfile(
    val mode: WhiteSpaceMode,
    val preserveOrdinarySpaces: Boolean,
    val preserveHardBreaks: Boolean
)

data class SegmentationPiece(
    val text: String,
    val isWordLike: Boolean,
    val kind: SegmentBreakKind,
    val start: Int
)

data class MergedSegmentation(
    var len: Int,
    var texts: MutableList<String>,
    var isWordLike: MutableList<Boolean>,
    var kinds: MutableList<SegmentBreakKind>,
    var starts: MutableList<Int>
)

data class AnalysisChunk(
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
    val consumedEndSegmentIndex: Int
)

data class TextAnalysis(
    val normalized: String,
    val chunks: List<AnalysisChunk>,
    val len: Int,
    val texts: List<String>,
    val isWordLike: List<Boolean>,
    val kinds: List<SegmentBreakKind>,
    val starts: List<Int>
)

data class EngineProfile(
    val lineFitEpsilon: Float,
    val carryCJKAfterClosingQuote: Boolean,
    val preferPrefixWidthsForBreakableRuns: Boolean,
    val preferEarlySoftHyphenBreak: Boolean
) {
    companion object {
        val DEFAULT = EngineProfile(
            lineFitEpsilon = 0.005f,
            carryCJKAfterClosingQuote = false,
            preferPrefixWidthsForBreakableRuns = false,
            preferEarlySoftHyphenBreak = false
        )
    }
}

/**
 * Platform-agnostic interface for measuring the advance width of a text run.
 *
 * Implementations must return the pixel width for the given string rendered in the
 * current font and size. On Android, [io.github.matrixy.pretext.android.PaintTextMeasurer] provides
 * a concrete implementation backed by [android.text.TextPaint].
 *
 * ```kotlin
 * val measurer = object : TextMeasurer {
 *     override fun measureText(text: String): Float = paint.measureText(text)
 * }
 * ```
 *
 * @see io.github.matrixy.pretext.android.PaintTextMeasurer
 */
interface TextMeasurer {
    /**
     * Returns the advance width, in pixels, of [text] in the current font configuration.
     *
     * @param text The string to measure.
     * @return Width in pixels.
     */
    fun measureText(text: String): Float
}

/**
 * Platform-agnostic interface for word and grapheme segmentation.
 *
 * Pretext requires a segmenter to split text into word-like segments (for break
 * opportunities) and grapheme clusters (for intra-word breaking when overflow-wrap
 * applies). On Android, [io.github.matrixy.pretext.android.IcuTextSegmenter] provides a concrete
 * implementation backed by [android.icu.text.BreakIterator].
 *
 * Register an implementation via [Pretext.setSegmenter] before calling [Pretext.prepare].
 *
 * @see io.github.matrixy.pretext.android.IcuTextSegmenter
 * @see Pretext.setSegmenter
 */
interface TextSegmenter {
    /**
     * Splits [text] into word-level segments with word-like classification.
     *
     * @param text The input string.
     * @return Ordered list of [WordSegment]s covering the entire input.
     */
    fun segmentWords(text: String): List<WordSegment>

    /**
     * Splits [text] into grapheme clusters.
     *
     * @param text The input string.
     * @return Ordered list of grapheme-cluster strings.
     */
    fun segmentGraphemes(text: String): List<String>
}

/**
 * A single word-level segment produced by [TextSegmenter.segmentWords].
 *
 * @property text The segment's text content.
 * @property index The character offset of this segment within the original string.
 * @property isWordLike `true` if this segment is a word or number (as opposed to whitespace or punctuation).
 */
data class WordSegment(
    val text: String,
    val index: Int,
    val isWordLike: Boolean
)
