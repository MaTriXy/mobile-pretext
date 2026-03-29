package io.github.matrixy.pretext.sample

import android.content.res.Configuration
import android.text.TextPaint
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.matrixy.pretext.LayoutResult
import io.github.matrixy.pretext.Pretext
import io.github.matrixy.pretext.android.PaintTextMeasurer
import kotlinx.coroutines.launch

private val corpusTexts = listOf(
    "The quick brown fox jumps over the lazy dog.",
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor.",
    "AGI \u6625\u5929\u5230\u4e86. \u0628\u062f\u0623\u062a \u0627\u0644\u0631\u062d\u0644\u0629 \ud83d\ude80 The future is multilingual.",
    "Kotlin is a modern programming language for building Android applications.",
    "\u4f60\u597d\u4e16\u754c\uff01Hola Mundo! Bonjour le monde! \u3053\u3093\u306b\u3061\u306f\u4e16\u754c\uff01",
    "\u0645\u0631\u062d\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645! Hello World! \u05e9\u05dc\u05d5\u05dd \u05e2\u05d5\u05dc\u05dd!",
    "\ud55c\uad6d\uc5b4 \ud14d\uc2a4\ud2b8 \ub808\uc774\uc544\uc6c3 \ud14c\uc2a4\ud2b8\uc785\ub2c8\ub2e4. \uc798 \uc791\ub3d9\ud558\ub098\uc694?",
    "Text layout without triggering native layout passes, pure arithmetic on cached widths.",
    "Pretext measures once with prepare(), then layouts instantly on every resize.",
    "\u30c6\u30ad\u30b9\u30c8\u306e\u30ec\u30a4\u30a2\u30a6\u30c8\u3092\u7d14\u7c8b\u306a\u7b97\u8853\u3067\u8a08\u7b97\u3002",
    "Swift Package for iOS using CoreText, Kotlin library for Android using TextPaint.",
    "Mixed bidirectional text with Hebrew \u05e2\u05d1\u05e8\u05d9\u05ea and Arabic \u0639\u0631\u0628\u064a inline.",
)

private data class RunResult(
    val textCount: Int,
    val widthDp: Float,
    val prepareMs: Double,
    val layoutMs: Double,
    val layoutPerTextUs: Double,
    val totalLines: Int,
    val results: List<Pair<String, LayoutResult>>
)

// Item indices for scroll targeting
private const val ITEM_CONTROLS = 0
// ITEM_CONTROLS + 1 = header "N texts rendered..."
// Then N text items
// Then divider, stats header, stat cards, explanation, bottom spacer

@Composable
fun PerformanceBenchmark() {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxWidthDp = (configuration.screenWidthDp - 64f).coerceAtLeast(200f)

    var textCount by remember { mutableIntStateOf(100) }
    var widthDp by remember { mutableFloatStateOf(minOf(maxWidthDp, 280f)) }
    var isRunning by remember { mutableStateOf(false) }
    var runResult by remember { mutableStateOf<RunResult?>(null) }

    val paint = remember { TextPaint().apply { textSize = with(density) { 14.sp.toPx() } } }
    val measurer = remember { PaintTextMeasurer(paint) }
    val lineHeightPx = with(density) { 20.sp.toPx() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Compute stats item index (after controls + header + all text items + divider)
    val statsIndex = remember(runResult) {
        val r = runResult ?: return@remember -1
        1 + 1 + r.results.size  // controls + header + texts -> divider is at this index
    }

    // Determine zone based on what's currently visible
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val lastVisible by remember { derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }}
    val hasResults = runResult != null
    val isInTextsZone = hasResults && firstVisible >= 2 && lastVisible < statsIndex
    val isInStatsZone = hasResults && statsIndex > 0 && lastVisible >= statsIndex

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Controls ────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Performance Test", style = MaterialTheme.typography.titleMedium)
                    Text("Set parameters, run, scroll through rendered texts, then see stats", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(4.dp))

                    if (isLandscape) {
                        // Side-by-side sliders in landscape
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Text count: $textCount", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                Slider(value = textCount.toFloat(), onValueChange = { textCount = it.toInt() }, valueRange = 10f..500f, steps = 48)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Layout width: ${widthDp.toInt()}dp", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                Slider(value = widthDp, onValueChange = { widthDp = it }, valueRange = 100f..maxWidthDp)
                            }
                        }
                    } else {
                        Text("Text count: $textCount", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        Slider(value = textCount.toFloat(), onValueChange = { textCount = it.toInt() }, valueRange = 10f..500f, steps = 48)

                        Text("Layout width: ${widthDp.toInt()}dp", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        Slider(value = widthDp, onValueChange = { widthDp = it }, valueRange = 100f..maxWidthDp)
                    }

                    Button(
                        onClick = {
                            isRunning = true
                            val texts = (0 until textCount).map { corpusTexts[it % corpusTexts.size] }
                            val widthPx = with(density) { widthDp.dp.toPx() }

                            Pretext.clearCache()
                            val t0 = System.nanoTime()
                            val prepared = texts.map { Pretext.prepare(it, measurer) }
                            val t1 = System.nanoTime()
                            val prepareMs = (t1 - t0) / 1_000_000.0

                            val iterations = 5
                            val t2 = System.nanoTime()
                            var lastResults = listOf<LayoutResult>()
                            repeat(iterations) { lastResults = prepared.map { Pretext.layout(it, widthPx, lineHeightPx) } }
                            val t3 = System.nanoTime()
                            val layoutMs = (t3 - t2) / 1_000_000.0 / iterations
                            val perTextUs = layoutMs * 1000.0 / textCount
                            val totalLines = lastResults.sumOf { it.lineCount }

                            runResult = RunResult(textCount, widthDp, prepareMs, layoutMs, perTextUs, totalLines, texts.zip(lastResults))
                            isRunning = false

                            // Scroll to show the first rendered text
                            scope.launch { listState.animateScrollToItem(2) }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRunning) "Running..." else "Run Test ($textCount texts at ${widthDp.toInt()}dp)")
                    }
                }
            }

            // ── Rendered texts ───────────────────────────────────
            runResult?.let { result ->
                item {
                    Text(
                        "${result.results.size} texts rendered at ${result.widthDp.toInt()}dp",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                itemsIndexed(result.results) { index, (text, layout) ->
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("#${index + 1}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF55c6d9))
                            Text("${layout.lineCount} lines \u00b7 ${layout.height.toInt()}px", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF84d96c))
                        }
                        Text(
                            text,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(result.widthDp.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                    }
                }

                // ── Divider ─────────────────────────────────────
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))
                }

                // ── Stats ───────────────────────────────────────
                item {
                    Text("Stats", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ResultCard("prepare()", "%.2fms".format(result.prepareMs), "One-time", Color(0xFFff9c5b), Modifier.weight(1f))
                        ResultCard("layout()", "%.3fms".format(result.layoutMs), "Per resize", Color(0xFF84d96c), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ResultCard("Per text", "%.2f\u00b5s".format(result.layoutPerTextUs), "layout() cost", Color(0xFF55c6d9), Modifier.weight(1f))
                        ResultCard("Total lines", "${result.totalLines}", "All texts", Color(0xFFf0c35f), Modifier.weight(1f))
                    }
                }
                item {
                    val ratio = result.prepareMs / maxOf(result.layoutMs, 0.001)
                    Text(
                        "layout() is %.0fx faster than prepare(). prepare() runs once when text appears, layout() runs instantly on every width change.".format(ratio),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(Modifier.height(60.dp)) // room for snackbar
                }
            }
        }

        // ── Floating snackbar ────────────────────────────────
        AnimatedVisibility(
            visible = isInTextsZone,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) {
            ElevatedButton(
                onClick = { scope.launch { listState.animateScrollToItem(statsIndex) } },
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Color(0xFF84d96c),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Show Results", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        AnimatedVisibility(
            visible = isInStatsZone,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) {
            ElevatedButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Color(0xFF55c6d9),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Jump Back Up", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ResultCard(label: String, value: String, subtitle: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = accent)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
