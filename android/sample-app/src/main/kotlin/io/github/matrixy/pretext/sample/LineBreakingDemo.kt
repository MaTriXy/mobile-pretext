package io.github.matrixy.pretext.sample

import android.content.res.Configuration
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.matrixy.pretext.Pretext
import io.github.matrixy.pretext.android.PaintTextMeasurer

@Composable
fun LineBreakingDemo() {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxAvailableWidth = (configuration.screenWidthDp - 48f).coerceAtLeast(150f)

    // Width slider in dp — clamped to screen width
    var widthDp by remember(maxAvailableWidth) { mutableFloatStateOf(minOf(maxAvailableWidth, 320f)) }

    val sampleText = "AGI \u6625\u5929\u5230\u4e86. \u0628\u062f\u0623\u062a \u0627\u0644\u0631\u062d\u0644\u0629 \ud83d\ude80 The future of AI is multilingual and beautiful."

    // Measure in pixels matching 18sp
    val textSizePx = with(density) { 18.sp.toPx() }
    val lineHeightPx = with(density) { 28.sp.toPx() }
    val paint = remember(textSizePx) { TextPaint().apply { textSize = textSizePx } }
    val measurer = remember(paint) { PaintTextMeasurer(paint) }

    val prepared = remember(sampleText, measurer) { Pretext.prepareWithSegments(sampleText, measurer) }

    // Convert dp width to px for pretext
    val maxWidthPx = with(density) { widthDp.dp.toPx() }
    val result = remember(prepared, maxWidthPx, lineHeightPx) {
        Pretext.layoutWithLines(prepared, maxWidthPx, lineHeightPx)
    }

    DemoSection(title = "Line Breaking", subtitle = "layoutWithLines() \u2014 see each line") {
        if (isLandscape) {
            // Landscape: slider + stats on left, rendered text + line breakdown on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left column: slider + rendered text
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Width: ${widthDp.toInt()}dp",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = widthDp,
                            onValueChange = { widthDp = it },
                            valueRange = 120f..maxAvailableWidth,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    Text(
                        sampleText,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        modifier = Modifier
                            .width(widthDp.dp)
                            .border(1.dp, Color(0xFF55c6d9).copy(alpha = 0.4f))
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${result.lineCount} lines \u00b7 ${result.height.toInt()}px total height \u00b7 max width: ${maxWidthPx.toInt()}px",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Right column: line breakdown
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Pretext computed lines:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFF8C00).copy(alpha = 0.3f))
                            .padding(4.dp)
                    ) {
                        result.lines.forEachIndexed { index, line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (index % 2 == 0) Color.Transparent else Color.White.copy(alpha = 0.03f))
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF55c6d9),
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    line.text,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    "%.0fpx".format(line.width),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Width slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Width: ${widthDp.toInt()}dp",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = widthDp,
                        onValueChange = { widthDp = it },
                        valueRange = 120f..maxAvailableWidth,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }

                // Show the actual text rendered by the system at the constrained width
                Text(
                    sampleText,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier
                        .width(widthDp.dp)
                        .border(1.dp, Color(0xFF55c6d9).copy(alpha = 0.4f))
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Pretext computed lines:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show pretext line-by-line breakdown
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFF8C00).copy(alpha = 0.3f))
                        .padding(4.dp)
                ) {
                    result.lines.forEachIndexed { index, line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (index % 2 == 0) Color.Transparent else Color.White.copy(alpha = 0.03f))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Line number
                            Text(
                                "${index + 1}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF55c6d9),
                                modifier = Modifier.width(24.dp)
                            )

                            // Line text — show full text, allow horizontal scroll if needed
                            Text(
                                line.text,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )

                            // Line width in px
                            Text(
                                "%.0fpx".format(line.width),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // Stats
                Text(
                    "${result.lineCount} lines \u00b7 ${result.height.toInt()}px total height \u00b7 max width: ${maxWidthPx.toInt()}px",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
