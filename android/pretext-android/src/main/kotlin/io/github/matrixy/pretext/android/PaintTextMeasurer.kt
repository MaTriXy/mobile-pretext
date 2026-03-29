package io.github.matrixy.pretext.android

import android.text.TextPaint
import io.github.matrixy.pretext.TextMeasurer

/**
 * Android [TextMeasurer] implementation backed by [TextPaint.measureText].
 *
 * Wraps a configured [TextPaint] instance so that Pretext can measure segment widths
 * using the same font, size, and text properties as your UI.
 *
 * ```kotlin
 * val paint = TextPaint().apply { textSize = 48f }
 * val measurer = PaintTextMeasurer(paint)
 * val prepared = Pretext.prepare(text, measurer)
 * ```
 *
 * @param paint The [TextPaint] to delegate measurements to. Must already be configured
 *   with the desired typeface and text size.
 * @see TextMeasurer
 */
class PaintTextMeasurer(private val paint: TextPaint) : TextMeasurer {
    override fun measureText(text: String): Float = paint.measureText(text)
}
