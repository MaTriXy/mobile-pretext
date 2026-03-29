import Foundation

// MARK: - Whitespace Normalization

private let collapsibleWhitespacePattern = try! NSRegularExpression(pattern: "[ \\t\\n\\r\\f]+")
private let needsNormalizationPattern = try! NSRegularExpression(pattern: "[\\t\\n\\r\\f]| {2,}|^ | $")

func normalizeWhitespaceNormal(_ text: String) -> String {
    let range = NSRange(text.startIndex..., in: text)
    if needsNormalizationPattern.firstMatch(in: text, range: range) == nil { return text }

    var normalized = collapsibleWhitespacePattern.stringByReplacingMatches(
        in: text, range: range, withTemplate: " "
    )
    if normalized.hasPrefix(" ") { normalized.removeFirst() }
    if normalized.hasSuffix(" ") { normalized.removeLast() }
    return normalized
}

func normalizeWhitespacePreWrap(_ text: String) -> String {
    var result = text.replacingOccurrences(of: "\r\n", with: "\n")
    result = result.replacingOccurrences(of: "\r", with: "\n")
    result = result.replacingOccurrences(of: "\u{000C}", with: "\n")
    return result
}

private func getWhiteSpaceProfile(_ whiteSpace: WhiteSpaceMode) -> WhiteSpaceProfile {
    switch whiteSpace {
    case .preWrap:
        return WhiteSpaceProfile(mode: .preWrap, preserveOrdinarySpaces: true, preserveHardBreaks: true)
    case .normal:
        return WhiteSpaceProfile(mode: .normal, preserveOrdinarySpaces: false, preserveHardBreaks: false)
    }
}

// MARK: - Break Kind Classification

private func classifySegmentBreakChar(_ ch: Character, _ profile: WhiteSpaceProfile) -> SegmentBreakKind {
    if profile.preserveOrdinarySpaces || profile.preserveHardBreaks {
        if ch == " " { return .preservedSpace }
        if ch == "\t" { return .tab }
        if profile.preserveHardBreaks && ch == "\n" { return .hardBreak }
    }
    if ch == " " { return .space }
    if ch == "\u{00A0}" || ch == "\u{202F}" || ch == "\u{2060}" || ch == "\u{FEFF}" { return .glue }
    if ch == "\u{200B}" { return .zeroWidthBreak }
    if ch == "\u{00AD}" { return .softHyphen }
    return .text
}

private func splitSegmentByBreakKind(
    _ segment: String,
    isWordLike: Bool,
    start: Int,
    profile: WhiteSpaceProfile
) -> [SegmentationPiece] {
    var pieces: [SegmentationPiece] = []
    var currentKind: SegmentBreakKind? = nil
    var currentText = ""
    var currentStart = start
    var currentWordLike = false
    var offset = 0

    for ch in segment {
        let kind = classifySegmentBreakChar(ch, profile)
        let wordLike = kind == .text && isWordLike

        if let ck = currentKind, kind == ck && wordLike == currentWordLike {
            currentText.append(ch)
            offset += ch.utf16.count
            continue
        }

        if currentKind != nil {
            pieces.append(SegmentationPiece(text: currentText, isWordLike: currentWordLike, kind: currentKind!, start: currentStart))
        }

        currentKind = kind
        currentText = String(ch)
        currentStart = start + offset
        currentWordLike = wordLike
        offset += ch.utf16.count
    }

    if let ck = currentKind {
        pieces.append(SegmentationPiece(text: currentText, isWordLike: currentWordLike, kind: ck, start: currentStart))
    }

    return pieces
}

// MARK: - Merge Helpers

private func isTextRunBoundary(_ kind: SegmentBreakKind) -> Bool {
    kind == .space || kind == .preservedSpace || kind == .zeroWidthBreak || kind == .hardBreak
}

private let urlSchemePattern = try! NSRegularExpression(pattern: "^[A-Za-z][A-Za-z0-9+.-]*:$")

private func isUrlLikeRunStart(_ seg: MergedSegmentation, _ index: Int) -> Bool {
    let text = seg.texts[index]
    if text.hasPrefix("www.") { return true }
    let range = NSRange(text.startIndex..., in: text)
    return urlSchemePattern.firstMatch(in: text, range: range) != nil &&
           index + 1 < seg.len &&
           seg.kinds[index + 1] == .text &&
           seg.texts[index + 1] == "//"
}

private func isUrlQueryBoundarySegment(_ text: String) -> Bool {
    text.contains("?") && (text.contains("://") || text.hasPrefix("www."))
}

// MARK: - URL Merge

private func mergeUrlLikeRuns(_ seg: MergedSegmentation) -> MergedSegmentation {
    var texts = seg.texts
    var isWordLike = seg.isWordLike
    var kinds = seg.kinds
    var starts = seg.starts

    for i in 0..<seg.len {
        guard kinds[i] == .text, isUrlLikeRunStart(seg, i) else { continue }
        var j = i + 1
        while j < seg.len && !isTextRunBoundary(kinds[j]) {
            texts[i] += texts[j]
            isWordLike[i] = true
            let endsQueryPrefix = texts[j].contains("?")
            kinds[j] = .text
            texts[j] = ""
            j += 1
            if endsQueryPrefix { break }
        }
    }

    return compact(texts: &texts, isWordLike: &isWordLike, kinds: &kinds, starts: &starts)
}

// MARK: - URL Query Merge

private func mergeUrlQueryRuns(_ seg: MergedSegmentation) -> MergedSegmentation {
    var texts: [String] = []
    var isWordLike: [Bool] = []
    var kinds: [SegmentBreakKind] = []
    var starts: [Int] = []

    var i = 0
    while i < seg.len {
        let text = seg.texts[i]
        texts.append(text)
        isWordLike.append(seg.isWordLike[i])
        kinds.append(seg.kinds[i])
        starts.append(seg.starts[i])

        guard isUrlQueryBoundarySegment(text) else { i += 1; continue }

        let nextIndex = i + 1
        guard nextIndex < seg.len, !isTextRunBoundary(seg.kinds[nextIndex]) else { i += 1; continue }

        var queryText = ""
        let queryStart = seg.starts[nextIndex]
        var j = nextIndex
        while j < seg.len && !isTextRunBoundary(seg.kinds[j]) {
            queryText += seg.texts[j]
            j += 1
        }

        if !queryText.isEmpty {
            texts.append(queryText)
            isWordLike.append(true)
            kinds.append(.text)
            starts.append(queryStart)
            i = j
        } else {
            i += 1
        }
    }

    return MergedSegmentation(len: texts.count, texts: texts, isWordLike: isWordLike, kinds: kinds, starts: starts)
}

// MARK: - Numeric Merge

private let numericJoinerChars: Set<Character> = [":", "-", "/", "\u{00D7}", ",", ".", "+", "\u{2013}", "\u{2014}"]

private func segmentContainsDecimalDigit(_ text: String) -> Bool {
    text.unicodeScalars.contains { CharacterSet.decimalDigits.contains($0) }
}

private func isNumericRunSegment(_ text: String) -> Bool {
    guard !text.isEmpty else { return false }
    for ch in text {
        if ch.isNumber || numericJoinerChars.contains(ch) { continue }
        return false
    }
    return true
}

private func mergeNumericRuns(_ seg: MergedSegmentation) -> MergedSegmentation {
    var texts: [String] = []
    var isWordLike: [Bool] = []
    var kinds: [SegmentBreakKind] = []
    var starts: [Int] = []

    var i = 0
    while i < seg.len {
        let text = seg.texts[i]
        let kind = seg.kinds[i]

        if kind == .text && isNumericRunSegment(text) && segmentContainsDecimalDigit(text) {
            var mergedText = text
            var j = i + 1
            while j < seg.len && seg.kinds[j] == .text && isNumericRunSegment(seg.texts[j]) {
                mergedText += seg.texts[j]
                j += 1
            }
            texts.append(mergedText)
            isWordLike.append(true)
            kinds.append(.text)
            starts.append(seg.starts[i])
            i = j
            continue
        }

        texts.append(text)
        isWordLike.append(seg.isWordLike[i])
        kinds.append(kind)
        starts.append(seg.starts[i])
        i += 1
    }

    return MergedSegmentation(len: texts.count, texts: texts, isWordLike: isWordLike, kinds: kinds, starts: starts)
}

// MARK: - ASCII Punctuation Chain Merge

private let asciiPuncChainPattern = try! NSRegularExpression(pattern: "^[A-Za-z0-9_]+[,:;]*$")
private let asciiPuncTrailingPattern = try! NSRegularExpression(pattern: "[,:;]+$")

private func mergeAsciiPunctuationChains(_ seg: MergedSegmentation) -> MergedSegmentation {
    var texts: [String] = []
    var isWordLike: [Bool] = []
    var kinds: [SegmentBreakKind] = []
    var starts: [Int] = []

    var i = 0
    while i < seg.len {
        let text = seg.texts[i]
        let kind = seg.kinds[i]
        let wordLike = seg.isWordLike[i]

        let range = NSRange(text.startIndex..., in: text)
        if kind == .text && wordLike && asciiPuncChainPattern.firstMatch(in: text, range: range) != nil {
            var mergedText = text
            var j = i + 1

            while j < seg.len {
                let mRange = NSRange(mergedText.startIndex..., in: mergedText)
                guard asciiPuncTrailingPattern.firstMatch(in: mergedText, range: mRange) != nil else { break }
                guard seg.kinds[j] == .text && seg.isWordLike[j] else { break }
                let nextText = seg.texts[j]
                let nRange = NSRange(nextText.startIndex..., in: nextText)
                guard asciiPuncChainPattern.firstMatch(in: nextText, range: nRange) != nil else { break }
                mergedText += nextText
                j += 1
            }

            texts.append(mergedText)
            isWordLike.append(true)
            kinds.append(.text)
            starts.append(seg.starts[i])
            i = j
            continue
        }

        texts.append(text)
        isWordLike.append(wordLike)
        kinds.append(kind)
        starts.append(seg.starts[i])
        i += 1
    }

    return MergedSegmentation(len: texts.count, texts: texts, isWordLike: isWordLike, kinds: kinds, starts: starts)
}

// MARK: - Hyphenated Numeric Split

private func splitHyphenatedNumericRuns(_ seg: MergedSegmentation) -> MergedSegmentation {
    var texts: [String] = []
    var isWordLike: [Bool] = []
    var kinds: [SegmentBreakKind] = []
    var starts: [Int] = []

    for i in 0..<seg.len {
        let text = seg.texts[i]
        if seg.kinds[i] == .text && text.contains("-") {
            let parts = text.split(separator: "-", omittingEmptySubsequences: false).map(String.init)
            var shouldSplit = parts.count > 1
            for part in parts {
                if part.isEmpty || !segmentContainsDecimalDigit(part) || !isNumericRunSegment(part) {
                    shouldSplit = false; break
                }
            }

            if shouldSplit {
                var offset = 0
                for (j, part) in parts.enumerated() {
                    let splitText = j < parts.count - 1 ? "\(part)-" : part
                    texts.append(splitText)
                    isWordLike.append(true)
                    kinds.append(.text)
                    starts.append(seg.starts[i] + offset)
                    offset += splitText.utf16.count
                }
                continue
            }
        }

        texts.append(text)
        isWordLike.append(seg.isWordLike[i])
        kinds.append(seg.kinds[i])
        starts.append(seg.starts[i])
    }

    return MergedSegmentation(len: texts.count, texts: texts, isWordLike: isWordLike, kinds: kinds, starts: starts)
}

// MARK: - Glue-Connected Text Merge

private func mergeGlueConnectedTextRuns(_ seg: MergedSegmentation) -> MergedSegmentation {
    var texts: [String] = []
    var isWordLike: [Bool] = []
    var kinds: [SegmentBreakKind] = []
    var starts: [Int] = []

    var read = 0
    while read < seg.len {
        var text = seg.texts[read]
        var wordLike = seg.isWordLike[read]
        var kind = seg.kinds[read]
        var start = seg.starts[read]

        if kind == .glue {
            var glueText = text
            let glueStart = start
            read += 1
            while read < seg.len && seg.kinds[read] == .glue {
                glueText += seg.texts[read]; read += 1
            }

            if read < seg.len && seg.kinds[read] == .text {
                text = glueText + seg.texts[read]
                wordLike = seg.isWordLike[read]
                kind = .text
                start = glueStart
                read += 1
            } else {
                texts.append(glueText); isWordLike.append(false)
                kinds.append(.glue); starts.append(glueStart)
                continue
            }
        } else {
            read += 1
        }

        if kind == .text {
            while read < seg.len && seg.kinds[read] == .glue {
                var glueText = ""
                while read < seg.len && seg.kinds[read] == .glue {
                    glueText += seg.texts[read]; read += 1
                }

                if read < seg.len && seg.kinds[read] == .text {
                    text += glueText + seg.texts[read]
                    wordLike = wordLike || seg.isWordLike[read]
                    read += 1; continue
                }

                text += glueText
            }
        }

        texts.append(text); isWordLike.append(wordLike)
        kinds.append(kind); starts.append(start)
    }

    return MergedSegmentation(len: texts.count, texts: texts, isWordLike: isWordLike, kinds: kinds, starts: starts)
}

// MARK: - CJK Forward Sticky Carry

private func carryTrailingForwardStickyAcrossCJKBoundary(_ seg: MergedSegmentation) -> MergedSegmentation {
    var texts = seg.texts
    let isWordLike = seg.isWordLike
    let kinds = seg.kinds
    var starts = seg.starts

    for i in 0..<(texts.count - 1) {
        guard kinds[i] == .text && kinds[i + 1] == .text else { continue }
        guard isCJK(texts[i]) && isCJK(texts[i + 1]) else { continue }

        guard let split = splitTrailingForwardStickyCluster(texts[i]) else { continue }
        texts[i] = split.head
        texts[i + 1] = split.tail + texts[i + 1]
        starts[i + 1] = starts[i] + split.head.utf16.count
    }

    return MergedSegmentation(len: texts.count, texts: texts, isWordLike: isWordLike, kinds: kinds, starts: starts)
}

// MARK: - Compact helper

private func compact(texts: inout [String], isWordLike: inout [Bool], kinds: inout [SegmentBreakKind], starts: inout [Int]) -> MergedSegmentation {
    var compactTexts: [String] = []
    var compactWordLike: [Bool] = []
    var compactKinds: [SegmentBreakKind] = []
    var compactStarts: [Int] = []

    for i in 0..<texts.count {
        if texts[i].isEmpty { continue }
        compactTexts.append(texts[i])
        compactWordLike.append(isWordLike[i])
        compactKinds.append(kinds[i])
        compactStarts.append(starts[i])
    }

    return MergedSegmentation(len: compactTexts.count, texts: compactTexts, isWordLike: compactWordLike, kinds: compactKinds, starts: compactStarts)
}

// MARK: - Leading Space + Marks Split (Arabic)

private func splitLeadingSpaceAndMarks(_ segment: String) -> (space: String, marks: String)? {
    guard segment.count >= 2, segment.first == " " else { return nil }
    let marks = String(segment.dropFirst())
    let allMarks = marks.unicodeScalars.allSatisfy {
        guard let scalar = Unicode.Scalar($0.value) else { return false }
        let cat = scalar.properties.generalCategory
        return cat == .nonspacingMark || cat == .spacingMark || cat == .enclosingMark
    }
    if allMarks { return (" ", marks) }
    return nil
}

// MARK: - Main Build

func buildMergedSegmentation(
    _ normalized: String,
    profile: EngineProfile,
    whiteSpaceProfile: WhiteSpaceProfile
) -> MergedSegmentation {
    let words = segmentWords(normalized)

    var mergedLen = 0
    var mergedTexts: [String] = []
    var mergedWordLike: [Bool] = []
    var mergedKinds: [SegmentBreakKind] = []
    var mergedStarts: [Int] = []

    for word in words {
        for piece in splitSegmentByBreakKind(word.text, isWordLike: word.isWordLike, start: word.index, profile: whiteSpaceProfile) {
            let isText = piece.kind == .text

            // CJK closing quote carry (Chromium-specific, disabled on iOS)
            if profile.carryCJKAfterClosingQuote && isText && mergedLen > 0 &&
               mergedKinds[mergedLen - 1] == .text && isCJK(piece.text) &&
               isCJK(mergedTexts[mergedLen - 1]) && endsWithClosingQuote(mergedTexts[mergedLen - 1]) {
                mergedTexts[mergedLen - 1] += piece.text
                mergedWordLike[mergedLen - 1] = mergedWordLike[mergedLen - 1] || piece.isWordLike
            }
            // CJK line-start prohibited
            else if isText && mergedLen > 0 && mergedKinds[mergedLen - 1] == .text &&
                    isCJKLineStartProhibitedSegment(piece.text) && isCJK(mergedTexts[mergedLen - 1]) {
                mergedTexts[mergedLen - 1] += piece.text
                mergedWordLike[mergedLen - 1] = mergedWordLike[mergedLen - 1] || piece.isWordLike
            }
            // Myanmar medial glue
            else if isText && mergedLen > 0 && mergedKinds[mergedLen - 1] == .text &&
                    endsWithMyanmarMedialGlue(mergedTexts[mergedLen - 1]) {
                mergedTexts[mergedLen - 1] += piece.text
                mergedWordLike[mergedLen - 1] = mergedWordLike[mergedLen - 1] || piece.isWordLike
            }
            // Arabic no-space punctuation
            else if isText && mergedLen > 0 && mergedKinds[mergedLen - 1] == .text &&
                    piece.isWordLike && containsArabicScript(piece.text) &&
                    endsWithArabicNoSpacePunctuation(mergedTexts[mergedLen - 1]) {
                mergedTexts[mergedLen - 1] += piece.text
                mergedWordLike[mergedLen - 1] = true
            }
            // Repeated single-char run
            else if isText && !piece.isWordLike && mergedLen > 0 && mergedKinds[mergedLen - 1] == .text &&
                    piece.text.count == 1 && piece.text != "-" && piece.text != "\u{2014}" &&
                    isRepeatedSingleCharRun(mergedTexts[mergedLen - 1], piece.text.first!) {
                mergedTexts[mergedLen - 1] += piece.text
            }
            // Left-sticky punctuation merge
            else if isText && !piece.isWordLike && mergedLen > 0 && mergedKinds[mergedLen - 1] == .text &&
                    (isLeftStickyPunctuationSegment(piece.text) || (piece.text == "-" && mergedWordLike[mergedLen - 1])) {
                mergedTexts[mergedLen - 1] += piece.text
            }
            // New segment
            else {
                mergedTexts.append(piece.text)
                mergedWordLike.append(piece.isWordLike)
                mergedKinds.append(piece.kind)
                mergedStarts.append(piece.start)
                mergedLen += 1
            }
        }
    }

    // Escaped quote cluster merge (forward pass)
    for i in 1..<mergedLen {
        if mergedKinds[i] == .text && !mergedWordLike[i] &&
           isEscapedQuoteClusterSegment(mergedTexts[i]) && mergedKinds[i - 1] == .text {
            mergedTexts[i - 1] += mergedTexts[i]
            mergedWordLike[i - 1] = mergedWordLike[i - 1] || mergedWordLike[i]
            mergedTexts[i] = ""
        }
    }

    // Forward-sticky cluster merge (backward pass)
    for i in stride(from: mergedLen - 2, through: 0, by: -1) {
        if mergedKinds[i] == .text && !mergedWordLike[i] && isForwardStickyClusterSegment(mergedTexts[i]) {
            var j = i + 1
            while j < mergedLen && mergedTexts[j].isEmpty { j += 1 }
            if j < mergedLen && mergedKinds[j] == .text {
                mergedTexts[j] = mergedTexts[i] + mergedTexts[j]
                mergedStarts[j] = mergedStarts[i]
                mergedTexts[i] = ""
            }
        }
    }

    // Compact empty entries
    var compactedSeg = compact(texts: &mergedTexts, isWordLike: &mergedWordLike, kinds: &mergedKinds, starts: &mergedStarts)

    // Apply remaining merge passes
    compactedSeg = mergeGlueConnectedTextRuns(compactedSeg)
    compactedSeg = mergeUrlLikeRuns(compactedSeg)
    compactedSeg = mergeUrlQueryRuns(compactedSeg)
    compactedSeg = mergeNumericRuns(compactedSeg)
    compactedSeg = splitHyphenatedNumericRuns(compactedSeg)
    compactedSeg = mergeAsciiPunctuationChains(compactedSeg)
    compactedSeg = carryTrailingForwardStickyAcrossCJKBoundary(compactedSeg)

    // Arabic leading space + marks split
    for i in 0..<(compactedSeg.len - 1) {
        guard let split = splitLeadingSpaceAndMarks(compactedSeg.texts[i]) else { continue }
        guard (compactedSeg.kinds[i] == .space || compactedSeg.kinds[i] == .preservedSpace) &&
              compactedSeg.kinds[i + 1] == .text &&
              containsArabicScript(compactedSeg.texts[i + 1]) else { continue }

        compactedSeg.texts[i] = split.space
        compactedSeg.isWordLike[i] = false
        compactedSeg.kinds[i] = compactedSeg.kinds[i] == .preservedSpace ? .preservedSpace : .space
        compactedSeg.texts[i + 1] = split.marks + compactedSeg.texts[i + 1]
        compactedSeg.starts[i + 1] = compactedSeg.starts[i] + split.space.utf16.count
    }

    return compactedSeg
}

// MARK: - Chunk Compilation

private func compileAnalysisChunks(_ seg: MergedSegmentation, _ profile: WhiteSpaceProfile) -> [AnalysisChunk] {
    guard seg.len > 0 else { return [] }
    guard profile.preserveHardBreaks else {
        return [AnalysisChunk(startSegmentIndex: 0, endSegmentIndex: seg.len, consumedEndSegmentIndex: seg.len)]
    }

    var chunks: [AnalysisChunk] = []
    var startIdx = 0

    for i in 0..<seg.len {
        guard seg.kinds[i] == .hardBreak else { continue }
        chunks.append(AnalysisChunk(startSegmentIndex: startIdx, endSegmentIndex: i, consumedEndSegmentIndex: i + 1))
        startIdx = i + 1
    }

    if startIdx < seg.len {
        chunks.append(AnalysisChunk(startSegmentIndex: startIdx, endSegmentIndex: seg.len, consumedEndSegmentIndex: seg.len))
    }

    return chunks
}

// MARK: - Public Entry Point

func analyzeText(_ text: String, profile: EngineProfile, whiteSpace: WhiteSpaceMode = .normal) -> TextAnalysis {
    let wsProfile = getWhiteSpaceProfile(whiteSpace)
    let normalized = wsProfile.mode == .preWrap
        ? normalizeWhitespacePreWrap(text)
        : normalizeWhitespaceNormal(text)

    guard !normalized.isEmpty else {
        return TextAnalysis(normalized: normalized, chunks: [], len: 0, texts: [], isWordLike: [], kinds: [], starts: [])
    }

    let seg = buildMergedSegmentation(normalized, profile: profile, whiteSpaceProfile: wsProfile)
    let chunks = compileAnalysisChunks(seg, wsProfile)

    return TextAnalysis(
        normalized: normalized,
        chunks: chunks,
        len: seg.len,
        texts: seg.texts,
        isWordLike: seg.isWordLike,
        kinds: seg.kinds,
        starts: seg.starts
    )
}
