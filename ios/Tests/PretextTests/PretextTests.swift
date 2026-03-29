import XCTest
@testable import Pretext

final class FakeTextMeasurer: TextMeasurer {
    let fontSize: Double

    init(fontSize: Double = 16.0) {
        self.fontSize = fontSize
    }

    func measureText(_ text: String) -> Double {
        var width = 0.0
        for ch in text {
            if ch == " " { width += fontSize * 0.33 }
            else if ch == "\t" { width += fontSize * 1.32 }
            else if ch.isEmojiPresentation || ch == "\u{FE0F}" { width += fontSize }
            else if isWideChar(ch) { width += fontSize }
            else if isPunctuation(ch) { width += fontSize * 0.4 }
            else { width += fontSize * 0.6 }
        }
        return width
    }

    private func isWideChar(_ ch: Character) -> Bool {
        guard let code = ch.unicodeScalars.first?.value else { return false }
        return (code >= 0x4E00 && code <= 0x9FFF) || (code >= 0x3400 && code <= 0x4DBF) ||
               (code >= 0xF900 && code <= 0xFAFF) || (code >= 0x3000 && code <= 0x303F) ||
               (code >= 0x3040 && code <= 0x309F) || (code >= 0x30A0 && code <= 0x30FF) ||
               (code >= 0xAC00 && code <= 0xD7AF) || (code >= 0xFF00 && code <= 0xFFEF) ||
               (code >= 0x20000 && code <= 0x2A6DF)
    }

    private func isPunctuation(_ ch: Character) -> Bool {
        let puncs: Set<Character> = [".", ",", "!", "?", ";", ":", "%", ")", "]", "}", "'", "\"",
                                      "\u{201C}", "\u{201D}", "\u{2018}", "\u{2019}", "\u{00BB}",
                                      "\u{203A}", "\u{2026}", "\u{2014}", "-"]
        return puncs.contains(ch)
    }
}

final class PretextTests: XCTestCase {

    func testEmptyTextReturnsZero() {
        let prepared = Pretext.prepare("", font: FontSpec(name: "Helvetica", size: 16))
        let result = Pretext.layout(prepared, maxWidth: 100, lineHeight: 20)
        XCTAssertEqual(result.lineCount, 0)
        XCTAssertEqual(result.height, 0)
    }

    func testSingleWordFitsOnOneLine() {
        let prepared = Pretext.prepare("Hello", font: FontSpec(name: "Helvetica", size: 16))
        let result = Pretext.layout(prepared, maxWidth: 200, lineHeight: 20)
        XCTAssertEqual(result.lineCount, 1)
        XCTAssertEqual(result.height, 20)
    }

    func testWhitespaceNormalization() {
        XCTAssertEqual(normalizeWhitespaceNormal("  hello  world  "), "hello world")
        XCTAssertEqual(normalizeWhitespaceNormal("hello\n\tworld"), "hello world")
        XCTAssertEqual(normalizeWhitespaceNormal("hello world"), "hello world")
    }

    func testPreWrapPreservesSpaces() {
        XCTAssertEqual(normalizeWhitespacePreWrap("hello  world"), "hello  world")
        XCTAssertEqual(normalizeWhitespacePreWrap("hello\r\nworld"), "hello\nworld")
    }

    func testCJKDetection() {
        XCTAssertTrue(isCJK("\u{4F60}"))
        XCTAssertTrue(isCJK("\u{65E5}"))
        XCTAssertTrue(isCJK("\u{D55C}"))
        XCTAssertFalse(isCJK("a"))
        XCTAssertFalse(isCJK("1"))
    }

    func testLayoutLinesConsistency() {
        let prepared = Pretext.prepareWithSegments("Hello world this is a test", font: FontSpec(name: "Helvetica", size: 16))
        let simple = Pretext.layout(PreparedText(core: prepared.core), maxWidth: 100, lineHeight: 20)
        let rich = Pretext.layoutWithLines(prepared, maxWidth: 100, lineHeight: 20)
        XCTAssertEqual(simple.lineCount, rich.lineCount)
    }

    func testMonotonicLineCount() {
        let prepared = Pretext.prepare("The quick brown fox jumps over the lazy dog", font: FontSpec(name: "Helvetica", size: 16))
        var prevCount = 0
        for width in stride(from: 500.0, to: 10.0, by: -10.0) {
            let result = Pretext.layout(prepared, maxWidth: width, lineHeight: 20)
            XCTAssertGreaterThanOrEqual(result.lineCount, 1)
            // Line count should be monotonically non-decreasing as width decreases
            XCTAssertGreaterThanOrEqual(result.lineCount, prevCount,
                "Line count should not decrease as width decreases: width=\(width), count=\(result.lineCount), prev=\(prevCount)")
            prevCount = result.lineCount
        }
    }

    func testLayoutResultHeight() {
        let prepared = Pretext.prepare("Hello world", font: FontSpec(name: "Helvetica", size: 16))
        let result = Pretext.layout(prepared, maxWidth: 1000, lineHeight: 24)
        XCTAssertEqual(result.height, Double(result.lineCount) * 24.0)
    }

    func testLayoutWithLinesReturnsSameLineCount() {
        let prepared = Pretext.prepareWithSegments("A short sentence.", font: FontSpec(name: "Helvetica", size: 16))
        let result = Pretext.layoutWithLines(prepared, maxWidth: 200, lineHeight: 20)
        XCTAssertEqual(result.lineCount, result.lines.count)
    }
}
