import Foundation

// MARK: - CJK Detection

func isCJK(_ s: String) -> Bool {
    for ch in s.unicodeScalars {
        let c = ch.value
        if (c >= 0x4E00 && c <= 0x9FFF) ||
           (c >= 0x3400 && c <= 0x4DBF) ||
           (c >= 0x20000 && c <= 0x2A6DF) ||
           (c >= 0x2A700 && c <= 0x2B73F) ||
           (c >= 0x2B740 && c <= 0x2B81F) ||
           (c >= 0x2B820 && c <= 0x2CEAF) ||
           (c >= 0x2CEB0 && c <= 0x2EBEF) ||
           (c >= 0x30000 && c <= 0x3134F) ||
           (c >= 0xF900 && c <= 0xFAFF) ||
           (c >= 0x2F800 && c <= 0x2FA1F) ||
           (c >= 0x3000 && c <= 0x303F) ||
           (c >= 0x3040 && c <= 0x309F) ||
           (c >= 0x30A0 && c <= 0x30FF) ||
           (c >= 0xAC00 && c <= 0xD7AF) ||
           (c >= 0xFF00 && c <= 0xFFEF) {
            return true
        }
    }
    return false
}

// Line-start prohibited characters (kinsoku shori)
let kinsokuStart: Set<Character> = [
    "\u{FF0C}", "\u{FF0E}", "\u{FF01}", "\u{FF1A}", "\u{FF1B}",
    "\u{FF1F}", "\u{3001}", "\u{3002}", "\u{30FB}", "\u{FF09}",
    "\u{3015}", "\u{3009}", "\u{300B}", "\u{300D}", "\u{300F}",
    "\u{3011}", "\u{3017}", "\u{3019}", "\u{301B}", "\u{30FC}",
    "\u{3005}", "\u{303B}", "\u{309D}", "\u{309E}", "\u{30FD}",
    "\u{30FE}",
]

// Line-end prohibited characters
let kinsokuEnd: Set<Character> = [
    "\"", "(", "[", "{",
    "\u{201C}", "\u{2018}", "\u{00AB}", "\u{2039}",
    "\u{FF08}", "\u{3014}", "\u{3008}", "\u{300A}",
    "\u{300C}", "\u{300E}", "\u{3010}", "\u{3016}",
    "\u{3018}", "\u{301A}",
]

let forwardStickyGlue: Set<Character> = ["'", "\u{2018}"]

let leftStickyPunctuation: Set<Character> = [
    ".", ",", "!", "?", ":", ";",
    "\u{060C}", "\u{061B}", "\u{061F}",
    "\u{0964}", "\u{0965}",
    "\u{104A}", "\u{104B}", "\u{104C}", "\u{104D}", "\u{104F}",
    ")", "]", "}",
    "%", "\"",
    "\u{201D}", "\u{2019}", "\u{00BB}", "\u{203A}",
    "\u{2026}",
]

let arabicNoSpaceTrailingPunctuation: Set<Character> = [
    ":", ".", "\u{060C}", "\u{061B}",
]

let myanmarMedialGlue: Set<Character> = ["\u{104F}"]

let closingQuoteChars: Set<Character> = [
    "\u{201D}", "\u{2019}", "\u{00BB}", "\u{203A}",
    "\u{300D}", "\u{300F}", "\u{3011}", "\u{300B}",
    "\u{3009}", "\u{3015}", "\u{FF09}",
]

// MARK: - CJK Helper Functions

func endsWithClosingQuote(_ text: String) -> Bool {
    for ch in text.reversed() {
        if closingQuoteChars.contains(ch) { return true }
        if !leftStickyPunctuation.contains(ch) { return false }
    }
    return false
}

func isCJKLineStartProhibitedSegment(_ segment: String) -> Bool {
    guard !segment.isEmpty else { return false }
    for ch in segment {
        if !kinsokuStart.contains(ch) && !leftStickyPunctuation.contains(ch) { return false }
    }
    return true
}

func isForwardStickyClusterSegment(_ segment: String) -> Bool {
    if isEscapedQuoteClusterSegment(segment) { return true }
    guard !segment.isEmpty else { return false }
    for ch in segment {
        if !kinsokuEnd.contains(ch) && !forwardStickyGlue.contains(ch) && !ch.isCombiningMark {
            return false
        }
    }
    return true
}

func isLeftStickyPunctuationSegment(_ segment: String) -> Bool {
    if isEscapedQuoteClusterSegment(segment) { return true }
    var sawPunctuation = false
    for ch in segment {
        if leftStickyPunctuation.contains(ch) {
            sawPunctuation = true
            continue
        }
        if sawPunctuation && ch.isCombiningMark { continue }
        return false
    }
    return sawPunctuation
}

func isEscapedQuoteClusterSegment(_ segment: String) -> Bool {
    var sawQuote = false
    for ch in segment {
        if ch == "\\" || ch.isCombiningMark { continue }
        if kinsokuEnd.contains(ch) || leftStickyPunctuation.contains(ch) || forwardStickyGlue.contains(ch) {
            sawQuote = true
            continue
        }
        return false
    }
    return sawQuote
}

func splitTrailingForwardStickyCluster(_ text: String) -> (head: String, tail: String)? {
    let chars = Array(text)
    var splitIndex = chars.count

    while splitIndex > 0 {
        let ch = chars[splitIndex - 1]
        if ch.isCombiningMark { splitIndex -= 1; continue }
        if kinsokuEnd.contains(ch) || forwardStickyGlue.contains(ch) { splitIndex -= 1; continue }
        break
    }

    if splitIndex <= 0 || splitIndex == chars.count { return nil }
    return (String(chars[0..<splitIndex]), String(chars[splitIndex...]))
}

func containsArabicScript(_ text: String) -> Bool {
    for scalar in text.unicodeScalars {
        let v = scalar.value
        if (v >= 0x0600 && v <= 0x06FF) || (v >= 0x0750 && v <= 0x077F) || (v >= 0x08A0 && v <= 0x08FF) {
            return true
        }
    }
    return false
}

func endsWithArabicNoSpacePunctuation(_ segment: String) -> Bool {
    guard !segment.isEmpty, containsArabicScript(segment) else { return false }
    return arabicNoSpaceTrailingPunctuation.contains(segment.last!)
}

func endsWithMyanmarMedialGlue(_ segment: String) -> Bool {
    guard !segment.isEmpty else { return false }
    return myanmarMedialGlue.contains(segment.last!)
}

func isRepeatedSingleCharRun(_ segment: String, _ ch: Character) -> Bool {
    guard !segment.isEmpty else { return false }
    for part in segment { if part != ch { return false } }
    return true
}

// MARK: - Character Extensions

extension Character {
    var isCombiningMark: Bool {
        guard let scalar = unicodeScalars.first else { return false }
        return scalar.properties.generalCategory == .nonspacingMark ||
               scalar.properties.generalCategory == .spacingMark ||
               scalar.properties.generalCategory == .enclosingMark
    }

    var isEmojiPresentation: Bool {
        unicodeScalars.contains { $0.properties.isEmojiPresentation }
    }
}
