import Foundation
import NaturalLanguage

// Module-level segmenter state (thread-safe)
private let segmenterLock = NSLock()
private var segmenterLanguage: NLLanguage?

func setSegmenterLocale(_ locale: Locale?) {
    segmenterLock.lock()
    defer { segmenterLock.unlock() }
    if let locale = locale {
        segmenterLanguage = NLLanguage(rawValue: locale.identifier)
    } else {
        segmenterLanguage = nil
    }
}

private func currentSegmenterLanguage() -> NLLanguage? {
    segmenterLock.lock()
    defer { segmenterLock.unlock() }
    return segmenterLanguage
}

struct WordSegment {
    let text: String
    let index: Int
    let isWordLike: Bool
}

func segmentWords(_ text: String) -> [WordSegment] {
    let tokenizer = NLTokenizer(unit: .word)
    if let lang = currentSegmenterLanguage() {
        tokenizer.setLanguage(lang)
    }
    tokenizer.string = text

    var segments: [WordSegment] = []
    var cursor = text.startIndex

    tokenizer.enumerateTokens(in: text.startIndex..<text.endIndex) { range, _ in
        // Emit gap (non-word segment) before this token
        if cursor < range.lowerBound {
            let gapText = String(text[cursor..<range.lowerBound])
            let gapIndex = text.utf16.distance(from: text.startIndex, to: cursor)
            segments.append(WordSegment(text: gapText, index: gapIndex, isWordLike: false))
        }

        // Emit the word token
        let wordText = String(text[range])
        let wordIndex = text.utf16.distance(from: text.startIndex, to: range.lowerBound)
        segments.append(WordSegment(text: wordText, index: wordIndex, isWordLike: true))

        cursor = range.upperBound
        return true
    }

    // Emit trailing gap
    if cursor < text.endIndex {
        let gapText = String(text[cursor..<text.endIndex])
        let gapIndex = text.utf16.distance(from: text.startIndex, to: cursor)
        segments.append(WordSegment(text: gapText, index: gapIndex, isWordLike: false))
    }

    return segments
}

func segmentGraphemes(_ text: String) -> [String] {
    // Swift strings iterate by grapheme clusters natively
    return text.map { String($0) }
}
