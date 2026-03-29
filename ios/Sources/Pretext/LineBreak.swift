import Foundation

// MARK: - Line Break Helpers

private func canBreakAfter(_ kind: SegmentBreakKind) -> Bool {
    kind == .space ||
    kind == .preservedSpace ||
    kind == .tab ||
    kind == .zeroWidthBreak ||
    kind == .softHyphen
}

private func isSimpleCollapsibleSpace(_ kind: SegmentBreakKind) -> Bool {
    kind == .space
}

private func getTabAdvance(_ lineWidth: Double, _ tabStopAdvance: Double) -> Double {
    if tabStopAdvance <= 0 { return 0 }
    let remainder = lineWidth.truncatingRemainder(dividingBy: tabStopAdvance)
    if abs(remainder) <= 1e-6 { return tabStopAdvance }
    return tabStopAdvance - remainder
}

private func getBreakableAdvance(
    _ graphemeWidths: [Double],
    _ graphemePrefixWidths: [Double]?,
    _ graphemeIndex: Int,
    _ preferPrefixWidths: Bool
) -> Double {
    if !preferPrefixWidths || graphemePrefixWidths == nil {
        return graphemeWidths[graphemeIndex]
    }
    return graphemePrefixWidths![graphemeIndex] - (graphemeIndex > 0 ? graphemePrefixWidths![graphemeIndex - 1] : 0)
}

private func fitSoftHyphenBreak(
    _ graphemeWidths: [Double],
    _ initialWidth: Double,
    _ maxWidth: Double,
    _ lineFitEpsilon: Double,
    _ discretionaryHyphenWidth: Double,
    _ cumulativeWidths: Bool
) -> (fitCount: Int, fittedWidth: Double) {
    var fitCount = 0
    var fittedWidth = initialWidth

    while fitCount < graphemeWidths.count {
        let nextWidth = cumulativeWidths
            ? initialWidth + graphemeWidths[fitCount]
            : fittedWidth + graphemeWidths[fitCount]
        let nextLineWidth = fitCount + 1 < graphemeWidths.count
            ? nextWidth + discretionaryHyphenWidth
            : nextWidth
        if nextLineWidth > maxWidth + lineFitEpsilon { break }
        fittedWidth = nextWidth
        fitCount += 1
    }

    return (fitCount, fittedWidth)
}

private func findChunkIndexForStart(_ prepared: PreparedCore, _ segmentIndex: Int) -> Int {
    for i in 0..<prepared.chunks.count {
        let chunk = prepared.chunks[i]
        if segmentIndex < chunk.consumedEndSegmentIndex { return i }
    }
    return -1
}

func normalizeLineStart(
    _ prepared: PreparedCore,
    _ start: LayoutCursor
) -> LayoutCursor? {
    var segmentIndex = start.segmentIndex
    let graphemeIndex = start.graphemeIndex

    if segmentIndex >= prepared.widths.count { return nil }
    if graphemeIndex > 0 { return start }

    let chunkIndex = findChunkIndexForStart(prepared, segmentIndex)
    if chunkIndex < 0 { return nil }

    let chunk = prepared.chunks[chunkIndex]
    if chunk.startSegmentIndex == chunk.endSegmentIndex && segmentIndex == chunk.startSegmentIndex {
        return LayoutCursor(segmentIndex: segmentIndex, graphemeIndex: 0)
    }

    if segmentIndex < chunk.startSegmentIndex { segmentIndex = chunk.startSegmentIndex }
    while segmentIndex < chunk.endSegmentIndex {
        let kind = prepared.kinds[segmentIndex]
        if kind != .space && kind != .zeroWidthBreak && kind != .softHyphen {
            return LayoutCursor(segmentIndex: segmentIndex, graphemeIndex: 0)
        }
        segmentIndex += 1
    }

    if chunk.consumedEndSegmentIndex >= prepared.widths.count { return nil }
    return LayoutCursor(segmentIndex: chunk.consumedEndSegmentIndex, graphemeIndex: 0)
}

// MARK: - Count Prepared Lines

func countPreparedLines(_ prepared: PreparedCore, maxWidth: Double) -> Int {
    if prepared.simpleLineWalkFastPath {
        return countPreparedLinesSimple(prepared, maxWidth: maxWidth)
    }
    return walkPreparedLines(prepared, maxWidth: maxWidth)
}

private func countPreparedLinesSimple(_ prepared: PreparedCore, maxWidth: Double) -> Int {
    let widths = prepared.widths
    let kinds = prepared.kinds
    let breakableWidths = prepared.breakableWidths
    let breakablePrefixWidths = prepared.breakablePrefixWidths
    if widths.isEmpty { return 0 }

    let profile = EngineProfile.default
    let lineFitEpsilon = profile.lineFitEpsilon

    var lineCount = 0
    var lineW = 0.0
    var hasContent = false

    func placeOnFreshLine(_ segmentIndex: Int) {
        let w = widths[segmentIndex]
        if w > maxWidth && breakableWidths[segmentIndex] != nil {
            let gWidths = breakableWidths[segmentIndex]!
            let gPrefixWidths = breakablePrefixWidths[segmentIndex] ?? nil
            lineW = 0
            for g in 0..<gWidths.count {
                let gw = getBreakableAdvance(
                    gWidths, gPrefixWidths, g,
                    profile.preferPrefixWidthsForBreakableRuns
                )
                if lineW > 0 && lineW + gw > maxWidth + lineFitEpsilon {
                    lineCount += 1
                    lineW = gw
                } else {
                    if lineW == 0 { lineCount += 1 }
                    lineW += gw
                }
            }
        } else {
            lineW = w
            lineCount += 1
        }
        hasContent = true
    }

    for i in 0..<widths.count {
        let w = widths[i]
        let kind = kinds[i]

        if !hasContent {
            placeOnFreshLine(i)
            continue
        }

        let newW = lineW + w
        if newW > maxWidth + lineFitEpsilon {
            if isSimpleCollapsibleSpace(kind) { continue }
            lineW = 0
            hasContent = false
            placeOnFreshLine(i)
            continue
        }

        lineW = newW
    }

    if !hasContent { return lineCount + 1 }
    return lineCount
}

// MARK: - Walk Prepared Lines (Simple)

private func walkPreparedLinesSimple(
    _ prepared: PreparedCore,
    maxWidth: Double,
    onLine: ((InternalLayoutLine) -> Void)? = nil
) -> Int {
    let widths = prepared.widths
    let kinds = prepared.kinds
    let breakableWidths = prepared.breakableWidths
    let breakablePrefixWidths = prepared.breakablePrefixWidths
    if widths.isEmpty { return 0 }

    let profile = EngineProfile.default
    let lineFitEpsilon = profile.lineFitEpsilon

    var lineCount = 0
    var lineW = 0.0
    var hasContent = false
    var lineStartSegmentIndex = 0
    var lineStartGraphemeIndex = 0
    var lineEndSegmentIndex = 0
    var lineEndGraphemeIndex = 0
    var pendingBreakSegmentIndex = -1
    var pendingBreakPaintWidth = 0.0

    func clearPendingBreak() {
        pendingBreakSegmentIndex = -1
        pendingBreakPaintWidth = 0
    }

    func emitCurrentLine(
        endSeg: Int? = nil, endGrapheme: Int? = nil, width: Double? = nil
    ) {
        lineCount += 1
        onLine?(InternalLayoutLine(
            startSegmentIndex: lineStartSegmentIndex,
            startGraphemeIndex: lineStartGraphemeIndex,
            endSegmentIndex: endSeg ?? lineEndSegmentIndex,
            endGraphemeIndex: endGrapheme ?? lineEndGraphemeIndex,
            width: width ?? lineW
        ))
        lineW = 0
        hasContent = false
        clearPendingBreak()
    }

    func startLineAtSegment(_ segmentIndex: Int, _ width: Double) {
        hasContent = true
        lineStartSegmentIndex = segmentIndex
        lineStartGraphemeIndex = 0
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
        lineW = width
    }

    func startLineAtGrapheme(_ segmentIndex: Int, _ graphemeIndex: Int, _ width: Double) {
        hasContent = true
        lineStartSegmentIndex = segmentIndex
        lineStartGraphemeIndex = graphemeIndex
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = graphemeIndex + 1
        lineW = width
    }

    func appendWholeSegment(_ segmentIndex: Int, _ width: Double) {
        if !hasContent {
            startLineAtSegment(segmentIndex, width)
            return
        }
        lineW += width
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
    }

    func updatePendingBreak(_ segmentIndex: Int, _ segmentWidth: Double) {
        if !canBreakAfter(kinds[segmentIndex]) { return }
        pendingBreakSegmentIndex = segmentIndex + 1
        pendingBreakPaintWidth = lineW - segmentWidth
    }

    func appendBreakableSegment(_ segmentIndex: Int) {
        appendBreakableSegmentFrom(segmentIndex, 0)
    }

    func appendBreakableSegmentFrom(_ segmentIndex: Int, _ startGraphemeIdx: Int) {
        let gWidths = breakableWidths[segmentIndex]!
        let gPrefixWidths = breakablePrefixWidths[segmentIndex] ?? nil
        for g in startGraphemeIdx..<gWidths.count {
            let gw = getBreakableAdvance(
                gWidths, gPrefixWidths, g,
                profile.preferPrefixWidthsForBreakableRuns
            )

            if !hasContent {
                startLineAtGrapheme(segmentIndex, g, gw)
                continue
            }

            if lineW + gw > maxWidth + lineFitEpsilon {
                emitCurrentLine()
                startLineAtGrapheme(segmentIndex, g, gw)
            } else {
                lineW += gw
                lineEndSegmentIndex = segmentIndex
                lineEndGraphemeIndex = g + 1
            }
        }

        if hasContent && lineEndSegmentIndex == segmentIndex && lineEndGraphemeIndex == gWidths.count {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
        }
    }

    var i = 0
    while i < widths.count {
        let w = widths[i]
        let kind = kinds[i]

        if !hasContent {
            if w > maxWidth && breakableWidths[i] != nil {
                appendBreakableSegment(i)
            } else {
                startLineAtSegment(i, w)
            }
            updatePendingBreak(i, w)
            i += 1
            continue
        }

        let newW = lineW + w
        if newW > maxWidth + lineFitEpsilon {
            if canBreakAfter(kind) {
                appendWholeSegment(i, w)
                emitCurrentLine(endSeg: i + 1, endGrapheme: 0, width: lineW - w)
                i += 1
                continue
            }

            if pendingBreakSegmentIndex >= 0 {
                emitCurrentLine(endSeg: pendingBreakSegmentIndex, endGrapheme: 0, width: pendingBreakPaintWidth)
                continue
            }

            if w > maxWidth && breakableWidths[i] != nil {
                emitCurrentLine()
                appendBreakableSegment(i)
                i += 1
                continue
            }

            emitCurrentLine()
            continue
        }

        appendWholeSegment(i, w)
        updatePendingBreak(i, w)
        i += 1
    }

    if hasContent { emitCurrentLine() }
    return lineCount
}

// MARK: - Walk Prepared Lines (Full)

func walkPreparedLines(
    _ prepared: PreparedCore,
    maxWidth: Double,
    onLine: ((InternalLayoutLine) -> Void)? = nil
) -> Int {
    if prepared.simpleLineWalkFastPath {
        return walkPreparedLinesSimple(prepared, maxWidth: maxWidth, onLine: onLine)
    }

    let widths = prepared.widths
    let lineEndFitAdvances = prepared.lineEndFitAdvances
    let lineEndPaintAdvances = prepared.lineEndPaintAdvances
    let kinds = prepared.kinds
    let breakableWidths = prepared.breakableWidths
    let breakablePrefixWidths = prepared.breakablePrefixWidths
    let discretionaryHyphenWidth = prepared.discretionaryHyphenWidth
    let tabStopAdvance = prepared.tabStopAdvance
    let chunks = prepared.chunks
    if widths.isEmpty || chunks.isEmpty { return 0 }

    let profile = EngineProfile.default
    let lineFitEpsilon = profile.lineFitEpsilon

    var lineCount = 0
    var lineW = 0.0
    var hasContent = false
    var lineStartSegmentIndex = 0
    var lineStartGraphemeIndex = 0
    var lineEndSegmentIndex = 0
    var lineEndGraphemeIndex = 0
    var pendingBreakSegmentIndex = -1
    var pendingBreakFitWidth = 0.0
    var pendingBreakPaintWidth = 0.0
    var pendingBreakKind: SegmentBreakKind? = nil

    func clearPendingBreak() {
        pendingBreakSegmentIndex = -1
        pendingBreakFitWidth = 0
        pendingBreakPaintWidth = 0
        pendingBreakKind = nil
    }

    func emitCurrentLine(
        endSeg: Int? = nil, endGrapheme: Int? = nil, width: Double? = nil
    ) {
        lineCount += 1
        onLine?(InternalLayoutLine(
            startSegmentIndex: lineStartSegmentIndex,
            startGraphemeIndex: lineStartGraphemeIndex,
            endSegmentIndex: endSeg ?? lineEndSegmentIndex,
            endGraphemeIndex: endGrapheme ?? lineEndGraphemeIndex,
            width: width ?? lineW
        ))
        lineW = 0
        hasContent = false
        clearPendingBreak()
    }

    func startLineAtSegment(_ segmentIndex: Int, _ width: Double) {
        hasContent = true
        lineStartSegmentIndex = segmentIndex
        lineStartGraphemeIndex = 0
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
        lineW = width
    }

    func startLineAtGrapheme(_ segmentIndex: Int, _ graphemeIndex: Int, _ width: Double) {
        hasContent = true
        lineStartSegmentIndex = segmentIndex
        lineStartGraphemeIndex = graphemeIndex
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = graphemeIndex + 1
        lineW = width
    }

    func appendWholeSegment(_ segmentIndex: Int, _ width: Double) {
        if !hasContent {
            startLineAtSegment(segmentIndex, width)
            return
        }
        lineW += width
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
    }

    func updatePendingBreakForWholeSegment(_ segmentIndex: Int, _ segmentWidth: Double) {
        if !canBreakAfter(kinds[segmentIndex]) { return }
        let fitAdvance = kinds[segmentIndex] == .tab ? 0 : lineEndFitAdvances[segmentIndex]
        let paintAdvance = kinds[segmentIndex] == .tab ? segmentWidth : lineEndPaintAdvances[segmentIndex]
        pendingBreakSegmentIndex = segmentIndex + 1
        pendingBreakFitWidth = lineW - segmentWidth + fitAdvance
        pendingBreakPaintWidth = lineW - segmentWidth + paintAdvance
        pendingBreakKind = kinds[segmentIndex]
    }

    func appendBreakableSegment(_ segmentIndex: Int) {
        appendBreakableSegmentFrom(segmentIndex, 0)
    }

    func appendBreakableSegmentFrom(_ segmentIndex: Int, _ startGraphemeIdx: Int) {
        let gWidths = breakableWidths[segmentIndex]!
        let gPrefixWidths = breakablePrefixWidths[segmentIndex] ?? nil
        for g in startGraphemeIdx..<gWidths.count {
            let gw = getBreakableAdvance(
                gWidths, gPrefixWidths, g,
                profile.preferPrefixWidthsForBreakableRuns
            )

            if !hasContent {
                startLineAtGrapheme(segmentIndex, g, gw)
                continue
            }

            if lineW + gw > maxWidth + lineFitEpsilon {
                emitCurrentLine()
                startLineAtGrapheme(segmentIndex, g, gw)
            } else {
                lineW += gw
                lineEndSegmentIndex = segmentIndex
                lineEndGraphemeIndex = g + 1
            }
        }

        if hasContent && lineEndSegmentIndex == segmentIndex && lineEndGraphemeIndex == gWidths.count {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
        }
    }

    func continueSoftHyphenBreakableSegment(_ segmentIndex: Int) -> Bool {
        guard pendingBreakKind == .softHyphen else { return false }
        guard let gWidths = breakableWidths[segmentIndex] else { return false }
        let fitWidths = profile.preferPrefixWidthsForBreakableRuns
            ? (breakablePrefixWidths[segmentIndex] ?? gWidths)
            : gWidths
        let usesPrefixWidths = profile.preferPrefixWidthsForBreakableRuns && breakablePrefixWidths[segmentIndex] != nil
        let result = fitSoftHyphenBreak(
            fitWidths, lineW, maxWidth, lineFitEpsilon,
            discretionaryHyphenWidth, usesPrefixWidths
        )
        if result.fitCount == 0 { return false }

        lineW = result.fittedWidth
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = result.fitCount
        clearPendingBreak()

        if result.fitCount == gWidths.count {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
            return true
        }

        emitCurrentLine(
            endSeg: segmentIndex,
            endGrapheme: result.fitCount,
            width: result.fittedWidth + discretionaryHyphenWidth
        )
        appendBreakableSegmentFrom(segmentIndex, result.fitCount)
        return true
    }

    func emitEmptyChunk(_ chunk: PreparedLineChunk) {
        lineCount += 1
        onLine?(InternalLayoutLine(
            startSegmentIndex: chunk.startSegmentIndex,
            startGraphemeIndex: 0,
            endSegmentIndex: chunk.consumedEndSegmentIndex,
            endGraphemeIndex: 0,
            width: 0
        ))
        clearPendingBreak()
    }

    for chunkIndex in 0..<chunks.count {
        let chunk = chunks[chunkIndex]
        if chunk.startSegmentIndex == chunk.endSegmentIndex {
            emitEmptyChunk(chunk)
            continue
        }

        hasContent = false
        lineW = 0
        lineStartSegmentIndex = chunk.startSegmentIndex
        lineStartGraphemeIndex = 0
        lineEndSegmentIndex = chunk.startSegmentIndex
        lineEndGraphemeIndex = 0
        clearPendingBreak()

        var i = chunk.startSegmentIndex
        while i < chunk.endSegmentIndex {
            let kind = kinds[i]
            let w = kind == .tab ? getTabAdvance(lineW, tabStopAdvance) : widths[i]

            if kind == .softHyphen {
                if hasContent {
                    lineEndSegmentIndex = i + 1
                    lineEndGraphemeIndex = 0
                    pendingBreakSegmentIndex = i + 1
                    pendingBreakFitWidth = lineW + discretionaryHyphenWidth
                    pendingBreakPaintWidth = lineW + discretionaryHyphenWidth
                    pendingBreakKind = kind
                }
                i += 1
                continue
            }

            if !hasContent {
                if w > maxWidth && breakableWidths[i] != nil {
                    appendBreakableSegment(i)
                } else {
                    startLineAtSegment(i, w)
                }
                updatePendingBreakForWholeSegment(i, w)
                i += 1
                continue
            }

            let newW = lineW + w
            if newW > maxWidth + lineFitEpsilon {
                let currentBreakFitWidth = lineW + (kind == .tab ? 0 : lineEndFitAdvances[i])
                let currentBreakPaintWidth = lineW + (kind == .tab ? w : lineEndPaintAdvances[i])

                if pendingBreakKind == .softHyphen &&
                   profile.preferEarlySoftHyphenBreak &&
                   pendingBreakFitWidth <= maxWidth + lineFitEpsilon {
                    emitCurrentLine(endSeg: pendingBreakSegmentIndex, endGrapheme: 0, width: pendingBreakPaintWidth)
                    continue
                }

                if pendingBreakKind == .softHyphen && continueSoftHyphenBreakableSegment(i) {
                    i += 1
                    continue
                }

                if canBreakAfter(kind) && currentBreakFitWidth <= maxWidth + lineFitEpsilon {
                    appendWholeSegment(i, w)
                    emitCurrentLine(endSeg: i + 1, endGrapheme: 0, width: currentBreakPaintWidth)
                    i += 1
                    continue
                }

                if pendingBreakSegmentIndex >= 0 && pendingBreakFitWidth <= maxWidth + lineFitEpsilon {
                    emitCurrentLine(endSeg: pendingBreakSegmentIndex, endGrapheme: 0, width: pendingBreakPaintWidth)
                    continue
                }

                if w > maxWidth && breakableWidths[i] != nil {
                    emitCurrentLine()
                    appendBreakableSegment(i)
                    i += 1
                    continue
                }

                emitCurrentLine()
                continue
            }

            appendWholeSegment(i, w)
            updatePendingBreakForWholeSegment(i, w)
            i += 1
        }

        if hasContent {
            let finalPaintWidth =
                pendingBreakSegmentIndex == chunk.consumedEndSegmentIndex
                    ? pendingBreakPaintWidth
                    : lineW
            emitCurrentLine(endSeg: chunk.consumedEndSegmentIndex, endGrapheme: 0, width: finalPaintWidth)
        }
    }

    return lineCount
}

// MARK: - Layout Next Line Range (Public Dispatch)

func layoutNextLineRangePublic(
    _ prepared: PreparedCore,
    start: LayoutCursor,
    maxWidth: Double
) -> LayoutLineRange? {
    guard let normalizedStart = normalizeLineStart(prepared, start) else { return nil }

    if prepared.simpleLineWalkFastPath {
        guard let line = layoutNextLineRangeSimple(prepared, normalizedStart, maxWidth) else { return nil }
        return LayoutLineRange(
            width: line.width,
            start: LayoutCursor(segmentIndex: line.startSegmentIndex, graphemeIndex: line.startGraphemeIndex),
            end: LayoutCursor(segmentIndex: line.endSegmentIndex, graphemeIndex: line.endGraphemeIndex)
        )
    }

    let chunkIndex = findChunkIndexForStart(prepared, normalizedStart.segmentIndex)
    if chunkIndex < 0 { return nil }

    let chunk = prepared.chunks[chunkIndex]
    if chunk.startSegmentIndex == chunk.endSegmentIndex {
        return LayoutLineRange(
            width: 0,
            start: LayoutCursor(segmentIndex: chunk.startSegmentIndex, graphemeIndex: 0),
            end: LayoutCursor(segmentIndex: chunk.consumedEndSegmentIndex, graphemeIndex: 0)
        )
    }

    guard let line = layoutNextLineRangeFull(prepared, normalizedStart, maxWidth, chunk) else { return nil }
    return LayoutLineRange(
        width: line.width,
        start: LayoutCursor(segmentIndex: line.startSegmentIndex, graphemeIndex: line.startGraphemeIndex),
        end: LayoutCursor(segmentIndex: line.endSegmentIndex, graphemeIndex: line.endGraphemeIndex)
    )
}

// MARK: - Layout Next Line Range (Simple)

private func layoutNextLineRangeSimple(
    _ prepared: PreparedCore,
    _ normalizedStart: LayoutCursor,
    _ maxWidth: Double
) -> InternalLayoutLine? {
    let widths = prepared.widths
    let kinds = prepared.kinds
    let breakableWidths = prepared.breakableWidths
    let breakablePrefixWidths = prepared.breakablePrefixWidths
    let profile = EngineProfile.default
    let lineFitEpsilon = profile.lineFitEpsilon

    var lineW = 0.0
    var hasContent = false
    let lineStartSegmentIndex = normalizedStart.segmentIndex
    let lineStartGraphemeIndex = normalizedStart.graphemeIndex
    var lineEndSegmentIndex = lineStartSegmentIndex
    var lineEndGraphemeIndex = lineStartGraphemeIndex
    var pendingBreakSegmentIndex = -1
    var pendingBreakPaintWidth = 0.0

    func finishLine(
        endSeg: Int? = nil, endGrapheme: Int? = nil, width: Double? = nil
    ) -> InternalLayoutLine? {
        if !hasContent { return nil }
        return InternalLayoutLine(
            startSegmentIndex: lineStartSegmentIndex,
            startGraphemeIndex: lineStartGraphemeIndex,
            endSegmentIndex: endSeg ?? lineEndSegmentIndex,
            endGraphemeIndex: endGrapheme ?? lineEndGraphemeIndex,
            width: width ?? lineW
        )
    }

    func startLineAtSegment(_ segmentIndex: Int, _ width: Double) {
        hasContent = true
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
        lineW = width
    }

    func startLineAtGrapheme(_ segmentIndex: Int, _ graphemeIndex: Int, _ width: Double) {
        hasContent = true
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = graphemeIndex + 1
        lineW = width
    }

    func appendWholeSegment(_ segmentIndex: Int, _ width: Double) {
        if !hasContent {
            startLineAtSegment(segmentIndex, width)
            return
        }
        lineW += width
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
    }

    func updatePendingBreak(_ segmentIndex: Int, _ segmentWidth: Double) {
        if !canBreakAfter(kinds[segmentIndex]) { return }
        pendingBreakSegmentIndex = segmentIndex + 1
        pendingBreakPaintWidth = lineW - segmentWidth
    }

    func appendBreakableSegmentFrom(_ segmentIndex: Int, _ startGraphemeIdx: Int) -> InternalLayoutLine? {
        let gWidths = breakableWidths[segmentIndex]!
        let gPrefixWidths = breakablePrefixWidths[segmentIndex] ?? nil
        for g in startGraphemeIdx..<gWidths.count {
            let gw = getBreakableAdvance(
                gWidths, gPrefixWidths, g,
                profile.preferPrefixWidthsForBreakableRuns
            )

            if !hasContent {
                startLineAtGrapheme(segmentIndex, g, gw)
                continue
            }

            if lineW + gw > maxWidth + lineFitEpsilon {
                return finishLine()
            }

            lineW += gw
            lineEndSegmentIndex = segmentIndex
            lineEndGraphemeIndex = g + 1
        }

        if hasContent && lineEndSegmentIndex == segmentIndex && lineEndGraphemeIndex == gWidths.count {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
        }
        return nil
    }

    for i in normalizedStart.segmentIndex..<widths.count {
        let w = widths[i]
        let kind = kinds[i]
        let startGraphemeIdx = i == normalizedStart.segmentIndex ? normalizedStart.graphemeIndex : 0

        if !hasContent {
            if startGraphemeIdx > 0 {
                if let line = appendBreakableSegmentFrom(i, startGraphemeIdx) { return line }
            } else if w > maxWidth && breakableWidths[i] != nil {
                if let line = appendBreakableSegmentFrom(i, 0) { return line }
            } else {
                startLineAtSegment(i, w)
            }
            updatePendingBreak(i, w)
            continue
        }

        let newW = lineW + w
        if newW > maxWidth + lineFitEpsilon {
            if canBreakAfter(kind) {
                appendWholeSegment(i, w)
                return finishLine(endSeg: i + 1, endGrapheme: 0, width: lineW - w)
            }

            if pendingBreakSegmentIndex >= 0 {
                return finishLine(endSeg: pendingBreakSegmentIndex, endGrapheme: 0, width: pendingBreakPaintWidth)
            }

            if w > maxWidth && breakableWidths[i] != nil {
                let currentLine = finishLine()
                if currentLine != nil { return currentLine }
                if let line = appendBreakableSegmentFrom(i, 0) { return line }
            }

            return finishLine()
        }

        appendWholeSegment(i, w)
        updatePendingBreak(i, w)
    }

    return finishLine()
}

// MARK: - Layout Next Line Range (Full, with chunks)

private func layoutNextLineRangeFull(
    _ prepared: PreparedCore,
    _ normalizedStart: LayoutCursor,
    _ maxWidth: Double,
    _ chunk: PreparedLineChunk
) -> InternalLayoutLine? {
    let widths = prepared.widths
    let lineEndFitAdvances = prepared.lineEndFitAdvances
    let lineEndPaintAdvances = prepared.lineEndPaintAdvances
    let kinds = prepared.kinds
    let breakableWidths = prepared.breakableWidths
    let breakablePrefixWidths = prepared.breakablePrefixWidths
    let discretionaryHyphenWidth = prepared.discretionaryHyphenWidth
    let tabStopAdvance = prepared.tabStopAdvance
    let profile = EngineProfile.default
    let lineFitEpsilon = profile.lineFitEpsilon

    var lineW = 0.0
    var hasContent = false
    let lineStartSegmentIndex = normalizedStart.segmentIndex
    let lineStartGraphemeIndex = normalizedStart.graphemeIndex
    var lineEndSegmentIndex = lineStartSegmentIndex
    var lineEndGraphemeIndex = lineStartGraphemeIndex
    var pendingBreakSegmentIndex = -1
    var pendingBreakFitWidth = 0.0
    var pendingBreakPaintWidth = 0.0
    var pendingBreakKind: SegmentBreakKind? = nil

    func clearPendingBreak() {
        pendingBreakSegmentIndex = -1
        pendingBreakFitWidth = 0
        pendingBreakPaintWidth = 0
        pendingBreakKind = nil
    }

    func finishLine(
        endSeg: Int? = nil, endGrapheme: Int? = nil, width: Double? = nil
    ) -> InternalLayoutLine? {
        if !hasContent { return nil }
        return InternalLayoutLine(
            startSegmentIndex: lineStartSegmentIndex,
            startGraphemeIndex: lineStartGraphemeIndex,
            endSegmentIndex: endSeg ?? lineEndSegmentIndex,
            endGraphemeIndex: endGrapheme ?? lineEndGraphemeIndex,
            width: width ?? lineW
        )
    }

    func startLineAtSegment(_ segmentIndex: Int, _ width: Double) {
        hasContent = true
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
        lineW = width
    }

    func startLineAtGrapheme(_ segmentIndex: Int, _ graphemeIndex: Int, _ width: Double) {
        hasContent = true
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = graphemeIndex + 1
        lineW = width
    }

    func appendWholeSegment(_ segmentIndex: Int, _ width: Double) {
        if !hasContent {
            startLineAtSegment(segmentIndex, width)
            return
        }
        lineW += width
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
    }

    func updatePendingBreakForWholeSegment(_ segmentIndex: Int, _ segmentWidth: Double) {
        if !canBreakAfter(kinds[segmentIndex]) { return }
        let fitAdvance = kinds[segmentIndex] == .tab ? 0 : lineEndFitAdvances[segmentIndex]
        let paintAdvance = kinds[segmentIndex] == .tab ? segmentWidth : lineEndPaintAdvances[segmentIndex]
        pendingBreakSegmentIndex = segmentIndex + 1
        pendingBreakFitWidth = lineW - segmentWidth + fitAdvance
        pendingBreakPaintWidth = lineW - segmentWidth + paintAdvance
        pendingBreakKind = kinds[segmentIndex]
    }

    func appendBreakableSegmentFrom(_ segmentIndex: Int, _ startGraphemeIdx: Int) -> InternalLayoutLine? {
        let gWidths = breakableWidths[segmentIndex]!
        let gPrefixWidths = breakablePrefixWidths[segmentIndex] ?? nil
        for g in startGraphemeIdx..<gWidths.count {
            let gw = getBreakableAdvance(
                gWidths, gPrefixWidths, g,
                profile.preferPrefixWidthsForBreakableRuns
            )

            if !hasContent {
                startLineAtGrapheme(segmentIndex, g, gw)
                continue
            }

            if lineW + gw > maxWidth + lineFitEpsilon {
                return finishLine()
            }

            lineW += gw
            lineEndSegmentIndex = segmentIndex
            lineEndGraphemeIndex = g + 1
        }

        if hasContent && lineEndSegmentIndex == segmentIndex && lineEndGraphemeIndex == gWidths.count {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
        }
        return nil
    }

    func maybeFinishAtSoftHyphen(_ segmentIndex: Int) -> InternalLayoutLine? {
        guard pendingBreakKind == .softHyphen && pendingBreakSegmentIndex >= 0 else { return nil }

        if let gWidths = breakableWidths[segmentIndex] {
            let fitWidths = profile.preferPrefixWidthsForBreakableRuns
                ? (breakablePrefixWidths[segmentIndex] ?? gWidths)
                : gWidths
            let usesPrefixWidths = profile.preferPrefixWidthsForBreakableRuns && breakablePrefixWidths[segmentIndex] != nil
            let result = fitSoftHyphenBreak(
                fitWidths, lineW, maxWidth, lineFitEpsilon,
                discretionaryHyphenWidth, usesPrefixWidths
            )

            if result.fitCount == gWidths.count {
                lineW = result.fittedWidth
                lineEndSegmentIndex = segmentIndex + 1
                lineEndGraphemeIndex = 0
                clearPendingBreak()
                return nil
            }

            if result.fitCount > 0 {
                return finishLine(
                    endSeg: segmentIndex,
                    endGrapheme: result.fitCount,
                    width: result.fittedWidth + discretionaryHyphenWidth
                )
            }
        }

        if pendingBreakFitWidth <= maxWidth + lineFitEpsilon {
            return finishLine(endSeg: pendingBreakSegmentIndex, endGrapheme: 0, width: pendingBreakPaintWidth)
        }

        return nil
    }

    for i in normalizedStart.segmentIndex..<chunk.endSegmentIndex {
        let kind = kinds[i]
        let startGraphemeIdx = i == normalizedStart.segmentIndex ? normalizedStart.graphemeIndex : 0
        let w = kind == .tab ? getTabAdvance(lineW, tabStopAdvance) : widths[i]

        if kind == .softHyphen && startGraphemeIdx == 0 {
            if hasContent {
                lineEndSegmentIndex = i + 1
                lineEndGraphemeIndex = 0
                pendingBreakSegmentIndex = i + 1
                pendingBreakFitWidth = lineW + discretionaryHyphenWidth
                pendingBreakPaintWidth = lineW + discretionaryHyphenWidth
                pendingBreakKind = kind
            }
            continue
        }

        if !hasContent {
            if startGraphemeIdx > 0 {
                if let line = appendBreakableSegmentFrom(i, startGraphemeIdx) { return line }
            } else if w > maxWidth && breakableWidths[i] != nil {
                if let line = appendBreakableSegmentFrom(i, 0) { return line }
            } else {
                startLineAtSegment(i, w)
            }
            updatePendingBreakForWholeSegment(i, w)
            continue
        }

        let newW = lineW + w
        if newW > maxWidth + lineFitEpsilon {
            let currentBreakFitWidth = lineW + (kind == .tab ? 0 : lineEndFitAdvances[i])
            let currentBreakPaintWidth = lineW + (kind == .tab ? w : lineEndPaintAdvances[i])

            if pendingBreakKind == .softHyphen &&
               profile.preferEarlySoftHyphenBreak &&
               pendingBreakFitWidth <= maxWidth + lineFitEpsilon {
                return finishLine(endSeg: pendingBreakSegmentIndex, endGrapheme: 0, width: pendingBreakPaintWidth)
            }

            if let softBreakLine = maybeFinishAtSoftHyphen(i) {
                return softBreakLine
            }

            if canBreakAfter(kind) && currentBreakFitWidth <= maxWidth + lineFitEpsilon {
                appendWholeSegment(i, w)
                return finishLine(endSeg: i + 1, endGrapheme: 0, width: currentBreakPaintWidth)
            }

            if pendingBreakSegmentIndex >= 0 && pendingBreakFitWidth <= maxWidth + lineFitEpsilon {
                return finishLine(endSeg: pendingBreakSegmentIndex, endGrapheme: 0, width: pendingBreakPaintWidth)
            }

            if w > maxWidth && breakableWidths[i] != nil {
                let currentLine = finishLine()
                if currentLine != nil { return currentLine }
                if let line = appendBreakableSegmentFrom(i, 0) { return line }
            }

            return finishLine()
        }

        appendWholeSegment(i, w)
        updatePendingBreakForWholeSegment(i, w)
    }

    if pendingBreakSegmentIndex == chunk.consumedEndSegmentIndex && lineEndGraphemeIndex == 0 {
        return finishLine(endSeg: chunk.consumedEndSegmentIndex, endGrapheme: 0, width: pendingBreakPaintWidth)
    }

    return finishLine(endSeg: chunk.consumedEndSegmentIndex, endGrapheme: 0, width: lineW)
}
