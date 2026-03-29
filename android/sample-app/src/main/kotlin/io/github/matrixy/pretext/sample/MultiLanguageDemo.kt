package io.github.matrixy.pretext.sample

import android.content.res.Configuration
import android.text.TextPaint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.matrixy.pretext.Pretext
import io.github.matrixy.pretext.android.PaintTextMeasurer

@Composable
fun MultiLanguageDemo() {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val paint = remember { TextPaint().apply { textSize = with(density) { 16.sp.toPx() } } }
    val measurer = remember { PaintTextMeasurer(paint) }
    val lineHeightPx = with(density) { 22.sp.toPx() }
    val maxWidth = with(density) { 280.dp.toPx() }

    val samples = listOf(
        "English" to "The quick brown fox jumps over the lazy dog.",
        "CJK Mixed" to "\u4f60\u597d\u4e16\u754c\uff01Hello \u6625\u5929\u5230\u4e86\u3002\u685c\u304c\u54b2\u3044\u3066\u3044\u308b\u3002",
        "Arabic" to "\u0645\u0631\u062d\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645! \u0647\u0630\u0627 \u0646\u0635 \u0639\u0631\u0628\u064a \u0644\u0644\u0627\u062e\u062a\u0628\u0627\u0631",
        "Emoji" to "Hello \ud83c\udf0d\ud83c\udf0e\ud83c\udf0f! The future is \ud83d\ude80\ud83e\udd16\ud83d\udca1 and \ud83c\udfa8\u2728",
        "Korean" to "\ud55c\uad6d\uc5b4 \ud14d\uc2a4\ud2b8 \ub808\uc774\uc544\uc6c3 \ud14c\uc2a4\ud2b8\uc785\ub2c8\ub2e4. \uc798 \uc791\ub3d9\ud558\ub098\uc694?",
        "Thai" to "\u0e2a\u0e27\u0e31\u0e2a\u0e14\u0e35\u0e04\u0e23\u0e31\u0e1a \u0e19\u0e35\u0e48\u0e04\u0e37\u0e2d\u0e01\u0e32\u0e23\u0e17\u0e14\u0e2a\u0e2d\u0e1a\u0e20\u0e32\u0e29\u0e32\u0e44\u0e17\u0e22",
        "Mixed Bidi" to "Hello \u0645\u0631\u062d\u0628\u0627 World \u0639\u0627\u0644\u0645 123 \u0664\u0665\u0666",
        "Soft Hyphens" to "Sup\u00ADer\u00ADcal\u00ADi\u00ADfrag\u00ADil\u00ADis\u00ADtic is a long word",
    )

    DemoSection(title = "Multi-Language", subtitle = "CJK, Arabic, Thai, Emoji, Bidi") {
        if (isLandscape) {
            // Landscape: two-column grid of language samples
            val leftSamples = samples.take((samples.size + 1) / 2)
            val rightSamples = samples.drop((samples.size + 1) / 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    leftSamples.forEach { (label, text) ->
                        val prepared = remember(text) { Pretext.prepare(text, measurer) }
                        val result = Pretext.layout(prepared, maxWidth, lineHeightPx)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                                Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                "${result.lineCount}L / ${result.height.toInt()}px",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (label != leftSamples.last().first) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rightSamples.forEach { (label, text) ->
                        val prepared = remember(text) { Pretext.prepare(text, measurer) }
                        val result = Pretext.layout(prepared, maxWidth, lineHeightPx)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                                Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                "${result.lineCount}L / ${result.height.toInt()}px",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (label != rightSamples.last().first) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                samples.forEach { (label, text) ->
                    val prepared = remember(text) { Pretext.prepare(text, measurer) }
                    val result = Pretext.layout(prepared, maxWidth, lineHeightPx)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                            Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            "${result.lineCount}L / ${result.height.toInt()}px",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (label != samples.last().first) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
