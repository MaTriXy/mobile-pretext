import Foundation
import CoreText

// MARK: - Public Types

/// Controls how whitespace characters are handled during text analysis.
///
/// Mirrors the CSS `white-space` property for the two supported modes.
public enum WhiteSpaceMode: Sendable {
    /// Standard collapsing behavior (CSS `white-space: normal`). Consecutive spaces collapse; newlines are treated as soft breaks.
    case normal
    /// Preserves spaces, tabs, and hard breaks (CSS `white-space: pre-wrap`). Lines still wrap at the container edge.
    case preWrap
}

/// Classification of a segment's break behavior within the internal line-breaking model.
///
/// Each prepared segment carries one of these kinds, which determines how it participates
/// in line fitting, collapsing, and break-opportunity logic.
public enum SegmentBreakKind: Sendable {
    case text
    case space
    case preservedSpace
    case tab
    case glue          // NBSP, NNBSP, WJ
    case zeroWidthBreak // ZWSP
    case softHyphen
    case hardBreak
}

/// A lightweight font descriptor used for text measurement.
///
/// `FontSpec` identifies a font by PostScript name and point size. It serves as the
/// cache key for segment metrics, so reusing the same spec across multiple `prepare` calls
/// avoids redundant CoreText measurements.
///
/// ```swift
/// let font = FontSpec(name: "Helvetica Neue", size: 16)
/// ```
public struct FontSpec: Hashable, Sendable {
    /// PostScript name of the font (e.g. `"Helvetica Neue"`, `"Georgia"`).
    public let name: String
    /// Point size.
    public let size: CGFloat

    /// Creates a font descriptor.
    /// - Parameters:
    ///   - name: PostScript name of the font.
    ///   - size: Point size.
    public init(name: String, size: CGFloat) {
        self.name = name
        self.size = size
    }
}

/// Options that control the text analysis phase.
public struct PrepareOptions: Sendable {
    /// Whitespace handling mode. Defaults to ``WhiteSpaceMode/normal``.
    public var whiteSpace: WhiteSpaceMode

    /// Creates prepare options.
    /// - Parameter whiteSpace: Whitespace handling mode. Defaults to `.normal`.
    public init(whiteSpace: WhiteSpaceMode = .normal) {
        self.whiteSpace = whiteSpace
    }
}

/// The result of a fast layout pass: line count and total height.
///
/// Returned by ``Pretext/layout(_:maxWidth:lineHeight:)``. This is the minimal
/// output needed for sizing a text container without materializing individual lines.
public struct LayoutResult: Sendable {
    /// Number of visual lines the text occupies at the given width.
    public let lineCount: Int
    /// Total height (`lineCount * lineHeight`).
    public let height: Double
}

/// A position within a prepared text, used to mark line boundaries.
///
/// Cursors identify a point between two graphemes by referencing the segment and
/// grapheme offset within that segment. Pass a cursor as the `start` parameter
/// to ``Pretext/layoutNextLine(_:start:maxWidth:)`` for variable-width iteration.
public struct LayoutCursor: Sendable, Equatable {
    /// Index into the prepared segment array.
    public let segmentIndex: Int
    /// Grapheme offset within the segment (0 means the segment boundary).
    public let graphemeIndex: Int

    /// Creates a cursor at the given position.
    /// - Parameters:
    ///   - segmentIndex: Index into the prepared segment array.
    ///   - graphemeIndex: Grapheme offset within that segment.
    public init(segmentIndex: Int, graphemeIndex: Int) {
        self.segmentIndex = segmentIndex
        self.graphemeIndex = graphemeIndex
    }
}

/// A materialized line of text with its content, measured width, and boundary cursors.
///
/// Returned by ``Pretext/layoutWithLines(_:maxWidth:lineHeight:)`` and
/// ``Pretext/layoutNextLine(_:start:maxWidth:)``.
public struct LayoutLine: Sendable {
    /// The visible text content of this line (soft hyphens render as `-` when broken).
    public let text: String
    /// Measured pixel width of the line content.
    public let width: Double
    /// Cursor marking the start of this line in the prepared text.
    public let start: LayoutCursor
    /// Cursor marking the end of this line (and the start of the next).
    public let end: LayoutCursor
}

/// A line range without materialized text — just width and boundary cursors.
///
/// Returned by ``Pretext/walkLineRanges(_:maxWidth:onLine:)``. Use this when you
/// need line geometry (width, cursor positions) without paying for string construction.
public struct LayoutLineRange: Sendable {
    /// Measured pixel width of the line content.
    public let width: Double
    /// Cursor marking the start of this line.
    public let start: LayoutCursor
    /// Cursor marking the end of this line.
    public let end: LayoutCursor
}

/// Complete layout result including all materialized lines.
///
/// Returned by ``Pretext/layoutWithLines(_:maxWidth:lineHeight:)``.
public struct LayoutLinesResult: Sendable {
    /// Number of visual lines.
    public let lineCount: Int
    /// Total height (`lineCount * lineHeight`).
    public let height: Double
    /// Each line's text, width, and boundary cursors.
    public let lines: [LayoutLine]
}

/// Timing and segment-count diagnostics from a `prepare` call.
///
/// Useful for profiling the one-time text analysis cost.
public struct PrepareProfile: Sendable {
    /// Time spent in the text-analysis phase (ms).
    public let analysisMs: Double
    /// Time spent in the measurement phase (ms).
    public let measureMs: Double
    /// Total prepare time (ms).
    public let totalMs: Double
    /// Number of segments produced by the analysis phase.
    public let analysisSegments: Int
    /// Number of segments after CJK splitting and measurement.
    public let preparedSegments: Int
    /// Number of segments that carry intra-segment break data.
    public let breakableSegments: Int
}

// MARK: - Opaque Public Handles

/// An opaque handle to analyzed and measured text, ready for layout.
///
/// Created by ``Pretext/prepare(_:font:options:)``. This is the fast-path handle:
/// it carries cached segment widths and break metadata but does not expose segment
/// strings. Pass it to ``Pretext/layout(_:maxWidth:lineHeight:)`` for pure-arithmetic
/// line counting.
///
/// `PreparedText` is `Sendable` and can be shared across threads. Prepare once,
/// then call `layout` on every width change.
public struct PreparedText: Sendable {
    internal let core: PreparedCore
}

/// A rich variant of ``PreparedText`` that also carries the segment string array.
///
/// Created by ``Pretext/prepareWithSegments(_:font:options:)``. Required for APIs
/// that materialize line text: ``Pretext/layoutWithLines(_:maxWidth:lineHeight:)``,
/// ``Pretext/layoutNextLine(_:start:maxWidth:)``, and
/// ``Pretext/walkLineRanges(_:maxWidth:onLine:)``.
public struct PreparedTextWithSegments: Sendable {
    internal let core: PreparedCore
    /// The ordered segment strings produced during analysis and measurement.
    public let segments: [String]
}

// MARK: - Internal Types

struct PreparedCore: Sendable {
    let widths: [Double]
    let lineEndFitAdvances: [Double]
    let lineEndPaintAdvances: [Double]
    let kinds: [SegmentBreakKind]
    let simpleLineWalkFastPath: Bool
    let segLevels: [Int8]?
    let breakableWidths: [[Double]?]
    let breakablePrefixWidths: [[Double]?]
    let discretionaryHyphenWidth: Double
    let tabStopAdvance: Double
    let chunks: [PreparedLineChunk]
}

struct PreparedLineChunk: Sendable {
    let startSegmentIndex: Int
    let endSegmentIndex: Int
    let consumedEndSegmentIndex: Int
}

struct SegmentMetrics {
    var width: Double
    var containsCJK: Bool
    var emojiCount: Int?
    var graphemeWidths: [Double]?
    var graphemePrefixWidths: [Double]?
}

struct InternalLayoutLine {
    let startSegmentIndex: Int
    let startGraphemeIndex: Int
    let endSegmentIndex: Int
    let endGraphemeIndex: Int
    let width: Double
}

struct WhiteSpaceProfile {
    let mode: WhiteSpaceMode
    let preserveOrdinarySpaces: Bool
    let preserveHardBreaks: Bool
}

struct SegmentationPiece {
    let text: String
    let isWordLike: Bool
    let kind: SegmentBreakKind
    let start: Int
}

struct MergedSegmentation {
    var len: Int
    var texts: [String]
    var isWordLike: [Bool]
    var kinds: [SegmentBreakKind]
    var starts: [Int]
}

struct AnalysisChunk {
    let startSegmentIndex: Int
    let endSegmentIndex: Int
    let consumedEndSegmentIndex: Int
}

struct TextAnalysis {
    let normalized: String
    let chunks: [AnalysisChunk]
    let len: Int
    let texts: [String]
    let isWordLike: [Bool]
    let kinds: [SegmentBreakKind]
    let starts: [Int]
}

struct EngineProfile {
    let lineFitEpsilon: Double
    let carryCJKAfterClosingQuote: Bool
    let preferPrefixWidthsForBreakableRuns: Bool
    let preferEarlySoftHyphenBreak: Bool

    static let `default` = EngineProfile(
        lineFitEpsilon: 0.005,
        carryCJKAfterClosingQuote: false,
        preferPrefixWidthsForBreakableRuns: false,
        preferEarlySoftHyphenBreak: false
    )
}

// MARK: - Text Measurer Protocol

protocol TextMeasurer {
    func measureText(_ text: String) -> Double
}
