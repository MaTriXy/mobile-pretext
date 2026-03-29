package io.github.matrixy.pretext.linebreak

import io.github.matrixy.pretext.*

/**
 * Internal line-walking core shared by the rich layout APIs and the hot-path line counter.
 * Ported from line-break.ts (~1057 lines).
 */

fun canBreakAfter(kind: SegmentBreakKind): Boolean {
    return kind == SegmentBreakKind.Space ||
           kind == SegmentBreakKind.PreservedSpace ||
           kind == SegmentBreakKind.Tab ||
           kind == SegmentBreakKind.ZeroWidthBreak ||
           kind == SegmentBreakKind.SoftHyphen
}

fun isSimpleCollapsibleSpace(kind: SegmentBreakKind): Boolean {
    return kind == SegmentBreakKind.Space
}

fun getTabAdvance(lineWidth: Float, tabStopAdvance: Float): Float {
    if (tabStopAdvance <= 0f) return 0f
    val remainder = lineWidth % tabStopAdvance
    if (kotlin.math.abs(remainder) <= 1e-6f) return tabStopAdvance
    return tabStopAdvance - remainder
}

fun getBreakableAdvance(
    graphemeWidths: FloatArray,
    graphemePrefixWidths: FloatArray?,
    graphemeIndex: Int,
    preferPrefixWidths: Boolean
): Float {
    if (!preferPrefixWidths || graphemePrefixWidths == null) {
        return graphemeWidths[graphemeIndex]
    }
    return graphemePrefixWidths[graphemeIndex] - (if (graphemeIndex > 0) graphemePrefixWidths[graphemeIndex - 1] else 0f)
}

data class SoftHyphenFitResult(val fitCount: Int, val fittedWidth: Float)

fun fitSoftHyphenBreak(
    graphemeWidths: FloatArray,
    initialWidth: Float,
    maxWidth: Float,
    lineFitEpsilon: Float,
    discretionaryHyphenWidth: Float,
    cumulativeWidths: Boolean
): SoftHyphenFitResult {
    var fitCount = 0
    var fittedWidth = initialWidth

    while (fitCount < graphemeWidths.size) {
        val nextWidth = if (cumulativeWidths)
            initialWidth + graphemeWidths[fitCount]
        else
            fittedWidth + graphemeWidths[fitCount]
        val nextLineWidth = if (fitCount + 1 < graphemeWidths.size)
            nextWidth + discretionaryHyphenWidth
        else
            nextWidth
        if (nextLineWidth > maxWidth + lineFitEpsilon) break
        fittedWidth = nextWidth
        fitCount++
    }

    return SoftHyphenFitResult(fitCount, fittedWidth)
}

fun findChunkIndexForStart(prepared: PreparedCore, segmentIndex: Int): Int {
    for (i in prepared.chunks.indices) {
        val chunk = prepared.chunks[i]
        if (segmentIndex < chunk.consumedEndSegmentIndex) return i
    }
    return -1
}

fun normalizeLineStart(prepared: PreparedCore, start: LayoutCursor): LayoutCursor? {
    var segmentIndex = start.segmentIndex
    val graphemeIndex = start.graphemeIndex

    if (segmentIndex >= prepared.widths.size) return null
    if (graphemeIndex > 0) return start

    val chunkIndex = findChunkIndexForStart(prepared, segmentIndex)
    if (chunkIndex < 0) return null

    val chunk = prepared.chunks[chunkIndex]
    if (chunk.startSegmentIndex == chunk.endSegmentIndex && segmentIndex == chunk.startSegmentIndex) {
        return LayoutCursor(segmentIndex, 0)
    }

    if (segmentIndex < chunk.startSegmentIndex) segmentIndex = chunk.startSegmentIndex
    while (segmentIndex < chunk.endSegmentIndex) {
        val kind = prepared.kinds[segmentIndex]
        if (kind != SegmentBreakKind.Space && kind != SegmentBreakKind.ZeroWidthBreak && kind != SegmentBreakKind.SoftHyphen) {
            return LayoutCursor(segmentIndex, 0)
        }
        segmentIndex++
    }

    if (chunk.consumedEndSegmentIndex >= prepared.widths.size) return null
    return LayoutCursor(chunk.consumedEndSegmentIndex, 0)
}

// ─── Hot-path line counter ───

fun countPreparedLines(prepared: PreparedCore, maxWidth: Float): Int {
    if (prepared.simpleLineWalkFastPath) {
        return countPreparedLinesSimple(prepared, maxWidth)
    }
    return walkPreparedLines(prepared, maxWidth)
}

fun countPreparedLinesSimple(prepared: PreparedCore, maxWidth: Float): Int {
    val widths = prepared.widths
    val kinds = prepared.kinds
    val breakableWidths = prepared.breakableWidths
    val breakablePrefixWidths = prepared.breakablePrefixWidths
    if (widths.isEmpty()) return 0

    val profile = EngineProfile.DEFAULT
    val lineFitEpsilon = profile.lineFitEpsilon

    var lineCount = 0
    var lineW = 0f
    var hasContent = false

    fun placeOnFreshLine(segmentIndex: Int) {
        val w = widths[segmentIndex]
        if (w > maxWidth && breakableWidths[segmentIndex] != null) {
            val gWidths = breakableWidths[segmentIndex]!!
            val gPrefixWidths = breakablePrefixWidths[segmentIndex]
            lineW = 0f
            for (g in gWidths.indices) {
                val gw = getBreakableAdvance(gWidths, gPrefixWidths, g, profile.preferPrefixWidthsForBreakableRuns)
                if (lineW > 0 && lineW + gw > maxWidth + lineFitEpsilon) {
                    lineCount++
                    lineW = gw
                } else {
                    if (lineW == 0f) lineCount++
                    lineW += gw
                }
            }
        } else {
            lineW = w
            lineCount++
        }
        hasContent = true
    }

    for (i in widths.indices) {
        val w = widths[i]
        val kind = kinds[i]

        if (!hasContent) {
            placeOnFreshLine(i)
            continue
        }

        val newW = lineW + w
        if (newW > maxWidth + lineFitEpsilon) {
            if (isSimpleCollapsibleSpace(kind)) continue
            lineW = 0f
            hasContent = false
            placeOnFreshLine(i)
            continue
        }

        lineW = newW
    }

    if (!hasContent) return lineCount + 1
    return lineCount
}

// ─── Rich-path simple line walker ───

fun walkPreparedLinesSimple(
    prepared: PreparedCore,
    maxWidth: Float,
    onLine: ((InternalLayoutLine) -> Unit)? = null
): Int {
    val widths = prepared.widths
    val kinds = prepared.kinds
    val breakableWidths = prepared.breakableWidths
    val breakablePrefixWidths = prepared.breakablePrefixWidths
    if (widths.isEmpty()) return 0

    val profile = EngineProfile.DEFAULT
    val lineFitEpsilon = profile.lineFitEpsilon

    var lineCount = 0
    var lineW = 0f
    var hasContent = false
    var lineStartSegmentIndex = 0
    var lineStartGraphemeIndex = 0
    var lineEndSegmentIndex = 0
    var lineEndGraphemeIndex = 0
    var pendingBreakSegmentIndex = -1
    var pendingBreakPaintWidth = 0f

    fun clearPendingBreak() {
        pendingBreakSegmentIndex = -1
        pendingBreakPaintWidth = 0f
    }

    fun emitCurrentLine(
        endSeg: Int = lineEndSegmentIndex,
        endGraph: Int = lineEndGraphemeIndex,
        width: Float = lineW
    ) {
        lineCount++
        onLine?.invoke(InternalLayoutLine(lineStartSegmentIndex, lineStartGraphemeIndex, endSeg, endGraph, width))
        lineW = 0f
        hasContent = false
        clearPendingBreak()
    }

    fun startLineAtSegment(segmentIndex: Int, width: Float) {
        hasContent = true
        lineStartSegmentIndex = segmentIndex
        lineStartGraphemeIndex = 0
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
        lineW = width
    }

    fun startLineAtGrapheme(segmentIndex: Int, graphemeIndex: Int, width: Float) {
        hasContent = true
        lineStartSegmentIndex = segmentIndex
        lineStartGraphemeIndex = graphemeIndex
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = graphemeIndex + 1
        lineW = width
    }

    fun appendWholeSegment(segmentIndex: Int, width: Float) {
        if (!hasContent) {
            startLineAtSegment(segmentIndex, width)
            return
        }
        lineW += width
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
    }

    fun updatePendingBreak(segmentIndex: Int, segmentWidth: Float) {
        if (!canBreakAfter(kinds[segmentIndex])) return
        pendingBreakSegmentIndex = segmentIndex + 1
        pendingBreakPaintWidth = lineW - segmentWidth
    }

    fun appendBreakableSegmentFrom(segmentIndex: Int, startGraphemeIndex: Int) {
        val gWidths = breakableWidths[segmentIndex]!!
        val gPrefixWidths = breakablePrefixWidths[segmentIndex]
        for (g in startGraphemeIndex until gWidths.size) {
            val gw = getBreakableAdvance(gWidths, gPrefixWidths, g, profile.preferPrefixWidthsForBreakableRuns)

            if (!hasContent) {
                startLineAtGrapheme(segmentIndex, g, gw)
                continue
            }

            if (lineW + gw > maxWidth + lineFitEpsilon) {
                emitCurrentLine()
                startLineAtGrapheme(segmentIndex, g, gw)
            } else {
                lineW += gw
                lineEndSegmentIndex = segmentIndex
                lineEndGraphemeIndex = g + 1
            }
        }

        if (hasContent && lineEndSegmentIndex == segmentIndex && lineEndGraphemeIndex == gWidths.size) {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
        }
    }

    var i = 0
    while (i < widths.size) {
        val w = widths[i]
        val kind = kinds[i]

        if (!hasContent) {
            if (w > maxWidth && breakableWidths[i] != null) {
                appendBreakableSegmentFrom(i, 0)
            } else {
                startLineAtSegment(i, w)
            }
            updatePendingBreak(i, w)
            i++
            continue
        }

        val newW = lineW + w
        if (newW > maxWidth + lineFitEpsilon) {
            if (canBreakAfter(kind)) {
                appendWholeSegment(i, w)
                emitCurrentLine(i + 1, 0, lineW - w)
                i++
                continue
            }

            if (pendingBreakSegmentIndex >= 0) {
                emitCurrentLine(pendingBreakSegmentIndex, 0, pendingBreakPaintWidth)
                continue
            }

            if (w > maxWidth && breakableWidths[i] != null) {
                emitCurrentLine()
                appendBreakableSegmentFrom(i, 0)
                i++
                continue
            }

            emitCurrentLine()
            continue
        }

        appendWholeSegment(i, w)
        updatePendingBreak(i, w)
        i++
    }

    if (hasContent) emitCurrentLine()
    return lineCount
}

// ─── Full chunk-aware line walker ───

fun walkPreparedLines(
    prepared: PreparedCore,
    maxWidth: Float,
    onLine: ((InternalLayoutLine) -> Unit)? = null
): Int {
    if (prepared.simpleLineWalkFastPath) {
        return walkPreparedLinesSimple(prepared, maxWidth, onLine)
    }

    val widths = prepared.widths
    val lineEndFitAdvances = prepared.lineEndFitAdvances
    val lineEndPaintAdvances = prepared.lineEndPaintAdvances
    val kinds = prepared.kinds
    val breakableWidths = prepared.breakableWidths
    val breakablePrefixWidths = prepared.breakablePrefixWidths
    val discretionaryHyphenWidth = prepared.discretionaryHyphenWidth
    val tabStopAdvance = prepared.tabStopAdvance
    val chunks = prepared.chunks
    if (widths.isEmpty() || chunks.isEmpty()) return 0

    val profile = EngineProfile.DEFAULT
    val lineFitEpsilon = profile.lineFitEpsilon

    var lineCount = 0
    var lineW = 0f
    var hasContent = false
    var lineStartSegmentIndex = 0
    var lineStartGraphemeIndex = 0
    var lineEndSegmentIndex = 0
    var lineEndGraphemeIndex = 0
    var pendingBreakSegmentIndex = -1
    var pendingBreakFitWidth = 0f
    var pendingBreakPaintWidth = 0f
    var pendingBreakKind: SegmentBreakKind? = null

    fun clearPendingBreak() {
        pendingBreakSegmentIndex = -1
        pendingBreakFitWidth = 0f
        pendingBreakPaintWidth = 0f
        pendingBreakKind = null
    }

    fun emitCurrentLine(
        endSeg: Int = lineEndSegmentIndex,
        endGraph: Int = lineEndGraphemeIndex,
        width: Float = lineW
    ) {
        lineCount++
        onLine?.invoke(InternalLayoutLine(lineStartSegmentIndex, lineStartGraphemeIndex, endSeg, endGraph, width))
        lineW = 0f
        hasContent = false
        clearPendingBreak()
    }

    fun startLineAtSegment(segmentIndex: Int, width: Float) {
        hasContent = true
        lineStartSegmentIndex = segmentIndex
        lineStartGraphemeIndex = 0
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
        lineW = width
    }

    fun startLineAtGrapheme(segmentIndex: Int, graphemeIndex: Int, width: Float) {
        hasContent = true
        lineStartSegmentIndex = segmentIndex
        lineStartGraphemeIndex = graphemeIndex
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = graphemeIndex + 1
        lineW = width
    }

    fun appendWholeSegment(segmentIndex: Int, width: Float) {
        if (!hasContent) {
            startLineAtSegment(segmentIndex, width)
            return
        }
        lineW += width
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
    }

    fun updatePendingBreakForWholeSegment(segmentIndex: Int, segmentWidth: Float) {
        if (!canBreakAfter(kinds[segmentIndex])) return
        val fitAdvance = if (kinds[segmentIndex] == SegmentBreakKind.Tab) 0f else lineEndFitAdvances[segmentIndex]
        val paintAdvance = if (kinds[segmentIndex] == SegmentBreakKind.Tab) segmentWidth else lineEndPaintAdvances[segmentIndex]
        pendingBreakSegmentIndex = segmentIndex + 1
        pendingBreakFitWidth = lineW - segmentWidth + fitAdvance
        pendingBreakPaintWidth = lineW - segmentWidth + paintAdvance
        pendingBreakKind = kinds[segmentIndex]
    }

    fun appendBreakableSegmentFrom(segmentIndex: Int, startGraphemeIndex: Int) {
        val gWidths = breakableWidths[segmentIndex]!!
        val gPrefixWidths = breakablePrefixWidths[segmentIndex]
        for (g in startGraphemeIndex until gWidths.size) {
            val gw = getBreakableAdvance(gWidths, gPrefixWidths, g, profile.preferPrefixWidthsForBreakableRuns)

            if (!hasContent) {
                startLineAtGrapheme(segmentIndex, g, gw)
                continue
            }

            if (lineW + gw > maxWidth + lineFitEpsilon) {
                emitCurrentLine()
                startLineAtGrapheme(segmentIndex, g, gw)
            } else {
                lineW += gw
                lineEndSegmentIndex = segmentIndex
                lineEndGraphemeIndex = g + 1
            }
        }

        if (hasContent && lineEndSegmentIndex == segmentIndex && lineEndGraphemeIndex == gWidths.size) {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
        }
    }

    fun continueSoftHyphenBreakableSegment(segmentIndex: Int): Boolean {
        if (pendingBreakKind != SegmentBreakKind.SoftHyphen) return false
        val gWidths = breakableWidths[segmentIndex] ?: return false
        val fitWidths = if (profile.preferPrefixWidthsForBreakableRuns)
            breakablePrefixWidths[segmentIndex] ?: gWidths
        else
            gWidths
        val usesPrefixWidths = fitWidths !== gWidths
        val result = fitSoftHyphenBreak(fitWidths, lineW, maxWidth, lineFitEpsilon, discretionaryHyphenWidth, usesPrefixWidths)
        if (result.fitCount == 0) return false

        lineW = result.fittedWidth
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = result.fitCount
        clearPendingBreak()

        if (result.fitCount == gWidths.size) {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
            return true
        }

        emitCurrentLine(segmentIndex, result.fitCount, result.fittedWidth + discretionaryHyphenWidth)
        appendBreakableSegmentFrom(segmentIndex, result.fitCount)
        return true
    }

    fun emitEmptyChunk(chunk: PreparedLineChunk) {
        lineCount++
        onLine?.invoke(InternalLayoutLine(chunk.startSegmentIndex, 0, chunk.consumedEndSegmentIndex, 0, 0f))
        clearPendingBreak()
    }

    for (chunkIndex in chunks.indices) {
        val chunk = chunks[chunkIndex]
        if (chunk.startSegmentIndex == chunk.endSegmentIndex) {
            emitEmptyChunk(chunk)
            continue
        }

        hasContent = false
        lineW = 0f
        lineStartSegmentIndex = chunk.startSegmentIndex
        lineStartGraphemeIndex = 0
        lineEndSegmentIndex = chunk.startSegmentIndex
        lineEndGraphemeIndex = 0
        clearPendingBreak()

        var i = chunk.startSegmentIndex
        while (i < chunk.endSegmentIndex) {
            val kind = kinds[i]
            val w = if (kind == SegmentBreakKind.Tab) getTabAdvance(lineW, tabStopAdvance) else widths[i]

            if (kind == SegmentBreakKind.SoftHyphen) {
                if (hasContent) {
                    lineEndSegmentIndex = i + 1
                    lineEndGraphemeIndex = 0
                    pendingBreakSegmentIndex = i + 1
                    pendingBreakFitWidth = lineW + discretionaryHyphenWidth
                    pendingBreakPaintWidth = lineW + discretionaryHyphenWidth
                    pendingBreakKind = kind
                }
                i++
                continue
            }

            if (!hasContent) {
                if (w > maxWidth && breakableWidths[i] != null) {
                    appendBreakableSegmentFrom(i, 0)
                } else {
                    startLineAtSegment(i, w)
                }
                updatePendingBreakForWholeSegment(i, w)
                i++
                continue
            }

            val newW = lineW + w
            if (newW > maxWidth + lineFitEpsilon) {
                val currentBreakFitWidth = lineW + (if (kind == SegmentBreakKind.Tab) 0f else lineEndFitAdvances[i])
                val currentBreakPaintWidth = lineW + (if (kind == SegmentBreakKind.Tab) w else lineEndPaintAdvances[i])

                if (pendingBreakKind == SegmentBreakKind.SoftHyphen &&
                    profile.preferEarlySoftHyphenBreak &&
                    pendingBreakFitWidth <= maxWidth + lineFitEpsilon
                ) {
                    emitCurrentLine(pendingBreakSegmentIndex, 0, pendingBreakPaintWidth)
                    continue
                }

                if (pendingBreakKind == SegmentBreakKind.SoftHyphen && continueSoftHyphenBreakableSegment(i)) {
                    i++
                    continue
                }

                if (canBreakAfter(kind) && currentBreakFitWidth <= maxWidth + lineFitEpsilon) {
                    appendWholeSegment(i, w)
                    emitCurrentLine(i + 1, 0, currentBreakPaintWidth)
                    i++
                    continue
                }

                if (pendingBreakSegmentIndex >= 0 && pendingBreakFitWidth <= maxWidth + lineFitEpsilon) {
                    emitCurrentLine(pendingBreakSegmentIndex, 0, pendingBreakPaintWidth)
                    continue
                }

                if (w > maxWidth && breakableWidths[i] != null) {
                    emitCurrentLine()
                    appendBreakableSegmentFrom(i, 0)
                    i++
                    continue
                }

                emitCurrentLine()
                continue
            }

            appendWholeSegment(i, w)
            updatePendingBreakForWholeSegment(i, w)
            i++
        }

        if (hasContent) {
            val finalPaintWidth = if (pendingBreakSegmentIndex == chunk.consumedEndSegmentIndex)
                pendingBreakPaintWidth
            else
                lineW
            emitCurrentLine(chunk.consumedEndSegmentIndex, 0, finalPaintWidth)
        }
    }

    return lineCount
}

// ─── layoutNextLineRange: variable-width single-line stepping ───

fun layoutNextLineRangePublic(
    prepared: PreparedCore,
    start: LayoutCursor,
    maxWidth: Float
): InternalLayoutLine? {
    val normalizedStart = normalizeLineStart(prepared, start) ?: return null

    if (prepared.simpleLineWalkFastPath) {
        return layoutNextLineRangeSimple(prepared, normalizedStart, maxWidth)
    }

    val chunkIndex = findChunkIndexForStart(prepared, normalizedStart.segmentIndex)
    if (chunkIndex < 0) return null

    val chunk = prepared.chunks[chunkIndex]
    if (chunk.startSegmentIndex == chunk.endSegmentIndex) {
        return InternalLayoutLine(chunk.startSegmentIndex, 0, chunk.consumedEndSegmentIndex, 0, 0f)
    }

    return layoutNextLineRangeFull(prepared, normalizedStart, chunk, maxWidth)
}

// ─── Simple variable-width single-line stepping ───

private fun layoutNextLineRangeSimple(
    prepared: PreparedCore,
    normalizedStart: LayoutCursor,
    maxWidth: Float
): InternalLayoutLine? {
    val widths = prepared.widths
    val kinds = prepared.kinds
    val breakableWidths = prepared.breakableWidths
    val breakablePrefixWidths = prepared.breakablePrefixWidths
    val profile = EngineProfile.DEFAULT
    val lineFitEpsilon = profile.lineFitEpsilon

    var lineW = 0f
    var hasContent = false
    val lineStartSegmentIndex = normalizedStart.segmentIndex
    val lineStartGraphemeIndex = normalizedStart.graphemeIndex
    var lineEndSegmentIndex = lineStartSegmentIndex
    var lineEndGraphemeIndex = lineStartGraphemeIndex
    var pendingBreakSegmentIndex = -1
    var pendingBreakPaintWidth = 0f

    fun finishLine(
        endSeg: Int = lineEndSegmentIndex,
        endGraph: Int = lineEndGraphemeIndex,
        width: Float = lineW
    ): InternalLayoutLine? {
        if (!hasContent) return null
        return InternalLayoutLine(lineStartSegmentIndex, lineStartGraphemeIndex, endSeg, endGraph, width)
    }

    fun startLineAtSegment(segmentIndex: Int, width: Float) {
        hasContent = true
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
        lineW = width
    }

    fun startLineAtGrapheme(segmentIndex: Int, graphemeIndex: Int, width: Float) {
        hasContent = true
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = graphemeIndex + 1
        lineW = width
    }

    fun appendWholeSegment(segmentIndex: Int, width: Float) {
        if (!hasContent) {
            startLineAtSegment(segmentIndex, width)
            return
        }
        lineW += width
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
    }

    fun updatePendingBreak(segmentIndex: Int, segmentWidth: Float) {
        if (!canBreakAfter(kinds[segmentIndex])) return
        pendingBreakSegmentIndex = segmentIndex + 1
        pendingBreakPaintWidth = lineW - segmentWidth
    }

    fun appendBreakableSegmentFrom(segmentIndex: Int, startGraphemeIdx: Int): InternalLayoutLine? {
        val gWidths = breakableWidths[segmentIndex]!!
        val gPrefixWidths = breakablePrefixWidths[segmentIndex]
        for (g in startGraphemeIdx until gWidths.size) {
            val gw = getBreakableAdvance(gWidths, gPrefixWidths, g, profile.preferPrefixWidthsForBreakableRuns)

            if (!hasContent) {
                startLineAtGrapheme(segmentIndex, g, gw)
                continue
            }

            if (lineW + gw > maxWidth + lineFitEpsilon) {
                return finishLine()
            }

            lineW += gw
            lineEndSegmentIndex = segmentIndex
            lineEndGraphemeIndex = g + 1
        }

        if (hasContent && lineEndSegmentIndex == segmentIndex && lineEndGraphemeIndex == gWidths.size) {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
        }
        return null
    }

    for (i in normalizedStart.segmentIndex until widths.size) {
        val w = widths[i]
        val kind = kinds[i]
        val startGraphemeIdx = if (i == normalizedStart.segmentIndex) normalizedStart.graphemeIndex else 0

        if (!hasContent) {
            if (startGraphemeIdx > 0) {
                val line = appendBreakableSegmentFrom(i, startGraphemeIdx)
                if (line != null) return line
            } else if (w > maxWidth && breakableWidths[i] != null) {
                val line = appendBreakableSegmentFrom(i, 0)
                if (line != null) return line
            } else {
                startLineAtSegment(i, w)
            }
            updatePendingBreak(i, w)
            continue
        }

        val newW = lineW + w
        if (newW > maxWidth + lineFitEpsilon) {
            if (canBreakAfter(kind)) {
                appendWholeSegment(i, w)
                return finishLine(i + 1, 0, lineW - w)
            }

            if (pendingBreakSegmentIndex >= 0) {
                return finishLine(pendingBreakSegmentIndex, 0, pendingBreakPaintWidth)
            }

            if (w > maxWidth && breakableWidths[i] != null) {
                val currentLine = finishLine()
                if (currentLine != null) return currentLine
                val line = appendBreakableSegmentFrom(i, 0)
                if (line != null) return line
            }

            return finishLine()
        }

        appendWholeSegment(i, w)
        updatePendingBreak(i, w)
    }

    return finishLine()
}

// ─── Full chunk-aware single-line stepping ───

private fun layoutNextLineRangeFull(
    prepared: PreparedCore,
    normalizedStart: LayoutCursor,
    chunk: PreparedLineChunk,
    maxWidth: Float
): InternalLayoutLine? {
    val widths = prepared.widths
    val lineEndFitAdvances = prepared.lineEndFitAdvances
    val lineEndPaintAdvances = prepared.lineEndPaintAdvances
    val kinds = prepared.kinds
    val breakableWidths = prepared.breakableWidths
    val breakablePrefixWidths = prepared.breakablePrefixWidths
    val discretionaryHyphenWidth = prepared.discretionaryHyphenWidth
    val tabStopAdvance = prepared.tabStopAdvance
    val profile = EngineProfile.DEFAULT
    val lineFitEpsilon = profile.lineFitEpsilon

    var lineW = 0f
    var hasContent = false
    val lineStartSegmentIndex = normalizedStart.segmentIndex
    val lineStartGraphemeIndex = normalizedStart.graphemeIndex
    var lineEndSegmentIndex = lineStartSegmentIndex
    var lineEndGraphemeIndex = lineStartGraphemeIndex
    var pendingBreakSegmentIndex = -1
    var pendingBreakFitWidth = 0f
    var pendingBreakPaintWidth = 0f
    var pendingBreakKind: SegmentBreakKind? = null

    fun clearPendingBreak() {
        pendingBreakSegmentIndex = -1
        pendingBreakFitWidth = 0f
        pendingBreakPaintWidth = 0f
        pendingBreakKind = null
    }

    fun finishLine(
        endSeg: Int = lineEndSegmentIndex,
        endGraph: Int = lineEndGraphemeIndex,
        width: Float = lineW
    ): InternalLayoutLine? {
        if (!hasContent) return null
        return InternalLayoutLine(lineStartSegmentIndex, lineStartGraphemeIndex, endSeg, endGraph, width)
    }

    fun startLineAtSegment(segmentIndex: Int, width: Float) {
        hasContent = true
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
        lineW = width
    }

    fun startLineAtGrapheme(segmentIndex: Int, graphemeIndex: Int, width: Float) {
        hasContent = true
        lineEndSegmentIndex = segmentIndex
        lineEndGraphemeIndex = graphemeIndex + 1
        lineW = width
    }

    fun appendWholeSegment(segmentIndex: Int, width: Float) {
        if (!hasContent) {
            startLineAtSegment(segmentIndex, width)
            return
        }
        lineW += width
        lineEndSegmentIndex = segmentIndex + 1
        lineEndGraphemeIndex = 0
    }

    fun updatePendingBreakForWholeSegment(segmentIndex: Int, segmentWidth: Float) {
        if (!canBreakAfter(kinds[segmentIndex])) return
        val fitAdvance = if (kinds[segmentIndex] == SegmentBreakKind.Tab) 0f else lineEndFitAdvances[segmentIndex]
        val paintAdvance = if (kinds[segmentIndex] == SegmentBreakKind.Tab) segmentWidth else lineEndPaintAdvances[segmentIndex]
        pendingBreakSegmentIndex = segmentIndex + 1
        pendingBreakFitWidth = lineW - segmentWidth + fitAdvance
        pendingBreakPaintWidth = lineW - segmentWidth + paintAdvance
        pendingBreakKind = kinds[segmentIndex]
    }

    fun appendBreakableSegmentFrom(segmentIndex: Int, startGraphemeIdx: Int): InternalLayoutLine? {
        val gWidths = breakableWidths[segmentIndex]!!
        val gPrefixWidths = breakablePrefixWidths[segmentIndex]
        for (g in startGraphemeIdx until gWidths.size) {
            val gw = getBreakableAdvance(gWidths, gPrefixWidths, g, profile.preferPrefixWidthsForBreakableRuns)

            if (!hasContent) {
                startLineAtGrapheme(segmentIndex, g, gw)
                continue
            }

            if (lineW + gw > maxWidth + lineFitEpsilon) {
                return finishLine()
            }

            lineW += gw
            lineEndSegmentIndex = segmentIndex
            lineEndGraphemeIndex = g + 1
        }

        if (hasContent && lineEndSegmentIndex == segmentIndex && lineEndGraphemeIndex == gWidths.size) {
            lineEndSegmentIndex = segmentIndex + 1
            lineEndGraphemeIndex = 0
        }
        return null
    }

    fun maybeFinishAtSoftHyphen(segmentIndex: Int): InternalLayoutLine? {
        if (pendingBreakKind != SegmentBreakKind.SoftHyphen || pendingBreakSegmentIndex < 0) return null

        val gWidths = breakableWidths[segmentIndex]
        if (gWidths != null) {
            val fitWidths = if (profile.preferPrefixWidthsForBreakableRuns)
                breakablePrefixWidths[segmentIndex] ?: gWidths
            else
                gWidths
            val usesPrefixWidths = fitWidths !== gWidths
            val result = fitSoftHyphenBreak(fitWidths, lineW, maxWidth, lineFitEpsilon, discretionaryHyphenWidth, usesPrefixWidths)

            if (result.fitCount == gWidths.size) {
                lineW = result.fittedWidth
                lineEndSegmentIndex = segmentIndex + 1
                lineEndGraphemeIndex = 0
                clearPendingBreak()
                return null
            }

            if (result.fitCount > 0) {
                return finishLine(segmentIndex, result.fitCount, result.fittedWidth + discretionaryHyphenWidth)
            }
        }

        if (pendingBreakFitWidth <= maxWidth + lineFitEpsilon) {
            return finishLine(pendingBreakSegmentIndex, 0, pendingBreakPaintWidth)
        }

        return null
    }

    for (i in normalizedStart.segmentIndex until chunk.endSegmentIndex) {
        val kind = kinds[i]
        val startGraphemeIdx = if (i == normalizedStart.segmentIndex) normalizedStart.graphemeIndex else 0
        val w = if (kind == SegmentBreakKind.Tab) getTabAdvance(lineW, tabStopAdvance) else widths[i]

        if (kind == SegmentBreakKind.SoftHyphen && startGraphemeIdx == 0) {
            if (hasContent) {
                lineEndSegmentIndex = i + 1
                lineEndGraphemeIndex = 0
                pendingBreakSegmentIndex = i + 1
                pendingBreakFitWidth = lineW + discretionaryHyphenWidth
                pendingBreakPaintWidth = lineW + discretionaryHyphenWidth
                pendingBreakKind = kind
            }
            continue
        }

        if (!hasContent) {
            if (startGraphemeIdx > 0) {
                val line = appendBreakableSegmentFrom(i, startGraphemeIdx)
                if (line != null) return line
            } else if (w > maxWidth && breakableWidths[i] != null) {
                val line = appendBreakableSegmentFrom(i, 0)
                if (line != null) return line
            } else {
                startLineAtSegment(i, w)
            }
            updatePendingBreakForWholeSegment(i, w)
            continue
        }

        val newW = lineW + w
        if (newW > maxWidth + lineFitEpsilon) {
            val currentBreakFitWidth = lineW + (if (kind == SegmentBreakKind.Tab) 0f else lineEndFitAdvances[i])
            val currentBreakPaintWidth = lineW + (if (kind == SegmentBreakKind.Tab) w else lineEndPaintAdvances[i])

            if (pendingBreakKind == SegmentBreakKind.SoftHyphen &&
                profile.preferEarlySoftHyphenBreak &&
                pendingBreakFitWidth <= maxWidth + lineFitEpsilon
            ) {
                return finishLine(pendingBreakSegmentIndex, 0, pendingBreakPaintWidth)
            }

            val softBreakLine = maybeFinishAtSoftHyphen(i)
            if (softBreakLine != null) return softBreakLine

            if (canBreakAfter(kind) && currentBreakFitWidth <= maxWidth + lineFitEpsilon) {
                appendWholeSegment(i, w)
                return finishLine(i + 1, 0, currentBreakPaintWidth)
            }

            if (pendingBreakSegmentIndex >= 0 && pendingBreakFitWidth <= maxWidth + lineFitEpsilon) {
                return finishLine(pendingBreakSegmentIndex, 0, pendingBreakPaintWidth)
            }

            if (w > maxWidth && breakableWidths[i] != null) {
                val currentLine = finishLine()
                if (currentLine != null) return currentLine
                val line = appendBreakableSegmentFrom(i, 0)
                if (line != null) return line
            }

            return finishLine()
        }

        appendWholeSegment(i, w)
        updatePendingBreakForWholeSegment(i, w)
    }

    if (pendingBreakSegmentIndex == chunk.consumedEndSegmentIndex && lineEndGraphemeIndex == 0) {
        return finishLine(chunk.consumedEndSegmentIndex, 0, pendingBreakPaintWidth)
    }

    return finishLine(chunk.consumedEndSegmentIndex, 0, lineW)
}
