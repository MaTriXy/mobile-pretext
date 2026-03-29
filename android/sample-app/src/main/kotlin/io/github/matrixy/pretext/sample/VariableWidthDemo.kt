package io.github.matrixy.pretext.sample

import android.content.res.Configuration
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.matrixy.pretext.LayoutCursor
import io.github.matrixy.pretext.LayoutLine
import io.github.matrixy.pretext.Pretext
import io.github.matrixy.pretext.android.PaintTextMeasurer

@Composable
fun VariableWidthDemo() {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val sampleText = "This text flows around an obstacle, just like CSS float. The first few lines are narrower because they share space with the blue rectangle. Once past it, lines expand to full width. This demonstrates layoutNextLine() with variable widths."

    val paint = remember { TextPaint().apply { textSize = with(density) { 15.sp.toPx() } } }
    val measurer = remember { PaintTextMeasurer(paint) }
    val lineHeightPx = with(density) { 21.sp.toPx() }
    val containerWidthPx = with(density) { 320.dp.toPx() }
    val obstacleWidthPx = with(density) { 100.dp.toPx() }
    val obstacleHeightPx = with(density) { 80.dp.toPx() }

    val prepared = remember(sampleText) { Pretext.prepareWithSegments(sampleText, measurer) }

    val lines = remember(prepared, containerWidthPx) {
        val result = mutableListOf<Pair<LayoutLine, Float>>()
        var cursor = LayoutCursor(0, 0)
        var y = 0f
        while (true) {
            val availableWidth = if (y < obstacleHeightPx) containerWidthPx - obstacleWidthPx - with(density) { 12.dp.toPx() } else containerWidthPx
            val line = Pretext.layoutNextLine(prepared, cursor, availableWidth) ?: break
            result.add(line to availableWidth)
            cursor = line.end
            y += lineHeightPx
        }
        result
    }

    DemoSection(title = "Variable Width", subtitle = "layoutNextLine() \u2014 text around obstacles") {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left: obstacle + flowing text
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(320.dp)
                        .border(1.dp, Color(0xFF9966FF).copy(alpha = 0.3f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(100.dp, 80.dp)
                            .background(Color(0xFF3366FF).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Obstacle", fontSize = 11.sp, color = Color(0xFF3366FF))
                    }
                    Column {
                        lines.forEach { (line, width) ->
                            Text(
                                line.text,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                maxLines = 1,
                                modifier = Modifier.width(with(density) { width.toDp() }),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                // Right: explanation
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "How it works",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "layoutNextLine() is called repeatedly with a different available width for each line. Lines next to the obstacle get a narrower width, while lines below it use the full container width.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${lines.size} lines total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .border(1.dp, Color(0xFF9966FF).copy(alpha = 0.3f))
                    .padding(4.dp)
            ) {
                // Obstacle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(100.dp, 80.dp)
                        .background(Color(0xFF3366FF).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Obstacle", fontSize = 11.sp, color = Color(0xFF3366FF))
                }

                // Lines
                Column {
                    lines.forEach { (line, width) ->
                        Text(
                            line.text,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            maxLines = 1,
                            modifier = Modifier.width(with(density) { width.toDp() }),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
