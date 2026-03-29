package io.github.matrixy.pretext.sample

import android.content.res.Configuration
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.matrixy.pretext.LayoutCursor
import io.github.matrixy.pretext.Pretext
import io.github.matrixy.pretext.PreparedTextWithSegments
import io.github.matrixy.pretext.android.PaintTextMeasurer

// ---------- Data model ----------

private data class RichFragment(
    val kind: String,
    val text: String,
    val style: String = "",
    val tone: String = ""
)

private val noteFragments = listOf(
    RichFragment("text", "Ship ", style = "body"),
    RichFragment("chip", "@maya", tone = "mention"),
    RichFragment("text", "'s ", style = "body"),
    RichFragment("text", "rich-note", style = "code"),
    RichFragment("text", " card once ", style = "body"),
    RichFragment("text", "pre-wrap", style = "code"),
    RichFragment("text", " lands. Status ", style = "body"),
    RichFragment("chip", "blocked", tone = "status"),
    RichFragment("text", " by ", style = "body"),
    RichFragment("text", "vertical text", style = "link"),
    RichFragment("text", " research, but \u5317\u4EAC copy and Arabic QA are both green. Keep ", style = "body"),
    RichFragment("chip", "\u062C\u0627\u0647\u0632", tone = "status"),
    RichFragment("text", " for ", style = "body"),
    RichFragment("text", "Cmd+K", style = "code"),
    RichFragment("text", " docs; the review bundle now includes \u4E2D\u6587 labels, \u0639\u0631\u0628\u064A fallback, and one more launch pass for ", style = "body"),
    RichFragment("chip", "Fri 2:30 PM", tone = "time"),
    RichFragment("text", ". Keep ", style = "body"),
    RichFragment("text", "layoutNextLine()", style = "code"),
    RichFragment("text", " public, tag this ", style = "body"),
    RichFragment("chip", "P1", tone = "priority"),
    RichFragment("text", ", keep ", style = "body"),
    RichFragment("chip", "3 reviewers", tone = "count"),
    RichFragment("text", ", and route feedback to ", style = "body"),
    RichFragment("text", "design sync", style = "link"),
    RichFragment("text", ".", style = "body"),
)

// ---------- Color palette ----------

private val PanelBg = Color(0xFFFFFDF8)
private val Ink = Color(0xFF201B18)
private val Accent = Color(0xFF955F3B)

private val CodeBg = Color(0x14111F2B) // ~rgba(17,31,43,0.08)

private val ChipMentionBg = Color(0x1F155A88)
private val ChipMentionFg = Color(0xFF155A88)
private val ChipStatusBg = Color(0x1FC48114)
private val ChipStatusFg = Color(0xFF916207)
private val ChipPriorityBg = Color(0x1AB02C2C)
private val ChipPriorityFg = Color(0xFF8E2323)
private val ChipTimeBg = Color(0x1C46764D)
private val ChipTimeFg = Color(0xFF355F38)
private val ChipCountBg = Color(0x1A43397A)
private val ChipCountFg = Color(0xFF483E83)

// ---------- Placed items ----------

private sealed class PlacedItem {
    abstract val x: Float
    abstract val y: Float
}

private data class PlacedText(
    override val x: Float,
    override val y: Float,
    val text: String,
    val style: String,
    val width: Float
) : PlacedItem()

private data class PlacedChip(
    override val x: Float,
    override val y: Float,
    val text: String,
    val tone: String,
    val width: Float
) : PlacedItem()

// ---------- Pre-measured fragment ----------

private data class MeasuredFragment(
    val fragment: RichFragment,
    val totalWidth: Float,
    val prepared: PreparedTextWithSegments?
)

// ---------- Composable ----------

@Composable
fun RichNoteDemo() {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val panelPad = 16f // note panel internal padding (each side)

    // Measure actual available width at runtime, not with static math
    var measuredMaxWidthDp by remember { mutableFloatStateOf(300f) }
    var containerWidthDp by remember { mutableFloatStateOf(280f) }

    // Paints for each text style
    val bodyPaint = remember {
        TextPaint().apply {
            textSize = with(density) { 17.sp.toPx() }
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }
    val codePaint = remember {
        TextPaint().apply {
            textSize = with(density) { 14.sp.toPx() }
            typeface = Typeface.MONOSPACE
        }
    }
    val chipPaint = remember {
        TextPaint().apply {
            textSize = with(density) { 12.sp.toPx() }
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    val bodyMeasurer = remember { PaintTextMeasurer(bodyPaint) }
    val codeMeasurer = remember { PaintTextMeasurer(codePaint) }
    val chipMeasurer = remember { PaintTextMeasurer(chipPaint) }

    val lineHeightDp = 34f
    val chipHeightDp = 24f
    val inlineGapDp = 4f
    val chipHPadDp = 10f
    val codeHPadDp = 7f
    val codeVPadDp = 2f

    val lineHeightPx = with(density) { lineHeightDp.dp.toPx() }
    val inlineGapPx = with(density) { inlineGapDp.dp.toPx() }
    val chipHPadPx = with(density) { chipHPadDp.dp.toPx() }
    val codeHPadPx = with(density) { codeHPadDp.dp.toPx() }

    // Prepare all fragments once (measurement is density-stable)
    val measured = remember {
        noteFragments.map { frag ->
            when (frag.kind) {
                "chip" -> {
                    val prep = Pretext.prepareWithSegments(frag.text, chipMeasurer)
                    val textWidth = Pretext.layoutWithLines(prep, Float.MAX_VALUE, lineHeightPx)
                        .lines.firstOrNull()?.width ?: 0f
                    // Add small buffer for Compose rendering variance
                    MeasuredFragment(frag, textWidth + chipHPadPx * 2 + 4f, prep)
                }
                "text" -> {
                    val meas = if (frag.style == "code") codeMeasurer else bodyMeasurer
                    val prep = Pretext.prepareWithSegments(frag.text.trim(), meas)
                    val textWidth = Pretext.layoutWithLines(prep, Float.MAX_VALUE, lineHeightPx)
                        .lines.firstOrNull()?.width ?: 0f
                    val chrome = if (frag.style == "code") codeHPadPx * 2 + 4f else 2f
                    MeasuredFragment(frag, textWidth + chrome, prep)
                }
                else -> MeasuredFragment(frag, 0f, null)
            }
        }
    }

    // Layout: place fragments left-to-right, wrapping when width is exceeded.
    // containerWidthDp controls the CONTENT width (inside the panel padding)
    val placedItems = remember(containerWidthDp) {
        val maxWidthPx = with(density) { containerWidthDp.dp.toPx() }
        val items = mutableListOf<PlacedItem>()
        var cx = 0f   // current x in pixels
        var cy = 0f   // current y in pixels

        for (mf in measured) {
            val frag = mf.fragment
            val totalWidth = mf.totalWidth

            when (frag.kind) {
                "chip" -> {
                    if (cx > 0f && cx + totalWidth > maxWidthPx) {
                        cx = 0f
                        cy += lineHeightPx
                    }
                    items.add(PlacedChip(cx, cy, frag.text, frag.tone, totalWidth))
                    cx += totalWidth + inlineGapPx
                }
                "text" -> {
                    val chrome = if (frag.style == "code") codeHPadPx * 2 + 4f else 2f
                    val prep = mf.prepared ?: continue
                    val displayText = frag.text.trim()
                    if (displayText.isEmpty()) {
                        cx += inlineGapPx
                        continue
                    }

                    // Add leading gap for spaces in original text
                    if (frag.text.startsWith(" ") && cx > 0f) cx += inlineGapPx

                    val remaining = maxWidthPx - cx

                    if (totalWidth <= remaining) {
                        // Fragment fits on the current line
                        items.add(PlacedText(cx, cy, displayText, frag.style, totalWidth))
                        cx += totalWidth + if (frag.text.endsWith(" ")) inlineGapPx else 0f
                    } else {
                        // Split this fragment across lines using layoutNextLine()
                        var cursor = LayoutCursor(0, 0)
                        var firstChunk = true
                        while (true) {
                            val availForText = (if (firstChunk) remaining else maxWidthPx) - chrome
                            if (availForText <= 1f) {
                                cx = 0f
                                cy += lineHeightPx
                                firstChunk = false
                                continue
                            }
                            val line = Pretext.layoutNextLine(prep, cursor, availForText) ?: break
                            val chunkWidth = line.width + chrome
                            items.add(PlacedText(cx, cy, line.text, frag.style, chunkWidth))
                            cx += chunkWidth + inlineGapPx
                            cursor = line.end
                            if (cursor.segmentIndex >= prep.segments.size) break
                            // Advance to next line for the remainder
                            cx = 0f
                            cy += lineHeightPx
                            firstChunk = false
                        }
                    }
                }
            }
        }
        items
    }

    val totalHeightPx = remember(placedItems) {
        val maxY = placedItems.maxOfOrNull { it.y } ?: 0f
        maxY + lineHeightPx
    }

    // Compute layout stats
    val lineCount = remember(placedItems) {
        val distinctYs = placedItems.map { it.y }.distinct().size
        distinctYs
    }
    val splitCount = remember(placedItems) {
        // Count text items that start at x=0 but are continuations (same fragment split across lines)
        placedItems.count { it is PlacedText }
    }

    // ---------- UI ----------

    // Shared panel composable
    @Composable
    fun NotePanel() {
        // Use BoxWithConstraints to measure REAL available width
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val realMaxContentDp = maxWidth.value - panelPad * 2
            // Update slider max when measured
            LaunchedEffect(realMaxContentDp) {
                measuredMaxWidthDp = realMaxContentDp
                if (containerWidthDp > realMaxContentDp) containerWidthDp = realMaxContentDp
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelBg)
                    .padding(panelPad.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(containerWidthDp.dp)
                        .height(with(density) { totalHeightPx.toDp() })
                ) {
                    placedItems.forEach { item ->
                        when (item) {
                            is PlacedText -> RenderTextItem(item, density, lineHeightDp, codeHPadDp, codeVPadDp)
                            is PlacedChip -> RenderChipItem(item, density, lineHeightDp, chipHeightDp, chipHPadDp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ControlsAndStats() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Width: ${containerWidthDp.toInt()}dp", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = containerWidthDp,
                onValueChange = { containerWidthDp = it },
                valueRange = 150f..measuredMaxWidthDp,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBadge("Lines", "$lineCount")
            StatBadge("Fragments", "$splitCount")
            StatBadge("Height", "${(totalHeightPx / density.density).toInt()}dp")
        }
        Text("Pretext layoutNextLine() computes each line break", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    DemoSection(
        title = "Rich Text",
        subtitle = "Mixed inline elements with live reflow via layoutNextLine()"
    ) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlsAndStats()
                }
                Column(modifier = Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotePanel()
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlsAndStats()
                NotePanel()
            }
        }
    }
}

@Composable
private fun BoxScope.RenderTextItem(
    item: PlacedText,
    density: androidx.compose.ui.unit.Density,
    lineHeightDp: Float,
    codeHPadDp: Float,
    codeVPadDp: Float
) {
    val xDp = with(density) { item.x.toDp() }
    val yDp = with(density) { item.y.toDp() }
    val wDp = with(density) { item.width.toDp() }

    when (item.style) {
        "code" -> {
            Box(
                modifier = Modifier
                    .offset(x = xDp, y = yDp + ((lineHeightDp.dp - 22.dp) / 2))
                    .clip(RoundedCornerShape(9.dp))
                    .background(CodeBg)
                    .padding(horizontal = codeHPadDp.dp, vertical = codeVPadDp.dp)
            ) {
                Text(
                    item.text,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Ink,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        "link" -> {
            Text(
                item.text,
                fontSize = 17.sp,
                color = Accent,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(x = xDp, y = yDp + ((lineHeightDp.dp - 20.dp) / 2))
            )
        }
        else -> {
            Text(
                item.text,
                fontSize = 17.sp,
                color = Ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(x = xDp, y = yDp + ((lineHeightDp.dp - 20.dp) / 2))
            )
        }
    }
}

@Composable
private fun BoxScope.RenderChipItem(
    item: PlacedChip,
    density: androidx.compose.ui.unit.Density,
    lineHeightDp: Float,
    chipHeightDp: Float,
    chipHPadDp: Float
) {
    val xDp = with(density) { item.x.toDp() }
    val yDp = with(density) { item.y.toDp() }
    val (bg, fg) = chipColors(item.tone)

    Box(
        modifier = Modifier
            .offset(x = xDp, y = yDp + ((lineHeightDp.dp - chipHeightDp.dp) / 2))
            .height(chipHeightDp.dp)
            .clip(RoundedCornerShape(chipHeightDp.dp / 2))
            .background(bg)
            .padding(horizontal = chipHPadDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            item.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1
        )
    }
}

private fun chipColors(tone: String): Pair<Color, Color> = when (tone) {
    "mention" -> ChipMentionBg to ChipMentionFg
    "status" -> ChipStatusBg to ChipStatusFg
    "priority" -> ChipPriorityBg to ChipPriorityFg
    "time" -> ChipTimeBg to ChipTimeFg
    "count" -> ChipCountBg to ChipCountFg
    else -> Color.LightGray to Color.DarkGray
}
