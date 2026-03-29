package io.github.matrixy.pretext.sample.breaker

import android.graphics.*
import android.text.TextPaint

// ── Rendering extension functions for BreakerGameView ──────────────

internal fun BreakerGameView.drawField(canvas: Canvas) {
    fillPaint.shader = LinearGradient(0f, fieldRect.top, 0f, fieldRect.bottom, 0xFF03080f.toInt(), 0xFF0b1520.toInt(), Shader.TileMode.CLAMP)
    canvas.drawRoundRect(fieldRect, 12f, 12f, fillPaint)
    fillPaint.shader = null
    strokePaint.color = 0xFF75d7e6.toInt(); strokePaint.alpha = 80
    canvas.drawRoundRect(fieldRect, 12f, 12f, strokePaint)
}

internal fun BreakerGameView.drawTextWall(canvas: Canvas) {
    val prepared = wallPrepared ?: return
    val margin = 14f * dp
    val left = fieldRect.left + margin
    val right = fieldRect.right - margin
    val lineH = 13f * dp

    var cursor = io.github.matrixy.pretext.LayoutCursor(0, 0)
    var y = fieldRect.top + margin

    while (y + lineH <= fieldRect.bottom - margin) {
        // Find blocked intervals on this row
        val blocked = mutableListOf<Pair<Float, Float>>()

        // Bricks block text
        for (brick in bricks) {
            if (!brick.alive) continue
            if (y + lineH < brick.y - 3f * dp || y > brick.y + brick.height + 3f * dp) continue
            blocked.add((brick.x - 8f * dp) to (brick.x + brick.width + 8f * dp))
        }

        // Ball blocks text (circle clearance)
        val ballRadius = 38f * dp
        val dy = (y + lineH / 2) - ball.y
        if (kotlin.math.abs(dy) < ballRadius) {
            val dx = kotlin.math.sqrt(ballRadius * ballRadius - dy * dy)
            blocked.add((ball.x - dx) to (ball.x + dx))
        }

        // Paddle blocks text
        val paddleTop = paddle.y - 6f * dp
        val paddleBottom = paddle.y + paddle.height + 6f * dp
        if (y + lineH > paddleTop && y < paddleBottom) {
            blocked.add((paddle.x - paddle.halfW - 12f * dp) to (paddle.x + paddle.halfW + 12f * dp))
        }

        // Wake holes block text (fading circular exclusions)
        for (hole in wakeHoles) {
            val hdy = (y + lineH / 2) - hole.y
            val fadeRadius = hole.radius * dp * (1f - hole.life / hole.maxLife)
            if (kotlin.math.abs(hdy) < fadeRadius) {
                val hdx = kotlin.math.sqrt(fadeRadius * fadeRadius - hdy * hdy)
                blocked.add((hole.x - hdx) to (hole.x + hdx))
            }
        }

        // Carve slots from the full row width
        val slots = carveSlots(left, right, blocked)

        // Fill each slot with text using layoutNextLine
        for (slot in slots) {
            val slotWidth = slot.second - slot.first
            if (slotWidth < 18f * dp) continue

            val line = io.github.matrixy.pretext.Pretext.layoutNextLine(prepared, cursor, slotWidth)
            if (line == null) {
                // Reset cursor to loop the text
                cursor = io.github.matrixy.pretext.LayoutCursor(0, 0)
                val retryLine = io.github.matrixy.pretext.Pretext.layoutNextLine(prepared, cursor, slotWidth)
                if (retryLine != null) {
                    drawWallLine(canvas, retryLine.text, slot.first, y)
                    cursor = retryLine.end
                }
                continue
            }
            drawWallLine(canvas, line.text, slot.first, y)
            cursor = line.end
        }

        y += lineH
    }
}

internal fun BreakerGameView.carveSlots(left: Float, right: Float, blocked: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (blocked.isEmpty()) return listOf(left to right)

    // Clamp and merge blocked intervals
    val clamped = blocked.map { maxOf(left, it.first) to minOf(right, it.second) }
        .filter { it.second > it.first }
        .sortedBy { it.first }

    val merged = mutableListOf<Pair<Float, Float>>()
    for (interval in clamped) {
        if (merged.isEmpty() || interval.first > merged.last().second) {
            merged.add(interval)
        } else {
            merged[merged.lastIndex] = merged.last().first to maxOf(merged.last().second, interval.second)
        }
    }

    // Get free slots between blocked intervals
    val slots = mutableListOf<Pair<Float, Float>>()
    var cur = left
    for (interval in merged) {
        if (interval.first - cur >= 18f * dp) {
            slots.add(cur to interval.first)
        }
        cur = maxOf(cur, interval.second)
    }
    if (right - cur >= 18f * dp) {
        slots.add(cur to right)
    }
    return slots
}

internal fun BreakerGameView.drawWallLine(canvas: Canvas, text: String, x: Float, y: Float) {
    val lineH = 13f * dp
    wallPaint.color = wallColors[((y / lineH).toInt()) % wallColors.size]
    wallPaint.alpha = 133 // 52% opacity matching web game
    canvas.drawText(text, x, y + wallPaint.textSize, wallPaint)
}

internal fun BreakerGameView.drawBricks(canvas: Canvas) {
    for (b in bricks) {
        if (!b.alive) continue
        val r = 4f * dp
        fillPaint.color = b.color; fillPaint.alpha = 46; fillPaint.shader = null
        canvas.drawRoundRect(b.x, b.y, b.x + b.width, b.y + b.height, r, r, fillPaint)
        strokePaint.color = b.color; strokePaint.alpha = 153; strokePaint.strokeWidth = 1.5f * dp
        canvas.drawRoundRect(b.x, b.y, b.x + b.width, b.y + b.height, r, r, strokePaint)
        brickPaint.color = b.color
        // Center text in brick (matching iOS)
        val tw = brickPaint.measureText(b.word)
        val fm = brickPaint.fontMetrics
        canvas.drawText(b.word, b.x + (b.width - tw) / 2f, b.y + (b.height - fm.ascent - fm.descent) / 2f, brickPaint)
    }
}

internal fun BreakerGameView.drawPowerUps(canvas: Canvas) {
    for (pu in powerUps.toList()) {
        val tw = puPaint.measureText(pu.label)
        val fm = puPaint.fontMetrics
        // Background pill (matching iOS)
        val pillW = 50f * dp; val pillH = 20f * dp
        fillPaint.color = pu.color; fillPaint.alpha = 38; fillPaint.shader = null
        canvas.drawRoundRect(pu.x - pillW / 2f, pu.y - pillH / 2f, pu.x + pillW / 2f, pu.y + pillH / 2f, 10f * dp, 10f * dp, fillPaint)
        strokePaint.color = pu.color; strokePaint.alpha = 128; strokePaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(pu.x - pillW / 2f, pu.y - pillH / 2f, pu.x + pillW / 2f, pu.y + pillH / 2f, 10f * dp, 10f * dp, strokePaint)
        // Label
        puPaint.color = pu.color
        canvas.drawText(pu.label, pu.x - tw / 2f, pu.y - (fm.ascent + fm.descent) / 2f, puPaint)
    }
}

internal fun BreakerGameView.drawParticles(canvas: Canvas) {
    for (p in particles.toList()) {
        particlePaint.color = p.color; particlePaint.alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
        canvas.drawText(p.ch, p.x, p.y, particlePaint)
    }
}

internal fun BreakerGameView.drawGuardLine(canvas: Canvas) {
    if (guardCharges <= 0) return
    strokePaint.color = 0xFFa4f094.toInt(); strokePaint.alpha = 200; strokePaint.strokeWidth = 3f
    canvas.drawLine(fieldRect.left + 4f, fieldRect.bottom - 4f, fieldRect.right - 4f, fieldRect.bottom - 4f, strokePaint)
}

internal fun BreakerGameView.drawPaddle(canvas: Canvas) {
    val text = if (paddle.widenTimer > 0f) paddleWide else paddleNormal
    val tw = paddlePaint.measureText(text)
    val fm = paddlePaint.fontMetrics
    // Glow rect behind paddle (matching iOS)
    fillPaint.color = 0xFFf5f0df.toInt(); fillPaint.alpha = 15; fillPaint.shader = null
    val glowR = RectF(paddle.x - paddle.halfW - 4f * dp, paddle.y - 4f * dp, paddle.x + paddle.halfW + 4f * dp, paddle.y + paddle.height + 4f * dp)
    canvas.drawRoundRect(glowR, 6f * dp, 6f * dp, fillPaint)
    // Paddle text
    paddlePaint.color = 0xFFf5f0df.toInt()
    paddlePaint.setShadowLayer(14f, 0f, 0f, 0x5975d7e6)
    canvas.drawText(text, paddle.x - tw / 2f, paddle.y - (fm.ascent + fm.descent) / 2f, paddlePaint)
    paddlePaint.clearShadowLayer()
}

internal fun BreakerGameView.drawBall(canvas: Canvas) {
    val tw = ballPaint.measureText(ballChar)
    val fm = ballPaint.fontMetrics
    ballPaint.color = 0xFFf5f0df.toInt()
    ballPaint.setShadowLayer(16f, 0f, 0f, 0x40f5f0df)
    canvas.drawText(ballChar, ball.x - tw / 2f, ball.y - (fm.ascent + fm.descent) / 2f, ballPaint)
    ballPaint.clearShadowLayer()
}

internal fun BreakerGameView.drawHud(canvas: Canvas) {
    // Title
    titlePaint.color = 0xFFf6f2df.toInt()
    canvas.drawText("PRETEXT BREAKER", hudRect.left + 8f, hudRect.top + 44f, titlePaint)

    // Score / Lives / Level
    hudPaint.color = 0xFFf6f2df.toInt()
    val status = "SCORE ${score.toString().padStart(5, '0')}   LIVES ${"$\u2764".repeat(lives)}   LEVEL ${level.toString().padStart(2, '0')}"
    canvas.drawText(status, hudRect.left + 8f, hudRect.top + 80f, hudPaint)

    // Active effects
    hudPaint.alpha = 160
    var ey = hudRect.top + 102f
    if (paddle.widenTimer > 0f) { canvas.drawText("WIDEN ${paddle.widenTimer.toInt()}s", hudRect.left + 8f, ey, hudPaint); ey += 18f }
    if (slowTimer > 0f) { canvas.drawText("SLOW ${slowTimer.toInt()}s", hudRect.left + 8f, ey, hudPaint); ey += 18f }
    if (guardCharges > 0) { hudPaint.color = 0xFFa4f094.toInt(); canvas.drawText(if (guardCharges > 1) "GUARD x$guardCharges" else "GUARD READY", hudRect.left + 8f, ey, hudPaint) }
    hudPaint.alpha = 255
}

internal fun BreakerGameView.drawFooter(canvas: Canvas) {
    hudPaint.color = 0xFFf2e6bf.toInt(); hudPaint.alpha = 120
    val remaining = bricks.count { it.alive }
    canvas.drawText("$remaining words remain", footerRect.left + 18f, footerRect.top + 20f, hudPaint)
    hudPaint.alpha = 255
}

internal fun BreakerGameView.drawOverlay(canvas: Canvas) {
    val cx = fieldRect.centerX(); val cy = fieldRect.centerY()
    when (state) {
        GState.SERVE -> {
            // No dim overlay for serve — just the prompt text (matching iOS)
            overlayPaint.color = 0xFFf6f2df.toInt()
            overlayPaint.alpha = 179 // 70% opacity matching iOS
            overlayPaint.textSize = 20f * dp
            drawCentered(canvas, "TAP TO LAUNCH", overlayPaint, cx, cy + 40f * dp)
            overlayPaint.alpha = 255
        }
        GState.OVER -> {
            // Dim background (matching iOS 80% opacity)
            fillPaint.color = 0x03050A; fillPaint.shader = null; fillPaint.alpha = 204
            canvas.drawRect(fieldRect, fillPaint)

            overlayPaint.color = 0xFFff6666.toInt()
            overlayPaint.textSize = 36f * dp
            drawCentered(canvas, "GAME OVER", overlayPaint, cx, cy - 30f * dp)

            overlayPaint.color = 0xFFf6f2df.toInt()
            overlayPaint.textSize = 20f * dp
            drawCentered(canvas, "FINAL SCORE: $score", overlayPaint, cx, cy + 20f * dp)

            overlayPaint.textSize = 16f * dp
            overlayPaint.alpha = 153
            drawCentered(canvas, "TAP TO RESTART", overlayPaint, cx, cy + 60f * dp)
            overlayPaint.alpha = 255
        }
        GState.CLEARED -> {
            // Dim background (matching iOS 70% opacity)
            fillPaint.color = 0x03050A; fillPaint.shader = null; fillPaint.alpha = 179
            canvas.drawRect(fieldRect, fillPaint)

            overlayPaint.color = 0xFF84d96c.toInt()
            overlayPaint.textSize = 32f * dp
            drawCentered(canvas, "WAVE CLEARED!", overlayPaint, cx, cy - 20f * dp)

            overlayPaint.color = 0xFFf6f2df.toInt()
            overlayPaint.textSize = 16f * dp
            overlayPaint.alpha = 153
            drawCentered(canvas, "TAP FOR LEVEL ${level + 1}", overlayPaint, cx, cy + 30f * dp)
            overlayPaint.alpha = 255
        }
        else -> {}
    }
    // Reset overlay paint to default size
    overlayPaint.textSize = 22f * dp
}

internal fun BreakerGameView.drawCentered(canvas: Canvas, text: String, paint: TextPaint, cx: Float, cy: Float) {
    val tw = paint.measureText(text); val fm = paint.fontMetrics
    canvas.drawText(text, cx - tw / 2f, cy - (fm.ascent + fm.descent) / 2f, paint)
}
