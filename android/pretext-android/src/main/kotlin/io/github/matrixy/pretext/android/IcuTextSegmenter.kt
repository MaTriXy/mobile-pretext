package io.github.matrixy.pretext.android

import android.icu.text.BreakIterator
import io.github.matrixy.pretext.TextSegmenter
import io.github.matrixy.pretext.WordSegment
import java.util.Locale

/**
 * Android [TextSegmenter] implementation backed by [android.icu.text.BreakIterator].
 *
 * Provides word-level and grapheme-cluster segmentation using the platform ICU library
 * available on API 24+. This is the standard segmenter for Android applications.
 *
 * ```kotlin
 * val segmenter = IcuTextSegmenter()          // uses Locale.getDefault()
 * val segmenter = IcuTextSegmenter(Locale.JAPANESE)  // explicit locale
 * Pretext.setSegmenter(segmenter)
 * ```
 *
 * @param locale The locale for word boundary rules. Defaults to [Locale.getDefault].
 * @see TextSegmenter
 * @see Pretext.setSegmenter
 */
class IcuTextSegmenter(locale: Locale? = null) : TextSegmenter {
    private val wordLocale = locale ?: Locale.getDefault()

    override fun segmentWords(text: String): List<WordSegment> {
        val bi = BreakIterator.getWordInstance(wordLocale)
        bi.setText(text)
        val segments = mutableListOf<WordSegment>()
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            val segment = text.substring(start, end)
            val status = bi.ruleStatus
            val isWordLike = (status >= BreakIterator.WORD_LETTER && status < BreakIterator.WORD_LETTER_LIMIT) ||
                             (status >= BreakIterator.WORD_NUMBER && status < BreakIterator.WORD_NUMBER_LIMIT)
            segments.add(WordSegment(segment, start, isWordLike))
            start = end
            end = bi.next()
        }
        return segments
    }

    override fun segmentGraphemes(text: String): List<String> {
        val bi = BreakIterator.getCharacterInstance(wordLocale)
        bi.setText(text)
        val graphemes = mutableListOf<String>()
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            graphemes.add(text.substring(start, end))
            start = end
            end = bi.next()
        }
        return graphemes
    }
}
