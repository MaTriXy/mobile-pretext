package io.github.matrixy.pretext.measurement

import io.github.matrixy.pretext.SegmentMetrics
import io.github.matrixy.pretext.TextMeasurer
import io.github.matrixy.pretext.TextSegmenter
import io.github.matrixy.pretext.analysis.isCJK

class MeasurementCache {
    private val lock = Any()
    private val caches = HashMap<Any, HashMap<String, SegmentMetrics>>()

    fun getMetrics(segment: String, fontKey: Any, measurer: TextMeasurer): SegmentMetrics {
        synchronized(lock) {
            val cache = caches.getOrPut(fontKey) { HashMap() }
            cache[segment]?.let { return it }
            val width = measurer.measureText(segment)
            val metrics = SegmentMetrics(width = width, containsCJK = isCJK(segment))
            cache[segment] = metrics
            return metrics
        }
    }

    fun getGraphemeWidths(
        segment: String,
        fontKey: Any,
        measurer: TextMeasurer,
        segmenter: TextSegmenter
    ): FloatArray? {
        synchronized(lock) {
            val metrics = ensureEntry(segment, fontKey, measurer)
            metrics.graphemeWidths?.let { return it }

            val graphemes = segmenter.segmentGraphemes(segment)
            if (graphemes.size <= 1) {
                metrics.graphemeWidths = null
                return null
            }

            val widths = FloatArray(graphemes.size) { i ->
                ensureEntry(graphemes[i], fontKey, measurer).width
            }
            metrics.graphemeWidths = widths
            return widths
        }
    }

    fun getGraphemePrefixWidths(
        segment: String,
        fontKey: Any,
        measurer: TextMeasurer,
        segmenter: TextSegmenter
    ): FloatArray? {
        synchronized(lock) {
            val metrics = ensureEntry(segment, fontKey, measurer)
            metrics.graphemePrefixWidths?.let { return it }

            val graphemes = segmenter.segmentGraphemes(segment)
            if (graphemes.size <= 1) {
                metrics.graphemePrefixWidths = null
                return null
            }

            val prefixWidths = FloatArray(graphemes.size)
            var prefix = ""
            for (i in graphemes.indices) {
                prefix += graphemes[i]
                prefixWidths[i] = ensureEntry(prefix, fontKey, measurer).width
            }
            metrics.graphemePrefixWidths = prefixWidths
            return prefixWidths
        }
    }

    fun clear() {
        synchronized(lock) { caches.clear() }
    }

    // Must be called while lock is held
    private fun ensureEntry(segment: String, fontKey: Any, measurer: TextMeasurer): SegmentMetrics {
        val cache = caches.getOrPut(fontKey) { HashMap() }
        cache[segment]?.let { return it }
        val width = measurer.measureText(segment)
        val metrics = SegmentMetrics(width = width, containsCJK = isCJK(segment))
        cache[segment] = metrics
        return metrics
    }
}

val sharedMeasurementCache = MeasurementCache()
