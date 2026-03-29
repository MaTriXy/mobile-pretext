package io.github.matrixy.pretext.sample

import android.content.res.Configuration
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
fun HeightMeasurementDemo() {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxWidthDp = (configuration.screenWidthDp - 48f).coerceAtLeast(200f)
    val maxWidthPx = with(density) { maxWidthDp.dp.toPx() }
    var sliderValue by remember(maxWidthPx) { mutableFloatStateOf(minOf(maxWidthPx, 300f * density.density)) }

    val sampleText = "The quick brown fox jumps over the lazy dog. This paragraph demonstrates how Pretext can compute text height without triggering layout reflow. Try adjusting the width slider to see the height recalculate instantly."

    val paint = remember {
        TextPaint().apply { textSize = with(density) { 16.sp.toPx() } }
    }
    val measurer = remember { PaintTextMeasurer(paint) }
    val lineHeightPx = with(density) { 22.sp.toPx() }

    val prepared = remember(sampleText) { Pretext.prepare(sampleText, measurer) }
    val result = Pretext.layout(prepared, sliderValue, lineHeightPx)

    DemoSection(title = "Height Measurement", subtitle = "Compute paragraph height without layout") {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left column: slider + stats
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Width: ${sliderValue.toInt()}px", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = (100f * density.density)..maxWidthPx,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBadge("Lines", "${result.lineCount}")
                        StatBadge("Height", "${result.height.toInt()}px")
                    }
                }
                // Right column: rendered text
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        sampleText,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .width(with(density) { sliderValue.toDp() })
                            .background(Color(0xFF1A1A2E))
                            .border(1.dp, Color(0xFF3366FF).copy(alpha = 0.3f))
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Width: ${sliderValue.toInt()}px", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = (100f * density.density)..maxWidthPx,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }

                // Stats row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBadge("Lines", "${result.lineCount}")
                    StatBadge("Height", "${result.height.toInt()}px")
                }

                // Rendered text
                Text(
                    sampleText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .width(with(density) { sliderValue.toDp() })
                        .background(Color(0xFF1A1A2E))
                        .border(1.dp, Color(0xFF3366FF).copy(alpha = 0.3f))
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
