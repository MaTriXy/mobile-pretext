package io.github.matrixy.pretext.compose

import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.matrixy.pretext.*
import io.github.matrixy.pretext.android.IcuTextSegmenter
import io.github.matrixy.pretext.android.PaintTextMeasurer
import kotlin.math.roundToInt

/**
 * Jetpack Compose composable that measures and sizes text using the Pretext layout engine.
 *
 * This composable prepares the given [text] with [PaintTextMeasurer] and [IcuTextSegmenter],
 * then uses [Pretext.layout] during the Compose layout phase to compute the required height.
 * It does not render the text itself; use it as a sizing placeholder or combine it with
 * custom drawing.
 *
 * ```kotlin
 * PretextText(
 *     text = "Hello, world!",
 *     style = TextStyle(fontSize = 16.sp),
 *     lineHeight = 20.sp,
 * )
 * ```
 *
 * @param text The string to lay out.
 * @param modifier Compose [Modifier] applied to the layout node.
 * @param style [TextStyle] controlling font size (other style properties are not yet forwarded).
 * @param lineHeight Uniform line height. Defaults to `20.sp`.
 */
@Composable
fun PretextText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    lineHeight: TextUnit = 20.sp,
) {
    val density = LocalDensity.current
    val resolvedLineHeight = with(density) { lineHeight.toPx() }

    val paint = remember(style, density) {
        TextPaint().apply {
            textSize = with(density) { (style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp).toPx() }
        }
    }

    val measurer = remember(paint) { PaintTextMeasurer(paint) }
    val segmenter = remember { IcuTextSegmenter() }

    val prepared = remember(text, paint) {
        Pretext.setSegmenter(segmenter)
        Pretext.prepare(text, measurer)
    }

    Layout(
        content = {},
        modifier = modifier,
    ) { _, constraints ->
        val maxWidth = constraints.maxWidth.toFloat()
        val result = Pretext.layout(prepared, maxWidth, resolvedLineHeight)
        val height = result.height.roundToInt()
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {}
    }
}
