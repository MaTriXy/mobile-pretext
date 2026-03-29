import Foundation

// MARK: - Public API

/// Pure-arithmetic text measurement and layout engine.
///
/// `Pretext` is a namespace (caseless enum) that exposes a two-phase API:
///
/// 1. **Prepare** — analyze and measure text once using CoreText.
/// 2. **Layout** — compute line breaks and height using only cached arithmetic.
///
/// The prepare phase is O(segments) with CoreText calls; the layout phase is
/// sub-microsecond per call with no font or canvas work.
///
/// ```swift
/// let font = FontSpec(name: "Helvetica Neue", size: 16)
/// let prepared = Pretext.prepare("Hello, world!", font: font)
/// let result = Pretext.layout(prepared, maxWidth: 300, lineHeight: 22)
/// ```
public enum Pretext {

    /// Analyzes and measures text, returning an opaque handle for fast layout.
    ///
    /// This is the recommended entry point for most use cases. The returned
    /// ``PreparedText`` caches segment widths and break metadata so that
    /// subsequent ``layout(_:maxWidth:lineHeight:)`` calls are pure arithmetic.
    ///
    /// Call once per text/font combination; call ``layout(_:maxWidth:lineHeight:)``
    /// on every width change.
    ///
    /// - Parameters:
    ///   - text: The input string. Supports all scripts, emoji, and bidirectional text.
    ///   - font: Font descriptor for CoreText measurement.
    ///   - options: Whitespace mode and other analysis options.
    /// - Returns: An opaque prepared handle.
    public static func prepare(_ text: String, font: FontSpec, options: PrepareOptions = PrepareOptions()) -> PreparedText {
        let analysis = analyzeText(text, profile: .default, whiteSpace: options.whiteSpace)
        return PreparedText(core: measureAnalysis(analysis, font: font, includeSegments: false).core)
    }

    /// Analyzes and measures text, returning a rich handle that includes segment strings.
    ///
    /// Use this variant when you need line-level text content via
    /// ``layoutWithLines(_:maxWidth:lineHeight:)``, ``layoutNextLine(_:start:maxWidth:)``,
    /// or ``walkLineRanges(_:maxWidth:onLine:)``.
    ///
    /// - Parameters:
    ///   - text: The input string.
    ///   - font: Font descriptor for CoreText measurement.
    ///   - options: Whitespace mode and other analysis options.
    /// - Returns: A prepared handle with accessible segment data.
    public static func prepareWithSegments(_ text: String, font: FontSpec, options: PrepareOptions = PrepareOptions()) -> PreparedTextWithSegments {
        let analysis = analyzeText(text, profile: .default, whiteSpace: options.whiteSpace)
        let result = measureAnalysis(analysis, font: font, includeSegments: true)
        return PreparedTextWithSegments(core: result.core, segments: result.segments ?? [])
    }

    /// Computes line count and total height — the hot path.
    ///
    /// This is pure arithmetic over cached segment widths: no CoreText calls, no string
    /// work, no allocations. Safe to call on every frame or resize event.
    ///
    /// - Parameters:
    ///   - prepared: A handle from ``prepare(_:font:options:)``.
    ///   - maxWidth: Available horizontal space in points.
    ///   - lineHeight: Fixed line height in points.
    /// - Returns: Line count and total height.
    public static func layout(_ prepared: PreparedText, maxWidth: Double, lineHeight: Double) -> LayoutResult {
        let lineCount = countPreparedLines(prepared.core, maxWidth: maxWidth)
        return LayoutResult(lineCount: lineCount, height: Double(lineCount) * lineHeight)
    }

    /// Computes layout and returns every line's text, width, and boundary cursors.
    ///
    /// Use when you need the actual string content of each line (e.g. for custom rendering).
    /// Requires a ``PreparedTextWithSegments`` from ``prepareWithSegments(_:font:options:)``.
    ///
    /// - Parameters:
    ///   - prepared: A rich prepared handle.
    ///   - maxWidth: Available horizontal space in points.
    ///   - lineHeight: Fixed line height in points.
    /// - Returns: Line count, total height, and an array of materialized lines.
    public static func layoutWithLines(_ prepared: PreparedTextWithSegments, maxWidth: Double, lineHeight: Double) -> LayoutLinesResult {
        var lines: [LayoutLine] = []
        guard !prepared.core.widths.isEmpty else {
            return LayoutLinesResult(lineCount: 0, height: 0, lines: [])
        }

        let lineCount = walkPreparedLines(prepared.core, maxWidth: maxWidth) { internalLine in
            lines.append(materializeLayoutLine(prepared, line: internalLine))
        }

        return LayoutLinesResult(lineCount: lineCount, height: Double(lineCount) * lineHeight, lines: lines)
    }

    /// Walks all lines without materializing text strings.
    ///
    /// Calls `onLine` for each line with its width and boundary cursors. This is the
    /// non-materializing batch API — useful for aggregate geometry work like shrinkwrap
    /// or hit-testing where you need line widths but not the text content.
    ///
    /// - Parameters:
    ///   - prepared: A rich prepared handle.
    ///   - maxWidth: Available horizontal space in points.
    ///   - onLine: Callback invoked once per line with a ``LayoutLineRange``.
    /// - Returns: Total number of lines.
    @discardableResult
    public static func walkLineRanges(_ prepared: PreparedTextWithSegments, maxWidth: Double, onLine: @escaping (LayoutLineRange) -> Void) -> Int {
        guard !prepared.core.widths.isEmpty else { return 0 }
        return walkPreparedLines(prepared.core, maxWidth: maxWidth) { line in
            onLine(toLayoutLineRange(line))
        }
    }

    /// Lays out a single line starting from the given cursor, for variable-width iteration.
    ///
    /// Use this to flow text around obstacles or into columns with varying widths.
    /// Pass a different `maxWidth` on each call. Returns `nil` when the text is exhausted.
    ///
    /// ```swift
    /// var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
    /// while let line = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: width) {
    ///     cursor = line.end
    /// }
    /// ```
    ///
    /// - Parameters:
    ///   - prepared: A rich prepared handle.
    ///   - start: Cursor position to begin the line from.
    ///   - maxWidth: Available horizontal space for this line.
    /// - Returns: The next materialized line, or `nil` if all text has been consumed.
    public static func layoutNextLine(_ prepared: PreparedTextWithSegments, start: LayoutCursor, maxWidth: Double) -> LayoutLine? {
        let range = layoutNextLineRangePublic(prepared.core, start: start, maxWidth: maxWidth)
        guard let range = range else { return nil }
        return materializeLine(prepared, range: range)
    }

    /// Clears the shared segment metrics cache and resets the word segmenter locale.
    ///
    /// Call after a font change or when memory pressure requires releasing cached measurements.
    /// Subsequent `prepare` calls will re-measure from scratch.
    public static func clearCache() {
        sharedMeasurementCache.clear()
        setSegmenterLocale(nil)
    }

    /// Sets the locale used by the word segmenter for future `prepare` calls.
    ///
    /// Different locales can produce different word boundaries (e.g. Thai, Lao, Khmer).
    /// Pass `nil` to revert to the system default. Also clears the measurement cache,
    /// since segment boundaries may change.
    ///
    /// - Parameter locale: The locale to use, or `nil` for the system default.
    public static func setLocale(_ locale: Locale?) {
        setSegmenterLocale(locale)
        sharedMeasurementCache.clear()
    }
}

// MARK: - Internal Orchestration

struct MeasureResult {
    let core: PreparedCore
    let segments: [String]?
}

func measureAnalysis(_ analysis: TextAnalysis, font: FontSpec, includeSegments: Bool) -> MeasureResult {
    let measurer = CoreTextMeasurer(font: font)
    let cache = sharedMeasurementCache
    let profile = EngineProfile.default

    let hyphenWidth = cache.getMetrics(segment: "-", font: font, measurer: measurer).width
    let spaceWidth = cache.getMetrics(segment: " ", font: font, measurer: measurer).width
    let tabStopAdvance = spaceWidth * 8

    guard analysis.len > 0 else {
        return MeasureResult(
            core: PreparedCore(
                widths: [], lineEndFitAdvances: [], lineEndPaintAdvances: [],
                kinds: [], simpleLineWalkFastPath: true, segLevels: nil,
                breakableWidths: [], breakablePrefixWidths: [],
                discretionaryHyphenWidth: hyphenWidth, tabStopAdvance: tabStopAdvance,
                chunks: []
            ),
            segments: includeSegments ? [] : nil
        )
    }

    var widths: [Double] = []
    var lineEndFitAdvances: [Double] = []
    var lineEndPaintAdvances: [Double] = []
    var kinds: [SegmentBreakKind] = []
    var simpleLineWalkFastPath = analysis.chunks.count <= 1
    var segStarts: [Int]? = includeSegments ? [] : nil
    var breakableWidths: [[Double]?] = []
    var breakablePrefixWidths: [[Double]?] = []
    var segments: [String]? = includeSegments ? [] : nil
    var preparedStartByAnalysisIndex = [Int](repeating: 0, count: analysis.len)
    var preparedEndByAnalysisIndex = [Int](repeating: 0, count: analysis.len)

    func pushMeasuredSegment(_ text: String, _ width: Double, _ fitAdv: Double, _ paintAdv: Double,
                             _ kind: SegmentBreakKind, _ start: Int,
                             _ breakable: [Double]?, _ breakablePrefix: [Double]?) {
        if kind != .text && kind != .space && kind != .zeroWidthBreak {
            simpleLineWalkFastPath = false
        }
        widths.append(width)
        lineEndFitAdvances.append(fitAdv)
        lineEndPaintAdvances.append(paintAdv)
        kinds.append(kind)
        segStarts?.append(start)
        breakableWidths.append(breakable)
        breakablePrefixWidths.append(breakablePrefix)
        segments?.append(text)
    }

    for mi in 0..<analysis.len {
        preparedStartByAnalysisIndex[mi] = widths.count
        let segText = analysis.texts[mi]
        let segWordLike = analysis.isWordLike[mi]
        let segKind = analysis.kinds[mi]
        let segStart = analysis.starts[mi]

        if segKind == .softHyphen {
            pushMeasuredSegment(segText, 0, hyphenWidth, hyphenWidth, segKind, segStart, nil, nil)
            preparedEndByAnalysisIndex[mi] = widths.count
            continue
        }

        if segKind == .hardBreak {
            pushMeasuredSegment(segText, 0, 0, 0, segKind, segStart, nil, nil)
            preparedEndByAnalysisIndex[mi] = widths.count
            continue
        }

        if segKind == .tab {
            pushMeasuredSegment(segText, 0, 0, 0, segKind, segStart, nil, nil)
            preparedEndByAnalysisIndex[mi] = widths.count
            continue
        }

        let segMetrics = cache.getMetrics(segment: segText, font: font, measurer: measurer)

        // CJK grapheme splitting
        if segKind == .text && segMetrics.containsCJK {
            var unitText = ""
            var unitStart = segStart

            let graphemes = segmentGraphemes(segText)
            var graphemeOffset = 0
            for grapheme in graphemes {
                if unitText.isEmpty {
                    unitText = grapheme
                    unitStart = segStart + graphemeOffset
                    graphemeOffset += grapheme.utf16.count
                    continue
                }

                let graphemeChar = grapheme.first!
                let unitFirstChar = unitText.first!

                if kinsokuEnd.contains(unitFirstChar) ||
                   kinsokuStart.contains(graphemeChar) ||
                   leftStickyPunctuation.contains(graphemeChar) ||
                   (profile.carryCJKAfterClosingQuote && isCJK(grapheme) && endsWithClosingQuote(unitText)) {
                    unitText += grapheme
                    graphemeOffset += grapheme.utf16.count
                    continue
                }

                let unitMetrics = cache.getMetrics(segment: unitText, font: font, measurer: measurer)
                pushMeasuredSegment(unitText, unitMetrics.width, unitMetrics.width, unitMetrics.width, .text, unitStart, nil, nil)
                unitText = grapheme
                unitStart = segStart + graphemeOffset
                graphemeOffset += grapheme.utf16.count
            }

            if !unitText.isEmpty {
                let unitMetrics = cache.getMetrics(segment: unitText, font: font, measurer: measurer)
                pushMeasuredSegment(unitText, unitMetrics.width, unitMetrics.width, unitMetrics.width, .text, unitStart, nil, nil)
            }
            preparedEndByAnalysisIndex[mi] = widths.count
            continue
        }

        let w = segMetrics.width
        let lineEndFitAdvance: Double = (segKind == .space || segKind == .preservedSpace || segKind == .zeroWidthBreak) ? 0 : w
        let lineEndPaintAdvance: Double = (segKind == .space || segKind == .zeroWidthBreak) ? 0 : w

        if segWordLike && segText.count > 1 {
            let gWidths = cache.getGraphemeWidths(segment: segText, font: font, measurer: measurer)
            let gPrefixWidths = profile.preferPrefixWidthsForBreakableRuns
                ? cache.getGraphemePrefixWidths(segment: segText, font: font, measurer: measurer)
                : nil
            pushMeasuredSegment(segText, w, lineEndFitAdvance, lineEndPaintAdvance, segKind, segStart, gWidths, gPrefixWidths)
        } else {
            pushMeasuredSegment(segText, w, lineEndFitAdvance, lineEndPaintAdvance, segKind, segStart, nil, nil)
        }
        preparedEndByAnalysisIndex[mi] = widths.count
    }

    let preparedChunks = mapAnalysisChunksToPreparedChunks(analysis.chunks, preparedStartByAnalysisIndex, preparedEndByAnalysisIndex)
    let segLevels: [Int8]? = segStarts != nil ? computeSegmentLevels(normalized: analysis.normalized, segStarts: segStarts!) : nil

    return MeasureResult(
        core: PreparedCore(
            widths: widths, lineEndFitAdvances: lineEndFitAdvances, lineEndPaintAdvances: lineEndPaintAdvances,
            kinds: kinds, simpleLineWalkFastPath: simpleLineWalkFastPath, segLevels: segLevels,
            breakableWidths: breakableWidths, breakablePrefixWidths: breakablePrefixWidths,
            discretionaryHyphenWidth: hyphenWidth, tabStopAdvance: tabStopAdvance, chunks: preparedChunks
        ),
        segments: segments
    )
}

private func mapAnalysisChunksToPreparedChunks(
    _ chunks: [AnalysisChunk],
    _ startMap: [Int],
    _ endMap: [Int]
) -> [PreparedLineChunk] {
    chunks.map { chunk in
        let start = chunk.startSegmentIndex < startMap.count ? startMap[chunk.startSegmentIndex] : (endMap.last ?? 0)
        let end = chunk.endSegmentIndex < startMap.count ? startMap[chunk.endSegmentIndex] : (endMap.last ?? 0)
        let consumed = chunk.consumedEndSegmentIndex < startMap.count ? startMap[chunk.consumedEndSegmentIndex] : (endMap.last ?? 0)
        return PreparedLineChunk(startSegmentIndex: start, endSegmentIndex: end, consumedEndSegmentIndex: consumed)
    }
}

// MARK: - Line Materialization

private func toLayoutLineRange(_ line: InternalLayoutLine) -> LayoutLineRange {
    LayoutLineRange(
        width: line.width,
        start: LayoutCursor(segmentIndex: line.startSegmentIndex, graphemeIndex: line.startGraphemeIndex),
        end: LayoutCursor(segmentIndex: line.endSegmentIndex, graphemeIndex: line.endGraphemeIndex)
    )
}

private func lineHasDiscretionaryHyphen(_ kinds: [SegmentBreakKind], _ startSeg: Int, _ startGrapheme: Int, _ endSeg: Int) -> Bool {
    endSeg > 0 && kinds[endSeg - 1] == .softHyphen && !(startSeg == endSeg && startGrapheme > 0)
}

private func buildLineText(_ segments: [String], _ kinds: [SegmentBreakKind],
                            _ startSeg: Int, _ startGrapheme: Int,
                            _ endSeg: Int, _ endGrapheme: Int) -> String {
    var text = ""
    let hasHyphen = lineHasDiscretionaryHyphen(kinds, startSeg, startGrapheme, endSeg)

    for i in startSeg..<endSeg {
        if kinds[i] == .softHyphen || kinds[i] == .hardBreak { continue }
        if i == startSeg && startGrapheme > 0 {
            let graphemes = segmentGraphemes(segments[i])
            text += graphemes[startGrapheme...].joined()
        } else {
            text += segments[i]
        }
    }

    if endGrapheme > 0 {
        if hasHyphen { text += "-" }
        let graphemes = segmentGraphemes(segments[endSeg])
        let from = (startSeg == endSeg) ? startGrapheme : 0
        text += graphemes[from..<endGrapheme].joined()
    } else if hasHyphen {
        text += "-"
    }

    return text
}

private func materializeLayoutLine(_ prepared: PreparedTextWithSegments, line: InternalLayoutLine) -> LayoutLine {
    LayoutLine(
        text: buildLineText(prepared.segments, prepared.core.kinds,
                            line.startSegmentIndex, line.startGraphemeIndex,
                            line.endSegmentIndex, line.endGraphemeIndex),
        width: line.width,
        start: LayoutCursor(segmentIndex: line.startSegmentIndex, graphemeIndex: line.startGraphemeIndex),
        end: LayoutCursor(segmentIndex: line.endSegmentIndex, graphemeIndex: line.endGraphemeIndex)
    )
}

private func materializeLine(_ prepared: PreparedTextWithSegments, range: LayoutLineRange) -> LayoutLine {
    LayoutLine(
        text: buildLineText(prepared.segments, prepared.core.kinds,
                            range.start.segmentIndex, range.start.graphemeIndex,
                            range.end.segmentIndex, range.end.graphemeIndex),
        width: range.width,
        start: range.start,
        end: range.end
    )
}
