package io.github.matrixy.pretext.analysis

/**
 * CJK detection, kinsoku tables, and script-specific character classification.
 * Ported from analysis.ts lines 105-319.
 */

private val arabicScriptRe = Regex("\\p{IsArabic}")
private val combiningMarkRe = Regex("\\p{M}")
private val decimalDigitRe = Regex("\\p{Nd}")

fun isCJK(s: String): Boolean {
    for (ch in s) {
        val c = ch.code
        // BMP ranges — use Char code directly
        if ((c in 0x4E00..0x9FFF) ||
            (c in 0x3400..0x4DBF) ||
            (c in 0xF900..0xFAFF) ||
            (c in 0x3000..0x303F) ||
            (c in 0x3040..0x309F) ||
            (c in 0x30A0..0x30FF) ||
            (c in 0xAC00..0xD7AF) ||
            (c in 0xFF00..0xFFEF)
        ) {
            return true
        }
    }
    // Check for astral CJK via codepoints
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        if ((cp in 0x20000..0x2A6DF) ||
            (cp in 0x2A700..0x2B73F) ||
            (cp in 0x2B740..0x2B81F) ||
            (cp in 0x2B820..0x2CEAF) ||
            (cp in 0x2CEB0..0x2EBEF) ||
            (cp in 0x30000..0x3134F) ||
            (cp in 0x2F800..0x2FA1F)
        ) {
            return true
        }
        i += Character.charCount(cp)
    }
    return false
}

// Line-start prohibited (kinsoku-shori) — these must not appear at the start of a line
val kinsokuStart = setOf(
    '\uFF0C', // fullwidth comma
    '\uFF0E', // fullwidth full stop
    '\uFF01', // fullwidth exclamation mark
    '\uFF1A', // fullwidth colon
    '\uFF1B', // fullwidth semicolon
    '\uFF1F', // fullwidth question mark
    '\u3001', // ideographic comma
    '\u3002', // ideographic full stop
    '\u30FB', // katakana middle dot
    '\uFF09', // fullwidth right parenthesis
    '\u3015', // right tortoise shell bracket
    '\u3009', // right angle bracket
    '\u300B', // right double angle bracket
    '\u300D', // right corner bracket
    '\u300F', // right white corner bracket
    '\u3011', // right black lenticular bracket
    '\u3017', // right white lenticular bracket
    '\u3019', // right white tortoise shell bracket
    '\u301B', // right white square bracket
    '\u30FC', // katakana-hiragana prolonged sound mark
    '\u3005', // ideographic iteration mark
    '\u303B', // vertical ideographic iteration mark
    '\u309D', // hiragana iteration mark
    '\u309E', // hiragana voiced iteration mark
    '\u30FD', // katakana iteration mark
    '\u30FE'  // katakana voiced iteration mark
)

// Line-end prohibited — these must not appear at the end of a line
val kinsokuEnd = setOf(
    '"',
    '(', '[', '{',
    '\u201C', // left double quotation mark
    '\u2018', // left single quotation mark
    '\u00AB', // left-pointing double angle quotation mark
    '\u2039', // single left-pointing angle quotation mark
    '\uFF08', // fullwidth left parenthesis
    '\u3014', // left tortoise shell bracket
    '\u3008', // left angle bracket
    '\u300A', // left double angle bracket
    '\u300C', // left corner bracket
    '\u300E', // left white corner bracket
    '\u3010', // left black lenticular bracket
    '\u3016', // left white lenticular bracket
    '\u3018', // left white tortoise shell bracket
    '\u301A'  // left white square bracket
)

val forwardStickyGlue = setOf(
    '\'', '\u2018' // apostrophe, left single quotation mark
)

val leftStickyPunctuation = setOf(
    '.', ',', '!', '?', ':', ';',
    '\u060C', // Arabic comma
    '\u061B', // Arabic semicolon
    '\u061F', // Arabic question mark
    '\u0964', // Devanagari danda
    '\u0965', // Devanagari double danda
    '\u104A', // Myanmar sign little section
    '\u104B', // Myanmar sign section
    '\u104C', // Myanmar symbol locative
    '\u104D', // Myanmar symbol completed
    '\u104F', // Myanmar symbol genitive
    ')', ']', '}',
    '%',
    '"',
    '\u201D', // right double quotation mark
    '\u2019', // right single quotation mark
    '\u00BB', // right-pointing double angle quotation mark
    '\u203A', // single right-pointing angle quotation mark
    '\u2026'  // horizontal ellipsis
)

val arabicNoSpaceTrailingPunctuation = setOf(
    ':',
    '.',
    '\u060C', // Arabic comma
    '\u061B'  // Arabic semicolon
)

val myanmarMedialGlue = setOf(
    '\u104F' // Myanmar symbol genitive
)

val closingQuoteChars = setOf(
    '\u201D', // right double quotation mark
    '\u2019', // right single quotation mark
    '\u00BB', // right-pointing double angle quotation mark
    '\u203A', // single right-pointing angle quotation mark
    '\u300D', // right corner bracket
    '\u300F', // right white corner bracket
    '\u3011', // right black lenticular bracket
    '\u300B', // right double angle bracket
    '\u3009', // right angle bracket
    '\u3015', // right tortoise shell bracket
    '\uFF09'  // fullwidth right parenthesis
)

fun endsWithClosingQuote(text: String): Boolean {
    for (i in text.length - 1 downTo 0) {
        val ch = text[i]
        if (closingQuoteChars.contains(ch)) return true
        if (!leftStickyPunctuation.contains(ch)) return false
    }
    return false
}

fun isCJKLineStartProhibitedSegment(segment: String): Boolean {
    if (segment.isEmpty()) return false
    for (ch in segment) {
        if (!kinsokuStart.contains(ch) && !leftStickyPunctuation.contains(ch)) return false
    }
    return true
}

fun isForwardStickyClusterSegment(segment: String): Boolean {
    if (isEscapedQuoteClusterSegment(segment)) return true
    if (segment.isEmpty()) return false
    for (ch in segment) {
        if (!kinsokuEnd.contains(ch) && !forwardStickyGlue.contains(ch) && !isCombiningMark(ch.code)) return false
    }
    return true
}

fun isLeftStickyPunctuationSegment(segment: String): Boolean {
    if (isEscapedQuoteClusterSegment(segment)) return true
    var sawPunctuation = false
    for (ch in segment) {
        if (leftStickyPunctuation.contains(ch)) {
            sawPunctuation = true
            continue
        }
        if (sawPunctuation && isCombiningMark(ch.code)) continue
        return false
    }
    return sawPunctuation
}

fun isEscapedQuoteClusterSegment(segment: String): Boolean {
    var sawQuote = false
    for (ch in segment) {
        if (ch == '\\' || isCombiningMark(ch.code)) continue
        if (kinsokuEnd.contains(ch) || leftStickyPunctuation.contains(ch) || forwardStickyGlue.contains(ch)) {
            sawQuote = true
            continue
        }
        return false
    }
    return sawQuote
}

fun splitTrailingForwardStickyCluster(text: String): Pair<String, String>? {
    val chars = text.toList().map { it.toString() }
    // Use codepoint-aware iteration for correctness
    val codepoints = mutableListOf<String>()
    var idx = 0
    while (idx < text.length) {
        val cp = text.codePointAt(idx)
        val len = Character.charCount(cp)
        codepoints.add(text.substring(idx, idx + len))
        idx += len
    }

    var splitIndex = codepoints.size

    while (splitIndex > 0) {
        val ch = codepoints[splitIndex - 1]
        if (ch.length == 1 && isCombiningMark(ch[0].code)) {
            splitIndex--
            continue
        }
        if (ch.length == 1 && (kinsokuEnd.contains(ch[0]) || forwardStickyGlue.contains(ch[0]))) {
            splitIndex--
            continue
        }
        break
    }

    if (splitIndex <= 0 || splitIndex == codepoints.size) return null
    return Pair(
        codepoints.subList(0, splitIndex).joinToString(""),
        codepoints.subList(splitIndex, codepoints.size).joinToString("")
    )
}

fun containsArabicScript(text: String): Boolean {
    return arabicScriptRe.containsMatchIn(text)
}

fun endsWithArabicNoSpacePunctuation(segment: String): Boolean {
    if (!containsArabicScript(segment) || segment.isEmpty()) return false
    return arabicNoSpaceTrailingPunctuation.contains(segment.last())
}

fun endsWithMyanmarMedialGlue(segment: String): Boolean {
    if (segment.isEmpty()) return false
    return myanmarMedialGlue.contains(segment.last())
}

fun isRepeatedSingleCharRun(segment: String, ch: String): Boolean {
    if (segment.isEmpty()) return false
    for (part in segment) {
        if (part.toString() != ch) return false
    }
    return true
}

fun isCombiningMark(c: Int): Boolean {
    val type = Character.getType(c)
    return type == Character.NON_SPACING_MARK.toInt() ||
           type == Character.COMBINING_SPACING_MARK.toInt() ||
           type == Character.ENCLOSING_MARK.toInt()
}

fun segmentContainsDecimalDigit(text: String): Boolean {
    return decimalDigitRe.containsMatchIn(text)
}

val numericJoinerChars = setOf(
    ':', '-', '/', '\u00D7', ',', '.', '+',
    '\u2013', // en dash
    '\u2014'  // em dash
)

fun isNumericRunSegment(text: String): Boolean {
    if (text.isEmpty()) return false
    for (ch in text) {
        if (Character.isDigit(ch) || numericJoinerChars.contains(ch)) continue
        // Check with regex for non-ASCII decimal digits
        if (decimalDigitRe.containsMatchIn(ch.toString())) continue
        return false
    }
    return true
}

fun splitLeadingSpaceAndMarks(segment: String): Pair<String, String>? {
    if (segment.length < 2 || segment[0] != ' ') return null
    val marks = segment.substring(1)
    val allMarks = marks.all { isCombiningMark(it.code) }
    if (allMarks) {
        return Pair(" ", marks)
    }
    return null
}
