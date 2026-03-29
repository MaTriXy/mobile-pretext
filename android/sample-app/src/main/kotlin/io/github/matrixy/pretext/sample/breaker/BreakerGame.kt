package io.github.matrixy.pretext.sample.breaker

import android.graphics.*
import android.text.TextPaint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import io.github.matrixy.pretext.*
import io.github.matrixy.pretext.android.PaintTextMeasurer
import kotlin.math.*
import kotlin.random.Random

class BreakerGameView(context: android.content.Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    // Screen dimensions in actual pixels
    internal val dp = resources.displayMetrics.density
    internal var screenW = 0f
    internal var screenH = 0f

    // Skip HUD drawing when Compose toolbar handles it
    var skipHud = false

    // Regions in actual pixels — recomputed when screen size changes
    internal var hudRect = RectF()
    internal var fieldRect = RectF()
    internal var footerRect = RectF()
    private var brickArea = RectF()

    // Fonts scaled with density
    private val mono = Typeface.MONOSPACE
    internal val brickPaint = TextPaint().apply { textSize = 20f * dp; typeface = mono; isAntiAlias = true; isFakeBoldText = true }
    internal val paddlePaint = TextPaint().apply { textSize = 18f * dp; typeface = mono; isAntiAlias = true; isFakeBoldText = true }
    internal val ballPaint = TextPaint().apply { textSize = 22f * dp; typeface = mono; isAntiAlias = true; isFakeBoldText = true }
    internal val hudPaint = TextPaint().apply { textSize = 15f * dp; typeface = mono; isAntiAlias = true }
    internal val titlePaint = TextPaint().apply { textSize = 28f * dp; typeface = mono; isAntiAlias = true; isFakeBoldText = true }
    internal val wallPaint = TextPaint().apply { textSize = 9f * dp; typeface = mono; isAntiAlias = true }
    internal val particlePaint = TextPaint().apply { textSize = 12f * dp; typeface = mono; isAntiAlias = true }
    internal val puPaint = TextPaint().apply { textSize = 13f * dp; typeface = mono; isAntiAlias = true; isFakeBoldText = true }
    internal val overlayPaint = TextPaint().apply { textSize = 22f * dp; typeface = mono; isAntiAlias = true; isFakeBoldText = true }
    internal val fillPaint = Paint().apply { isAntiAlias = true }
    internal val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f }

    private val measurer = PaintTextMeasurer(brickPaint)
    private val wallMeasurer = PaintTextMeasurer(wallPaint)

    // Game state
    internal var state = GState.SERVE
    internal var level = 1
    internal var score = 0
    internal var bestScore = 0
    internal var lives = 3
    internal var ball = Ball(0f, 0f, 0f, 0f, 330f * dp, 10f * dp)
    internal var paddle = Paddle(0f, 0f, 0f, 24f * dp)
    internal val bricks = mutableListOf<Brick>()
    internal val particles = mutableListOf<Particle>()
    internal val powerUps = mutableListOf<PowerUp>()
    internal var guardCharges = 0
    internal var slowTimer = 0f
    internal var shakeAmount = 0f
    internal var clearedTimer = 0f

    // Text wall (background flowing text that flows around obstacles)
    internal var wallPrepared: PreparedTextWithSegments? = null

    // Wake holes -- temporary exclusion zones the ball leaves behind
    internal val wakeHoles = mutableListOf<WakeHole>()
    private var lastWakeX = 0f
    private var lastWakeY = 0f

    internal val paddleNormal = "\u27E6==========\u27E7"
    internal val paddleWide = "\u27E6==============\u27E7"
    internal val ballChar = "\u25C9"

    private val brickColors = intArrayOf(0xFFff9c5b.toInt(), 0xFFf0c35f.toInt(), 0xFF84d96c.toInt(), 0xFF55c6d9.toInt(), 0xFF8ba7ff.toInt())
    internal val wallColors = intArrayOf(0xFF7eb8cc.toInt(), 0xFF8ec5d6.toInt(), 0xFF9ed0df.toInt(), 0xFFaedae7.toInt())

    private val sentences = arrayOf(
        "Pretext turns motion into language and lets every measured word swing into place while you break the sentence apart.",
        "The paragraph bends around the glowing ball tightens around the paddle and keeps recomposing itself between every collision.",
        "Shatter enough words and the remaining copy grows bolder wraps again and surges into a new block of living text."
    )

    private var gameThread: Thread? = null
    @Volatile private var running = false
    private var touchX = -1f

    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(h: SurfaceHolder) {
        computeLayout()
        buildWall()
        startLevel(1)
        running = true
        gameThread = Thread(this).also { it.start() }
    }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) { computeLayout() }
    override fun surfaceDestroyed(h: SurfaceHolder) { running = false; gameThread?.join() }

    private fun computeLayout() {
        screenW = width.toFloat().let { if (it > 0f) it else resources.displayMetrics.widthPixels.toFloat() }
        screenH = height.toFloat().let { if (it > 0f) it else resources.displayMetrics.heightPixels.toFloat() }

        // No inset handling — Compose toolbar already accounts for notch/nav bar
        // This SurfaceView gets the remaining space below the toolbar
        val margin = 8f * dp
        val fieldTop = margin
        val fieldBottom = screenH - 28f * dp
        fieldRect = RectF(margin, fieldTop, screenW - margin, fieldBottom)
        footerRect = RectF(margin, fieldBottom + 2f * dp, screenW - margin, screenH - 2f * dp)
        hudRect = RectF(margin, 0f, screenW - margin, fieldTop) // minimal, HUD is in Compose

        val brickTopMargin = 48f * dp
        val brickH = minOf(200f * dp, fieldRect.height() * 0.35f)
        brickArea = RectF(fieldRect.left + 6f * dp, fieldRect.top + brickTopMargin, fieldRect.right - 6f * dp, fieldRect.top + brickTopMargin + brickH)
    }

    // Touch is in screen pixels — no conversion needed

    // ── Text wall ───────────────────────────────────────────────────

    private fun buildWall() {
        val wallWords = listOf(
            "pretext layout measure cursor segment wrap glyph inline reflow stream bidi kern space split signal static dynamic vector module bounce trail track render flow",
            "text snakes between every obstacle and keeps every word alive while the field recomposes around the moving ball and the waiting paddle",
            "small copy fills the arena from border to border and the larger block labels stay readable as targets floating above the paragraph wall"
        )
        val bigText = wallWords.joinToString(" ").let { base -> (0 until 20).joinToString(" ") { base } }
        wallPrepared = Pretext.prepareWithSegments(bigText, wallMeasurer)
    }

    // ── Level setup ─────────────────────────────────────────────────

    private fun startLevel(lvl: Int) {
        level = lvl
        state = GState.SERVE
        bricks.clear(); particles.clear(); powerUps.clear(); wakeHoles.clear()
        guardCharges = 0; slowTimer = 0f; paddle.widenTimer = 0f; clearedTimer = 0f

        val sentence = sentences[(lvl - 1) % sentences.size]
        val words = sentence.split(" ").filter { it.isNotEmpty() }

        for ((i, word) in words.withIndex()) {
            val tw = brickPaint.measureText(word)
            val bw = tw + 12f * dp
            val bh = 34f * dp
            bricks.add(Brick(
                x = brickArea.left, y = brickArea.top,
                xTarget = brickArea.left, yTarget = brickArea.top,
                width = bw, height = bh, word = word,
                color = brickColors[i % brickColors.size],
                value = maxOf(20, word.replace(Regex("[^a-zA-Z0-9]"), "").length * 12)
            ))
        }
        reflowBricks(snap = true)

        // Paddle
        val pw = paddlePaint.measureText(paddleNormal)
        paddle = Paddle(fieldRect.centerX(), fieldRect.bottom - 50f * dp, pw / 2f, 22f * dp)

        // Ball
        ball = Ball(paddle.x, paddle.y - 28f * dp, 0f, 0f, getBaseSpeed(), 10f * dp)
    }

    private fun getBaseSpeed() = (330f + (level - 1) * 26f) * dp
    private fun getCurrentSpeed() = if (slowTimer > 0f) getBaseSpeed() * 0.74f else getBaseSpeed()

    private val brickRowH = 34f  // will be multiplied by dp
    private val brickPadH = 6f   // vertical gap between rows, multiplied by dp
    private val brickGapX = 4f   // horizontal gap between bricks, multiplied by dp

    private fun reflowBricks(snap: Boolean = false) {
        val alive = bricks.filter { it.alive }
        if (alive.isEmpty()) return

        // Simple word-flow layout matching iOS: measure each brick and flow left-to-right
        val areaW = brickArea.width()
        var cx = 0f
        var row = 0

        for (brick in alive) {
            val tw = brickPaint.measureText(brick.word)
            val bw = tw + 12f * dp
            val bh = brickRowH * dp

            if (cx + bw > areaW && cx > 0f) {
                row++
                cx = 0f
            }

            brick.width = bw
            brick.height = bh
            brick.xTarget = brickArea.left + cx
            brick.yTarget = brickArea.top + row * (brickRowH + brickPadH) * dp
            if (snap) { brick.x = brick.xTarget; brick.y = brick.yTarget }
            cx += bw + brickGapX * dp
        }
    }

    private fun launchBall() {
        if (state == GState.OVER) { score = 0; lives = 3; startLevel(1); return }
        if (state != GState.SERVE) return
        state = GState.PLAYING
        val spd = getCurrentSpeed()
        val angle = -PI.toFloat() / 2f + (Random.nextFloat() - 0.5f) * 0.6f
        ball.vx = cos(angle) * spd
        ball.vy = sin(angle) * spd
        ball.speed = spd
    }

    // ── Game loop ───────────────────────────────────────────────────

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            update(dt)
            render()
        }
    }

    private fun update(dt: Float) {
        shakeAmount = maxOf(0f, shakeAmount - dt * 16f)

        // Animate bricks toward targets
        for (b in bricks) {
            b.x += (b.xTarget - b.x) * minOf(1f, dt * 10f)
            b.y += (b.yTarget - b.y) * minOf(1f, dt * 10f)
        }

        when (state) {
            GState.SERVE -> { ball.x = paddle.x; ball.y = paddle.y - 28f * dp }
            GState.PLAYING -> { updatePlaying(dt) }
            GState.CLEARED -> { clearedTimer -= dt; updateParticles(dt); if (clearedTimer <= 0f) startLevel(level + 1) }
            GState.OVER -> { updateParticles(dt) }
        }
    }

    private fun updatePlaying(dt: Float) {
        // Timers
        if (paddle.widenTimer > 0f) { paddle.widenTimer -= dt; if (paddle.widenTimer <= 0f) paddle.halfW = paddlePaint.measureText(paddleNormal) / 2f }
        if (slowTimer > 0f) {
            slowTimer -= dt
            if (slowTimer <= 0f) { rescaleSpeed(getBaseSpeed()) }
        }

        // Move ball
        ball.x += ball.vx * dt; ball.y += ball.vy * dt

        // Wake holes -- temporary circular exclusion zones the ball leaves behind
        val wakeDist = sqrt((ball.x - lastWakeX).pow(2) + (ball.y - lastWakeY).pow(2))
        if (wakeDist > 16f) {
            wakeHoles.add(WakeHole(ball.x, ball.y, 0f))
            lastWakeX = ball.x; lastWakeY = ball.y
        }
        wakeHoles.forEach { it.life += dt }
        wakeHoles.removeAll { it.life >= it.maxLife }

        val fl = fieldRect.left + 10f; val fr = fieldRect.right - 10f; val ft = fieldRect.top + 10f; val fb = fieldRect.bottom

        // Walls
        if (ball.x - ball.radius < fl) { ball.x = fl + ball.radius; ball.vx = abs(ball.vx) }
        if (ball.x + ball.radius > fr) { ball.x = fr - ball.radius; ball.vx = -abs(ball.vx) }
        if (ball.y - ball.radius < ft) { ball.y = ft + ball.radius; ball.vy = abs(ball.vy) }

        // Bottom
        if (ball.y + ball.radius > fb) {
            if (guardCharges > 0) { guardCharges--; ball.y = fb - ball.radius; ball.vy = -abs(ball.vy); shakeAmount = 1.8f }
            else { loseLife(); return }
        }

        // Paddle collision
        val pl = paddle.x - paddle.halfW - 2f; val pr = paddle.x + paddle.halfW + 2f
        val pt = paddle.y; val pb = paddle.y + paddle.height
        if (ball.vy > 0 && ball.y + ball.radius >= pt && ball.y - ball.radius <= pb && ball.x >= pl && ball.x <= pr) {
            ball.y = pt - ball.radius
            val hitNorm = ((ball.x - paddle.x) / paddle.halfW).coerceIn(-1f, 1f)
            val adj = if (abs(hitNorm) < 0.18f) (if (ball.vx >= 0) 1 else -1) * 0.18f else hitNorm
            ball.vx = adj * ball.speed * 0.82f
            ball.vy = -sqrt(maxOf(0f, ball.speed * ball.speed - ball.vx * ball.vx))
            shakeAmount = 1f
        }

        // Brick collision
        for (brick in bricks) {
            if (!brick.alive) continue
            val cx = ball.x.coerceIn(brick.x, brick.x + brick.width)
            val cy = ball.y.coerceIn(brick.y, brick.y + brick.height)
            val dx = ball.x - cx; val dy = ball.y - cy
            if (dx * dx + dy * dy <= ball.radius * ball.radius) {
                brick.alive = false
                score += brick.value
                bestScore = maxOf(bestScore, score)
                shakeAmount = 2.2f
                spawnBurstParticles(brick)
                maybeSpawnPowerUp(brick)
                // Reflect
                val bcx = brick.x + brick.width / 2f; val bcy = brick.y + brick.height / 2f
                val rdx = ball.x - bcx; val rdy = ball.y - bcy
                if (abs(rdx / brick.width) > abs(rdy / brick.height)) ball.vx = if (rdx > 0) abs(ball.vx) else -abs(ball.vx)
                else ball.vy = if (rdy > 0) abs(ball.vy) else -abs(ball.vy)
                reflowBricks()
                if (bricks.none { it.alive }) { state = GState.CLEARED; clearedTimer = 2f }
                break
            }
        }

        // Power-ups
        val puIter = powerUps.iterator()
        while (puIter.hasNext()) {
            val pu = puIter.next(); pu.y += pu.vy * dt
            if (pu.y > fb + 20f) { puIter.remove(); continue }
            if (pu.y >= paddle.y && pu.x >= paddle.x - paddle.halfW && pu.x <= paddle.x + paddle.halfW) {
                applyPowerUp(pu.kind); puIter.remove()
            }
        }
        updateParticles(dt)
    }

    private fun rescaleSpeed(newSpeed: Float) {
        val len = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
        if (len > 0f) { ball.vx = ball.vx / len * newSpeed; ball.vy = ball.vy / len * newSpeed }
        ball.speed = newSpeed
    }

    private fun loseLife() {
        lives--; shakeAmount = 3.2f
        guardCharges = 0; slowTimer = 0f; paddle.widenTimer = 0f
        paddle.halfW = paddlePaint.measureText(paddleNormal) / 2f
        if (lives <= 0) { state = GState.OVER; bestScore = maxOf(bestScore, score) }
        else { state = GState.SERVE; ball.vx = 0f; ball.vy = 0f }
    }

    private fun spawnBurstParticles(b: Brick) {
        for (ch in b.word) {
            particles.add(Particle(
                b.x + Random.nextFloat() * b.width, b.y + b.height / 2f,
                (Random.nextFloat() - 0.5f) * 300f, (Random.nextFloat() - 0.8f) * 250f,
                0.8f + Random.nextFloat() * 0.5f, 1.3f, ch.toString(), b.color
            ))
        }
    }

    private fun maybeSpawnPowerUp(b: Brick) {
        if (Random.nextFloat() > 0.34f) return
        val kind = PUKind.entries[Random.nextInt(PUKind.entries.size)]
        val (label, color) = when (kind) {
            PUKind.WIDEN -> "WIDEN" to 0xFF95edff.toInt()
            PUKind.GUARD -> "GUARD" to 0xFFa4f094.toInt()
            PUKind.LIFE -> "+LIFE" to 0xFFff9db8.toInt()
            PUKind.SLOW -> "SLOW" to 0xFFffd577.toInt()
        }
        powerUps.add(PowerUp(b.x + b.width / 2f, b.y + b.height, 146f * dp, kind, label, color))
    }

    private fun applyPowerUp(kind: PUKind) {
        score += 35; bestScore = maxOf(bestScore, score); shakeAmount = 1.3f
        when (kind) {
            PUKind.WIDEN -> { paddle.widenTimer = 12f; paddle.halfW = paddlePaint.measureText(paddleWide) / 2f }
            PUKind.GUARD -> guardCharges++
            PUKind.LIFE -> lives++
            PUKind.SLOW -> { slowTimer = 10f; rescaleSpeed(getBaseSpeed() * 0.74f) }
        }
    }

    private fun updateParticles(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) { val p = iter.next(); p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 400f * dt; p.life -= dt; if (p.life <= 0f) iter.remove() }
    }

    // ── Rendering ───────────────────────────────────────────────────

    private fun render() {
        val canvas: Canvas
        try { canvas = holder.lockCanvas() ?: return } catch (_: Exception) { return }
        try {
            canvas.drawColor(Color.BLACK)
            canvas.save()

            // Shake
            if (shakeAmount > 0f) {
                canvas.translate((Random.nextFloat() - 0.5f) * shakeAmount * 4f * dp, (Random.nextFloat() - 0.5f) * shakeAmount * 4f * dp)
            }

            drawField(canvas)
            drawTextWall(canvas)
            drawBricks(canvas)
            drawPowerUps(canvas)
            drawParticles(canvas)
            drawGuardLine(canvas)
            drawPaddle(canvas)
            drawBall(canvas)
            if (!skipHud) { drawHud(canvas); drawFooter(canvas) }
            if (state != GState.PLAYING) drawOverlay(canvas)

            canvas.restore()
        } finally { holder.unlockCanvasAndPost(canvas) }
    }

    // ── Touch ───────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = x
                if (state == GState.SERVE || state == GState.OVER) launchBall()
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchX >= 0f) {
                    val dx = x - touchX; touchX = x
                    paddle.x = (paddle.x + dx).coerceIn(fieldRect.left + paddle.halfW, fieldRect.right - paddle.halfW)
                    if (state == GState.SERVE) { ball.x = paddle.x; ball.y = paddle.y - 28f * dp }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchX = -1f
        }
        return true
    }
}
