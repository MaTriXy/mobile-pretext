package io.github.matrixy.pretext.analysis

import io.github.matrixy.pretext.WhiteSpaceMode
import io.github.matrixy.pretext.WhiteSpaceProfile

private val collapsibleWhitespaceRe = Regex("[ \\t\\n\\r\\u000C]+")
private val needsNormalizationRe = Regex("[\\t\\n\\r\\u000C]| {2,}|^ | $")

fun normalizeWhitespaceNormal(text: String): String {
    if (!needsNormalizationRe.containsMatchIn(text)) return text
    var normalized = collapsibleWhitespaceRe.replace(text, " ")
    if (normalized.startsWith(' ')) normalized = normalized.substring(1)
    if (normalized.isNotEmpty() && normalized.endsWith(' ')) normalized = normalized.substring(0, normalized.length - 1)
    return normalized
}

fun normalizeWhitespacePreWrap(text: String): String {
    return text.replace("\r\n", "\n").replace('\r', '\n').replace('\u000C', '\n')
}

fun getWhiteSpaceProfile(whiteSpace: WhiteSpaceMode): WhiteSpaceProfile {
    return when (whiteSpace) {
        WhiteSpaceMode.PreWrap -> WhiteSpaceProfile(WhiteSpaceMode.PreWrap, preserveOrdinarySpaces = true, preserveHardBreaks = true)
        WhiteSpaceMode.Normal -> WhiteSpaceProfile(WhiteSpaceMode.Normal, preserveOrdinarySpaces = false, preserveHardBreaks = false)
    }
}
