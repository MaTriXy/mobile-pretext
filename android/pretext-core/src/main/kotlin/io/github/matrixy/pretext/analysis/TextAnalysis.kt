package io.github.matrixy.pretext.analysis

import io.github.matrixy.pretext.*

/**
 * Full text analysis pipeline ported from analysis.ts.
 * Normalization, segmentation, merge passes, and chunk compilation.
 */

private val urlSchemeSegmentRe = Regex("^[A-Za-z][A-Za-z0-9+.-]*:$")
private val asciiPunctuationChainSegmentRe = Regex("^[A-Za-z0-9_]+[,:;]*$")
private val asciiPunctuationChainTrailingJoinersRe = Regex("[,:;]+$")

fun classifySegmentBreakChar(ch: Char, whiteSpaceProfile: WhiteSpaceProfile): SegmentBreakKind {
    if (whiteSpaceProfile.preserveOrdinarySpaces || whiteSpaceProfile.preserveHardBreaks) {
        if (ch == ' ') return SegmentBreakKind.PreservedSpace
        if (ch == '\t') return SegmentBreakKind.Tab
        if (whiteSpaceProfile.preserveHardBreaks && ch == '\n') return SegmentBreakKind.HardBreak
    }
    if (ch == ' ') return SegmentBreakKind.Space
    if (ch == '\u00A0' || ch == '\u202F' || ch == '\u2060' || ch == '\uFEFF') {
        return SegmentBreakKind.Glue
    }
    if (ch == '\u200B') return SegmentBreakKind.ZeroWidthBreak
    if (ch == '\u00AD') return SegmentBreakKind.SoftHyphen
    return SegmentBreakKind.Text
}

fun splitSegmentByBreakKind(
    segment: String,
    isWordLike: Boolean,
    start: Int,
    whiteSpaceProfile: WhiteSpaceProfile
): List<SegmentationPiece> {
    val pieces = mutableListOf<SegmentationPiece>()
    var currentKind: SegmentBreakKind? = null
    var currentText = StringBuilder()
    var currentStart = start
    var currentWordLike = false
    var offset = 0

    for (ch in segment) {
        val kind = classifySegmentBreakChar(ch, whiteSpaceProfile)
        val wordLike = kind == SegmentBreakKind.Text && isWordLike

        if (currentKind != null && kind == currentKind && wordLike == currentWordLike) {
            currentText.append(ch)
            offset += ch.toString().length
            continue
        }

        if (currentKind != null) {
            pieces.add(SegmentationPiece(
                text = currentText.toString(),
                isWordLike = currentWordLike,
                kind = currentKind,
                start = currentStart
            ))
        }

        currentKind = kind
        currentText = StringBuilder().append(ch)
        currentStart = start + offset
        currentWordLike = wordLike
        offset += ch.toString().length
    }

    if (currentKind != null) {
        pieces.add(SegmentationPiece(
            text = currentText.toString(),
            isWordLike = currentWordLike,
            kind = currentKind,
            start = currentStart
        ))
    }

    return pieces
}

fun isTextRunBoundary(kind: SegmentBreakKind): Boolean {
    return kind == SegmentBreakKind.Space ||
           kind == SegmentBreakKind.PreservedSpace ||
           kind == SegmentBreakKind.ZeroWidthBreak ||
           kind == SegmentBreakKind.HardBreak
}

fun isUrlLikeRunStart(segmentation: MergedSegmentation, index: Int): Boolean {
    val text = segmentation.texts[index]
    if (text.startsWith("www.")) return true
    return urlSchemeSegmentRe.containsMatchIn(text) &&
           index + 1 < segmentation.len &&
           segmentation.kinds[index + 1] == SegmentBreakKind.Text &&
           segmentation.texts[index + 1] == "//"
}

fun isUrlQueryBoundarySegment(text: String): Boolean {
    return text.contains('?') && (text.contains("://") || text.startsWith("www."))
}

fun mergeUrlLikeRuns(segmentation: MergedSegmentation): MergedSegmentation {
    val texts = segmentation.texts.toMutableList()
    val isWordLike = segmentation.isWordLike.toMutableList()
    val kinds = segmentation.kinds.toMutableList()
    val starts = segmentation.starts.toMutableList()

    for (i in 0 until segmentation.len) {
        if (kinds[i] != SegmentBreakKind.Text || !isUrlLikeRunStart(segmentation, i)) continue

        var j = i + 1
        while (j < segmentation.len && !isTextRunBoundary(kinds[j])) {
            texts[i] = texts[i] + texts[j]
            isWordLike[i] = true
            val endsQueryPrefix = texts[j].contains('?')
            kinds[j] = SegmentBreakKind.Text
            texts[j] = ""
            j++
            if (endsQueryPrefix) break
        }
    }

    // Compact
    var compactLen = 0
    for (read in texts.indices) {
        val text = texts[read]
        if (text.isEmpty()) continue
        if (compactLen != read) {
            texts[compactLen] = text
            isWordLike[compactLen] = isWordLike[read]
            kinds[compactLen] = kinds[read]
            starts[compactLen] = starts[read]
        }
        compactLen++
    }

    return MergedSegmentation(
        len = compactLen,
        texts = texts.subList(0, compactLen).toMutableList(),
        isWordLike = isWordLike.subList(0, compactLen).toMutableList(),
        kinds = kinds.subList(0, compactLen).toMutableList(),
        starts = starts.subList(0, compactLen).toMutableList()
    )
}

fun mergeUrlQueryRuns(segmentation: MergedSegmentation): MergedSegmentation {
    val texts = mutableListOf<String>()
    val isWordLike = mutableListOf<Boolean>()
    val kinds = mutableListOf<SegmentBreakKind>()
    val starts = mutableListOf<Int>()

    var i = 0
    while (i < segmentation.len) {
        val text = segmentation.texts[i]
        texts.add(text)
        isWordLike.add(segmentation.isWordLike[i])
        kinds.add(segmentation.kinds[i])
        starts.add(segmentation.starts[i])

        if (!isUrlQueryBoundarySegment(text)) { i++; continue }

        val nextIndex = i + 1
        if (nextIndex >= segmentation.len || isTextRunBoundary(segmentation.kinds[nextIndex])) { i++; continue }

        var queryText = ""
        val queryStart = segmentation.starts[nextIndex]
        var j = nextIndex
        while (j < segmentation.len && !isTextRunBoundary(segmentation.kinds[j])) {
            queryText += segmentation.texts[j]
            j++
        }

        if (queryText.isNotEmpty()) {
            texts.add(queryText)
            isWordLike.add(true)
            kinds.add(SegmentBreakKind.Text)
            starts.add(queryStart)
            i = j
        } else {
            i++
        }
    }

    return MergedSegmentation(
        len = texts.size,
        texts = texts,
        isWordLike = isWordLike,
        kinds = kinds,
        starts = starts
    )
}

fun mergeNumericRuns(segmentation: MergedSegmentation): MergedSegmentation {
    val texts = mutableListOf<String>()
    val isWordLike = mutableListOf<Boolean>()
    val kinds = mutableListOf<SegmentBreakKind>()
    val starts = mutableListOf<Int>()

    var i = 0
    while (i < segmentation.len) {
        val text = segmentation.texts[i]
        val kind = segmentation.kinds[i]

        if (kind == SegmentBreakKind.Text && isNumericRunSegment(text) && segmentContainsDecimalDigit(text)) {
            var mergedText = text
            var j = i + 1
            while (j < segmentation.len &&
                   segmentation.kinds[j] == SegmentBreakKind.Text &&
                   isNumericRunSegment(segmentation.texts[j])
            ) {
                mergedText += segmentation.texts[j]
                j++
            }

            texts.add(mergedText)
            isWordLike.add(true)
            kinds.add(SegmentBreakKind.Text)
            starts.add(segmentation.starts[i])
            i = j
            continue
        }

        texts.add(text)
        isWordLike.add(segmentation.isWordLike[i])
        kinds.add(kind)
        starts.add(segmentation.starts[i])
        i++
    }

    return MergedSegmentation(
        len = texts.size,
        texts = texts,
        isWordLike = isWordLike,
        kinds = kinds,
        starts = starts
    )
}

fun mergeAsciiPunctuationChains(segmentation: MergedSegmentation): MergedSegmentation {
    val texts = mutableListOf<String>()
    val isWordLike = mutableListOf<Boolean>()
    val kinds = mutableListOf<SegmentBreakKind>()
    val starts = mutableListOf<Int>()

    var i = 0
    while (i < segmentation.len) {
        val text = segmentation.texts[i]
        val kind = segmentation.kinds[i]
        val wordLike = segmentation.isWordLike[i]

        if (kind == SegmentBreakKind.Text && wordLike && asciiPunctuationChainSegmentRe.containsMatchIn(text)) {
            var mergedText = text
            var j = i + 1

            while (asciiPunctuationChainTrailingJoinersRe.containsMatchIn(mergedText) &&
                   j < segmentation.len &&
                   segmentation.kinds[j] == SegmentBreakKind.Text &&
                   segmentation.isWordLike[j] &&
                   asciiPunctuationChainSegmentRe.containsMatchIn(segmentation.texts[j])
            ) {
                mergedText += segmentation.texts[j]
                j++
            }

            texts.add(mergedText)
            isWordLike.add(true)
            kinds.add(SegmentBreakKind.Text)
            starts.add(segmentation.starts[i])
            i = j
            continue
        }

        texts.add(text)
        isWordLike.add(wordLike)
        kinds.add(kind)
        starts.add(segmentation.starts[i])
        i++
    }

    return MergedSegmentation(
        len = texts.size,
        texts = texts,
        isWordLike = isWordLike,
        kinds = kinds,
        starts = starts
    )
}

fun splitHyphenatedNumericRuns(segmentation: MergedSegmentation): MergedSegmentation {
    val texts = mutableListOf<String>()
    val isWordLike = mutableListOf<Boolean>()
    val kinds = mutableListOf<SegmentBreakKind>()
    val starts = mutableListOf<Int>()

    for (i in 0 until segmentation.len) {
        val text = segmentation.texts[i]
        if (segmentation.kinds[i] == SegmentBreakKind.Text && text.contains('-')) {
            val parts = text.split('-')
            var shouldSplit = parts.size > 1
            for (j in parts.indices) {
                val part = parts[j]
                if (!shouldSplit) break
                if (part.isEmpty() || !segmentContainsDecimalDigit(part) || !isNumericRunSegment(part)) {
                    shouldSplit = false
                }
            }

            if (shouldSplit) {
                var offset = 0
                for (j in parts.indices) {
                    val part = parts[j]
                    val splitText = if (j < parts.size - 1) "$part-" else part
                    texts.add(splitText)
                    isWordLike.add(true)
                    kinds.add(SegmentBreakKind.Text)
                    starts.add(segmentation.starts[i] + offset)
                    offset += splitText.length
                }
                continue
            }
        }

        texts.add(text)
        isWordLike.add(segmentation.isWordLike[i])
        kinds.add(segmentation.kinds[i])
        starts.add(segmentation.starts[i])
    }

    return MergedSegmentation(
        len = texts.size,
        texts = texts,
        isWordLike = isWordLike,
        kinds = kinds,
        starts = starts
    )
}

fun mergeGlueConnectedTextRuns(segmentation: MergedSegmentation): MergedSegmentation {
    val texts = mutableListOf<String>()
    val isWordLike = mutableListOf<Boolean>()
    val kinds = mutableListOf<SegmentBreakKind>()
    val starts = mutableListOf<Int>()

    var read = 0
    while (read < segmentation.len) {
        var text = segmentation.texts[read]
        var wordLike = segmentation.isWordLike[read]
        var kind = segmentation.kinds[read]
        var start = segmentation.starts[read]

        if (kind == SegmentBreakKind.Glue) {
            var glueText = text
            val glueStart = start
            read++
            while (read < segmentation.len && segmentation.kinds[read] == SegmentBreakKind.Glue) {
                glueText += segmentation.texts[read]
                read++
            }

            if (read < segmentation.len && segmentation.kinds[read] == SegmentBreakKind.Text) {
                text = glueText + segmentation.texts[read]
                wordLike = segmentation.isWordLike[read]
                kind = SegmentBreakKind.Text
                start = glueStart
                read++
            } else {
                texts.add(glueText)
                isWordLike.add(false)
                kinds.add(SegmentBreakKind.Glue)
                starts.add(glueStart)
                continue
            }
        } else {
            read++
        }

        if (kind == SegmentBreakKind.Text) {
            while (read < segmentation.len && segmentation.kinds[read] == SegmentBreakKind.Glue) {
                var glueText = ""
                while (read < segmentation.len && segmentation.kinds[read] == SegmentBreakKind.Glue) {
                    glueText += segmentation.texts[read]
                    read++
                }

                if (read < segmentation.len && segmentation.kinds[read] == SegmentBreakKind.Text) {
                    text += glueText + segmentation.texts[read]
                    wordLike = wordLike || segmentation.isWordLike[read]
                    read++
                    continue
                }

                text += glueText
            }
        }

        texts.add(text)
        isWordLike.add(wordLike)
        kinds.add(kind)
        starts.add(start)
    }

    return MergedSegmentation(
        len = texts.size,
        texts = texts,
        isWordLike = isWordLike,
        kinds = kinds,
        starts = starts
    )
}

fun carryTrailingForwardStickyAcrossCJKBoundary(segmentation: MergedSegmentation): MergedSegmentation {
    val texts = segmentation.texts.toMutableList()
    val isWordLike = segmentation.isWordLike.toMutableList()
    val kinds = segmentation.kinds.toMutableList()
    val starts = segmentation.starts.toMutableList()

    for (i in 0 until texts.size - 1) {
        if (kinds[i] != SegmentBreakKind.Text || kinds[i + 1] != SegmentBreakKind.Text) continue
        if (!isCJK(texts[i]) || !isCJK(texts[i + 1])) continue

        val split = splitTrailingForwardStickyCluster(texts[i]) ?: continue

        texts[i] = split.first
        texts[i + 1] = split.second + texts[i + 1]
        starts[i + 1] = starts[i] + split.first.length
    }

    return MergedSegmentation(
        len = texts.size,
        texts = texts,
        isWordLike = isWordLike,
        kinds = kinds,
        starts = starts
    )
}

fun buildMergedSegmentation(
    normalized: String,
    profile: EngineProfile,
    whiteSpaceProfile: WhiteSpaceProfile,
    segmenter: TextSegmenter
): MergedSegmentation {
    var mergedLen = 0
    val mergedTexts = mutableListOf<String>()
    val mergedWordLike = mutableListOf<Boolean>()
    val mergedKinds = mutableListOf<SegmentBreakKind>()
    val mergedStarts = mutableListOf<Int>()

    for (s in segmenter.segmentWords(normalized)) {
        for (piece in splitSegmentByBreakKind(s.text, s.isWordLike, s.index, whiteSpaceProfile)) {
            val isText = piece.kind == SegmentBreakKind.Text

            if (profile.carryCJKAfterClosingQuote &&
                isText &&
                mergedLen > 0 &&
                mergedKinds[mergedLen - 1] == SegmentBreakKind.Text &&
                isCJK(piece.text) &&
                isCJK(mergedTexts[mergedLen - 1]) &&
                endsWithClosingQuote(mergedTexts[mergedLen - 1])
            ) {
                mergedTexts[mergedLen - 1] = mergedTexts[mergedLen - 1] + piece.text
                mergedWordLike[mergedLen - 1] = mergedWordLike[mergedLen - 1] || piece.isWordLike
            } else if (
                isText &&
                mergedLen > 0 &&
                mergedKinds[mergedLen - 1] == SegmentBreakKind.Text &&
                isCJKLineStartProhibitedSegment(piece.text) &&
                isCJK(mergedTexts[mergedLen - 1])
            ) {
                mergedTexts[mergedLen - 1] = mergedTexts[mergedLen - 1] + piece.text
                mergedWordLike[mergedLen - 1] = mergedWordLike[mergedLen - 1] || piece.isWordLike
            } else if (
                isText &&
                mergedLen > 0 &&
                mergedKinds[mergedLen - 1] == SegmentBreakKind.Text &&
                endsWithMyanmarMedialGlue(mergedTexts[mergedLen - 1])
            ) {
                mergedTexts[mergedLen - 1] = mergedTexts[mergedLen - 1] + piece.text
                mergedWordLike[mergedLen - 1] = mergedWordLike[mergedLen - 1] || piece.isWordLike
            } else if (
                isText &&
                mergedLen > 0 &&
                mergedKinds[mergedLen - 1] == SegmentBreakKind.Text &&
                piece.isWordLike &&
                containsArabicScript(piece.text) &&
                endsWithArabicNoSpacePunctuation(mergedTexts[mergedLen - 1])
            ) {
                mergedTexts[mergedLen - 1] = mergedTexts[mergedLen - 1] + piece.text
                mergedWordLike[mergedLen - 1] = true
            } else if (
                isText &&
                !piece.isWordLike &&
                mergedLen > 0 &&
                mergedKinds[mergedLen - 1] == SegmentBreakKind.Text &&
                piece.text.length == 1 &&
                piece.text != "-" &&
                piece.text != "\u2014" &&
                isRepeatedSingleCharRun(mergedTexts[mergedLen - 1], piece.text)
            ) {
                mergedTexts[mergedLen - 1] = mergedTexts[mergedLen - 1] + piece.text
            } else if (
                isText &&
                !piece.isWordLike &&
                mergedLen > 0 &&
                mergedKinds[mergedLen - 1] == SegmentBreakKind.Text &&
                (isLeftStickyPunctuationSegment(piece.text) ||
                 (piece.text == "-" && mergedWordLike[mergedLen - 1]))
            ) {
                mergedTexts[mergedLen - 1] = mergedTexts[mergedLen - 1] + piece.text
            } else {
                if (mergedLen >= mergedTexts.size) {
                    mergedTexts.add(piece.text)
                    mergedWordLike.add(piece.isWordLike)
                    mergedKinds.add(piece.kind)
                    mergedStarts.add(piece.start)
                } else {
                    mergedTexts[mergedLen] = piece.text
                    mergedWordLike[mergedLen] = piece.isWordLike
                    mergedKinds[mergedLen] = piece.kind
                    mergedStarts[mergedLen] = piece.start
                }
                mergedLen++
            }
        }
    }

    // Escaped quote cluster forward pass
    for (i in 1 until mergedLen) {
        if (mergedKinds[i] == SegmentBreakKind.Text &&
            !mergedWordLike[i] &&
            isEscapedQuoteClusterSegment(mergedTexts[i]) &&
            mergedKinds[i - 1] == SegmentBreakKind.Text
        ) {
            mergedTexts[i - 1] = mergedTexts[i - 1] + mergedTexts[i]
            mergedWordLike[i - 1] = mergedWordLike[i - 1] || mergedWordLike[i]
            mergedTexts[i] = ""
        }
    }

    // Forward-sticky cluster backward pass
    for (i in mergedLen - 2 downTo 0) {
        if (mergedKinds[i] == SegmentBreakKind.Text && !mergedWordLike[i] && isForwardStickyClusterSegment(mergedTexts[i])) {
            var j = i + 1
            while (j < mergedLen && mergedTexts[j].isEmpty()) j++
            if (j < mergedLen && mergedKinds[j] == SegmentBreakKind.Text) {
                mergedTexts[j] = mergedTexts[i] + mergedTexts[j]
                mergedStarts[j] = mergedStarts[i]
                mergedTexts[i] = ""
            }
        }
    }

    // Compact empty entries
    var compactLen = 0
    for (read in 0 until mergedLen) {
        val text = mergedTexts[read]
        if (text.isEmpty()) continue
        if (compactLen != read) {
            mergedTexts[compactLen] = text
            mergedWordLike[compactLen] = mergedWordLike[read]
            mergedKinds[compactLen] = mergedKinds[read]
            mergedStarts[compactLen] = mergedStarts[read]
        }
        compactLen++
    }

    // Trim lists to compactLen
    while (mergedTexts.size > compactLen) mergedTexts.removeAt(mergedTexts.size - 1)
    while (mergedWordLike.size > compactLen) mergedWordLike.removeAt(mergedWordLike.size - 1)
    while (mergedKinds.size > compactLen) mergedKinds.removeAt(mergedKinds.size - 1)
    while (mergedStarts.size > compactLen) mergedStarts.removeAt(mergedStarts.size - 1)

    val compacted = mergeGlueConnectedTextRuns(MergedSegmentation(
        len = compactLen,
        texts = mergedTexts,
        isWordLike = mergedWordLike,
        kinds = mergedKinds,
        starts = mergedStarts
    ))

    val withMergedUrls = carryTrailingForwardStickyAcrossCJKBoundary(
        mergeAsciiPunctuationChains(
            splitHyphenatedNumericRuns(
                mergeNumericRuns(
                    mergeUrlQueryRuns(
                        mergeUrlLikeRuns(compacted)
                    )
                )
            )
        )
    )

    // Arabic leading space + marks split
    for (i in 0 until withMergedUrls.len - 1) {
        val split = splitLeadingSpaceAndMarks(withMergedUrls.texts[i]) ?: continue
        if ((withMergedUrls.kinds[i] != SegmentBreakKind.Space && withMergedUrls.kinds[i] != SegmentBreakKind.PreservedSpace) ||
            withMergedUrls.kinds[i + 1] != SegmentBreakKind.Text ||
            !containsArabicScript(withMergedUrls.texts[i + 1])
        ) {
            continue
        }

        withMergedUrls.texts[i] = split.first
        withMergedUrls.isWordLike[i] = false
        withMergedUrls.kinds[i] = if (withMergedUrls.kinds[i] == SegmentBreakKind.PreservedSpace) SegmentBreakKind.PreservedSpace else SegmentBreakKind.Space
        withMergedUrls.texts[i + 1] = split.second + withMergedUrls.texts[i + 1]
        withMergedUrls.starts[i + 1] = withMergedUrls.starts[i] + split.first.length
    }

    return withMergedUrls
}

fun compileAnalysisChunks(segmentation: MergedSegmentation, whiteSpaceProfile: WhiteSpaceProfile): List<AnalysisChunk> {
    if (segmentation.len == 0) return emptyList()
    if (!whiteSpaceProfile.preserveHardBreaks) {
        return listOf(AnalysisChunk(
            startSegmentIndex = 0,
            endSegmentIndex = segmentation.len,
            consumedEndSegmentIndex = segmentation.len
        ))
    }

    val chunks = mutableListOf<AnalysisChunk>()
    var startSegmentIndex = 0

    for (i in 0 until segmentation.len) {
        if (segmentation.kinds[i] != SegmentBreakKind.HardBreak) continue

        chunks.add(AnalysisChunk(
            startSegmentIndex = startSegmentIndex,
            endSegmentIndex = i,
            consumedEndSegmentIndex = i + 1
        ))
        startSegmentIndex = i + 1
    }

    if (startSegmentIndex < segmentation.len) {
        chunks.add(AnalysisChunk(
            startSegmentIndex = startSegmentIndex,
            endSegmentIndex = segmentation.len,
            consumedEndSegmentIndex = segmentation.len
        ))
    }

    return chunks
}

fun analyzeText(
    text: String,
    profile: EngineProfile,
    whiteSpace: WhiteSpaceMode = WhiteSpaceMode.Normal,
    segmenter: TextSegmenter
): TextAnalysis {
    val whiteSpaceProfile = getWhiteSpaceProfile(whiteSpace)
    val normalized = if (whiteSpaceProfile.mode == WhiteSpaceMode.PreWrap)
        normalizeWhitespacePreWrap(text)
    else
        normalizeWhitespaceNormal(text)

    if (normalized.isEmpty()) {
        return TextAnalysis(
            normalized = normalized,
            chunks = emptyList(),
            len = 0,
            texts = emptyList(),
            isWordLike = emptyList(),
            kinds = emptyList(),
            starts = emptyList()
        )
    }

    val segmentation = buildMergedSegmentation(normalized, profile, whiteSpaceProfile, segmenter)
    return TextAnalysis(
        normalized = normalized,
        chunks = compileAnalysisChunks(segmentation, whiteSpaceProfile),
        len = segmentation.len,
        texts = segmentation.texts,
        isWordLike = segmentation.isWordLike,
        kinds = segmentation.kinds,
        starts = segmentation.starts
    )
}
