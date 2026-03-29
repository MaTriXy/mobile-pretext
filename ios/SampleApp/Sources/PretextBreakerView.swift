import SwiftUI
import Pretext

// MARK: - Game Types

enum GameMode {
    case serve, playing, gameOver, waveCleared
}

enum PowerUpKind: CaseIterable {
    case widen, guard_, extraLife, slow

    var label: String {
        switch self {
        case .widen: return "WIDEN"
        case .guard_: return "GUARD"
        case .extraLife: return "+LIFE"
        case .slow: return "SLOW"
        }
    }

    var color: Color {
        switch self {
        case .widen: return Color(red: 0.3, green: 0.9, blue: 0.95)
        case .guard_: return Color(red: 0.4, green: 0.9, blue: 0.4)
        case .extraLife: return Color(red: 1.0, green: 0.5, blue: 0.7)
        case .slow: return Color(red: 1.0, green: 0.9, blue: 0.3)
        }
    }
}

struct Ball {
    var x: Double
    var y: Double
    var vx: Double
    var vy: Double
    var radius: Double = 8
}

struct Paddle {
    var x: Double
    var y: Double
    var width: Double = 160
    var height: Double = 22
    var expanded: Bool = false
    var expandTimer: Double = 0

    var normalWidth: Double { 160 }
    var expandedWidth: Double { 210 }
    var currentWidth: Double { expanded ? expandedWidth : normalWidth }
    var displayString: String { expanded ? "\u{27E6}==============\u{27E7}" : "\u{27E6}==========\u{27E7}" }
}

struct Brick: Identifiable {
    let id = UUID()
    let word: String
    var x: Double
    var y: Double
    var width: Double
    var height: Double
    var alive: Bool = true
    var colorIndex: Int
    var targetX: Double
    var targetY: Double

    static let colors: [Color] = [
        Color(red: 1.0, green: 0.612, blue: 0.357),   // #ff9c5b
        Color(red: 0.941, green: 0.765, blue: 0.373),  // #f0c35f
        Color(red: 0.518, green: 0.851, blue: 0.424),  // #84d96c
        Color(red: 0.333, green: 0.776, blue: 0.851),  // #55c6d9
        Color(red: 0.545, green: 0.655, blue: 1.0),    // #8ba7ff
    ]
}

struct PowerUp: Identifiable {
    let id = UUID()
    let kind: PowerUpKind
    var x: Double
    var y: Double
    var vy: Double = 90
}

struct Particle: Identifiable {
    let id = UUID()
    var x: Double
    var y: Double
    var vx: Double
    var vy: Double
    var life: Double
    var maxLife: Double
    var char: String
    var color: Color
}

// MARK: - Game Engine

@MainActor
class BreakerGame: ObservableObject {
    // State
    var mode: GameMode = .serve
    var score: Int = 0
    var lives: Int = 3
    var level: Int = 1

    var ball = Ball(x: 0, y: 0, vx: 0, vy: 0)
    var paddle = Paddle(x: 0, y: 0)
    var bricks: [Brick] = []
    var powerUps: [PowerUp] = []
    var particles: [Particle] = []
    var hasGuard: Bool = false
    var slowTimer: Double = 0
    var guardFlashY: Double = 0

    var lastDate: Date?
    var playAreaRect: CGRect = .zero
    var canvasSize: CGSize = .zero

    // Fonts
    let brickFont = FontSpec(name: "Menlo-Bold", size: 20)
    let paddleFont = FontSpec(name: "Menlo", size: 18)

    // Wall text
    private let wallFont = FontSpec(name: "Menlo", size: 12)
    private var wallPrepared: PreparedTextWithSegments?
    private var wakeHoles: [(x: Double, y: Double, life: Double, maxLife: Double, radius: Double)] = []
    private var lastWakeX: Double = 0
    private var lastWakeY: Double = 0

    private let wallColors: [Color] = [
        Color(red: 0.49, green: 0.72, blue: 0.8),
        Color(red: 0.56, green: 0.77, blue: 0.84),
        Color(red: 0.62, green: 0.82, blue: 0.87),
        Color(red: 0.68, green: 0.85, blue: 0.91),
    ]

    func buildWall() {
        let wallWords = [
            "pretext layout measure cursor segment wrap glyph inline reflow stream bidi kern space split signal static dynamic vector module bounce trail track render flow",
            "text snakes between every obstacle and keeps every word alive while the field recomposes around the moving ball and the waiting paddle",
            "small copy fills the arena from border to border and the larger block labels stay readable as targets floating above the paragraph wall"
        ]
        let bigText = (0..<20).map { _ in wallWords.joined(separator: " ") }.joined(separator: " ")
        wallPrepared = Pretext.prepareWithSegments(bigText, font: wallFont)
    }

    // Level data
    static let levelSentences: [String] = [
        "Pretext turns motion into language and lets every measured word swing into place while you break the sentence apart.",
        "The paragraph bends around the glowing ball tightens around the paddle and keeps recomposing itself between every collision.",
        "Shatter enough words and the remaining copy grows bolder wraps again and surges into a new block of living text.",
    ]

    // Layout constants
    let brickRowHeight: Double = 34
    let brickPadding: Double = 6
    let brickTopMargin: Double = 60
    let hudHeight: Double = 48
    let playAreaInset: Double = 16

    // MARK: - Pretext Integration

    func measureWord(_ word: String) -> Double {
        let rich = Pretext.prepareWithSegments(word, font: brickFont)
        let lines = Pretext.layoutWithLines(rich, maxWidth: 9999, lineHeight: brickRowHeight)
        return lines.lines.first?.width ?? 0
    }

    func relayoutBricks(areaWidth: Double) {
        // Collect alive brick indices and their words
        let aliveIndices = bricks.indices.filter { bricks[$0].alive }
        let aliveWords = aliveIndices.map { bricks[$0].word }
        guard !aliveWords.isEmpty else { return }

        let sentence = aliveWords.joined(separator: " ")
        let prepared = Pretext.prepareWithSegments(sentence, font: brickFont)
        let usableWidth = areaWidth - brickPadding * 2
        let result = Pretext.layoutWithLines(prepared, maxWidth: usableWidth, lineHeight: brickRowHeight)

        // Map line texts back to alive bricks
        var wordIdx = 0
        let areaLeft = playAreaRect.minX + brickPadding
        for (lineIdx, line) in result.lines.enumerated() {
            let words = splitLineIntoOriginalWords(lineText: line.text, remainingWords: Array(aliveWords[wordIdx...]))
            var xOffset: Double = 0
            for word in words {
                guard wordIdx < aliveIndices.count else { break }
                let bi = aliveIndices[wordIdx]

                let wordWidth = measureWord(word)
                let targetX = areaLeft + xOffset
                let targetY = playAreaRect.minY + brickTopMargin + Double(lineIdx) * (brickRowHeight + brickPadding)
                bricks[bi].targetX = targetX
                bricks[bi].targetY = targetY
                bricks[bi].width = wordWidth + brickPadding * 2
                bricks[bi].height = brickRowHeight
                xOffset += wordWidth + brickPadding * 2 + 4
                wordIdx += 1
            }
        }
    }

    private func splitLineIntoOriginalWords(lineText: String, remainingWords: [String]) -> [String] {
        var result: [String] = []
        var remaining = lineText.trimmingCharacters(in: .whitespaces)
        for word in remainingWords {
            if remaining.hasPrefix(word) {
                result.append(word)
                remaining = String(remaining.dropFirst(word.count)).trimmingCharacters(in: .init(charactersIn: " "))
            } else {
                break
            }
        }
        return result
    }

    // MARK: - Setup

    func setupLevel() {
        let sentenceIdx = (level - 1) % BreakerGame.levelSentences.count
        let sentence = BreakerGame.levelSentences[sentenceIdx]
        let words = sentence.split(separator: " ").map(String.init)
        let areaWidth = playAreaRect.width - brickPadding * 2
        let areaLeft = playAreaRect.minX + brickPadding

        bricks = []
        var xOffset: Double = 0
        var row: Int = 0

        for (i, word) in words.enumerated() {
            let wordWidth = measureWord(word)
            let brickWidth = wordWidth + brickPadding * 2

            if xOffset + brickWidth > areaWidth && xOffset > 0 {
                row += 1
                xOffset = 0
            }

            let bx = areaLeft + xOffset
            let by = playAreaRect.minY + brickTopMargin + Double(row) * (brickRowHeight + brickPadding)

            bricks.append(Brick(
                word: word,
                x: bx, y: by,
                width: brickWidth, height: brickRowHeight,
                colorIndex: i % Brick.colors.count,
                targetX: bx, targetY: by
            ))

            xOffset += brickWidth + 4
        }

        powerUps = []
        particles = []
        hasGuard = false
        slowTimer = 0
        paddle.expanded = false
        paddle.expandTimer = 0

        if wallPrepared == nil { buildWall() }

        resetBallOnPaddle()
        mode = .serve
    }

    func resetBallOnPaddle() {
        ball.x = paddle.x + paddle.currentWidth / 2
        ball.y = paddle.y - ball.radius - 2
        ball.vx = 0
        ball.vy = 0
    }

    func launchBall() {
        guard mode == .serve else { return }
        let speed = 330.0 + Double(level - 1) * 26.0
        let angle = Double.random(in: -0.4...0.4)
        ball.vx = sin(angle) * speed
        ball.vy = -cos(angle) * speed
        mode = .playing
    }

    // MARK: - Update

    var safeAreaTop: Double = 0
    var safeAreaBottom: Double = 0

    func updateAndDraw(context: inout GraphicsContext, size: CGSize, date: Date) {
        let newCanvas = canvasSize != size
        canvasSize = size

        // Compute play area — respect safe area (notch)
        let inset = playAreaInset
        let topOffset = max(safeAreaTop, 0)
        let bottomOffset = max(safeAreaBottom, 0)
        let areaX = inset
        let areaY = hudHeight + inset + topOffset
        let areaW = size.width - inset * 2
        let areaH = size.height - hudHeight - inset * 2 - topOffset - bottomOffset
        playAreaRect = CGRect(x: areaX, y: areaY, width: areaW, height: areaH)

        if newCanvas || bricks.isEmpty {
            paddle.x = areaX + areaW / 2 - paddle.currentWidth / 2
            paddle.y = areaY + areaH - 50
            setupLevel()
        }

        // Delta time
        let dt: Double
        if let last = lastDate {
            dt = min(date.timeIntervalSince(last), 1.0 / 30.0)
        } else {
            dt = 1.0 / 60.0
        }
        lastDate = date

        if mode == .playing {
            updatePhysics(dt: dt)
        }
        if mode == .serve {
            resetBallOnPaddle()
        }

        // Animate bricks toward targets
        for i in bricks.indices where bricks[i].alive {
            bricks[i].x += (bricks[i].targetX - bricks[i].x) * min(1.0, dt * 8)
            bricks[i].y += (bricks[i].targetY - bricks[i].y) * min(1.0, dt * 8)
        }

        // Timers
        if paddle.expanded {
            paddle.expandTimer -= dt
            if paddle.expandTimer <= 0 {
                paddle.expanded = false
                paddle.expandTimer = 0
            }
        }
        if slowTimer > 0 {
            slowTimer -= dt
            if slowTimer <= 0 { slowTimer = 0 }
        }

        // Update particles
        particles = particles.compactMap { var p = $0; p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt; return p.life > 0 ? p : nil }

        // Update power-ups
        updatePowerUps(dt: dt)

        // Draw everything
        drawBackground(context: &context, size: size)
        drawPlayArea(context: &context)
        drawTextWall(context: &context, fieldRect: playAreaRect)
        drawBricks(context: &context)
        drawParticles(context: &context)
        drawPowerUps(context: &context)
        drawGuardNet(context: &context)
        drawPaddle(context: &context)
        drawBall(context: &context)
        drawOverlay(context: &context, size: size)
    }

    func updatePhysics(dt: Double) {
        let speedMul = slowTimer > 0 ? 0.55 : 1.0

        ball.x += ball.vx * dt * speedMul
        ball.y += ball.vy * dt * speedMul

        // Wake holes trail behind ball
        let wakeDist = sqrt(pow(ball.x - lastWakeX, 2) + pow(ball.y - lastWakeY, 2))
        if wakeDist > 16 {
            wakeHoles.append((x: ball.x, y: ball.y, life: 0, maxLife: 0.42, radius: 30))
            lastWakeX = ball.x; lastWakeY = ball.y
        }
        wakeHoles = wakeHoles.map { (x: $0.x, y: $0.y, life: $0.life + dt, maxLife: $0.maxLife, radius: $0.radius) }
        wakeHoles.removeAll { $0.life >= $0.maxLife }

        let area = playAreaRect

        // Wall collisions
        if ball.x - ball.radius < area.minX {
            ball.x = area.minX + ball.radius
            ball.vx = abs(ball.vx)
        }
        if ball.x + ball.radius > area.maxX {
            ball.x = area.maxX - ball.radius
            ball.vx = -abs(ball.vx)
        }
        if ball.y - ball.radius < area.minY {
            ball.y = area.minY + ball.radius
            ball.vy = abs(ball.vy)
        }

        // Bottom - lose life or guard
        if ball.y + ball.radius > paddle.y + paddle.height + 30 {
            if hasGuard {
                hasGuard = false
                ball.vy = -abs(ball.vy)
                ball.y = paddle.y - ball.radius - 10
                spawnParticles(x: ball.x, y: paddle.y + paddle.height + 20, count: 8, color: PowerUpKind.guard_.color, char: "#")
            } else {
                lives -= 1
                if lives <= 0 {
                    mode = .gameOver
                } else {
                    mode = .serve
                    resetBallOnPaddle()
                }
                return
            }
        }

        // Paddle collision
        let px = paddle.x
        let pw = paddle.currentWidth
        let py = paddle.y
        let ph = paddle.height
        if ball.vy > 0 &&
            ball.y + ball.radius >= py &&
            ball.y + ball.radius <= py + ph + 10 &&
            ball.x >= px - ball.radius &&
            ball.x <= px + pw + ball.radius {

            let hitPos = (ball.x - px) / pw // 0..1
            let speed = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
            let angle = (hitPos - 0.5) * 2.4 // -1.2 .. 1.2 radians spread
            ball.vx = sin(angle) * speed
            ball.vy = -abs(cos(angle) * speed)
            ball.y = py - ball.radius - 1
        }

        // Brick collisions
        for i in bricks.indices where bricks[i].alive {
            let b = bricks[i]
            if ball.x + ball.radius > b.x &&
                ball.x - ball.radius < b.x + b.width &&
                ball.y + ball.radius > b.y &&
                ball.y - ball.radius < b.y + b.height {

                bricks[i].alive = false
                score += 10 * level

                // Determine bounce direction
                let overlapLeft = (ball.x + ball.radius) - b.x
                let overlapRight = (b.x + b.width) - (ball.x - ball.radius)
                let overlapTop = (ball.y + ball.radius) - b.y
                let overlapBottom = (b.y + b.height) - (ball.y - ball.radius)
                let minOverlap = min(overlapLeft, overlapRight, overlapTop, overlapBottom)

                if minOverlap == overlapLeft || minOverlap == overlapRight {
                    ball.vx = -ball.vx
                } else {
                    ball.vy = -ball.vy
                }

                // Particles
                spawnParticles(x: b.x + b.width / 2, y: b.y + b.height / 2, count: 6, color: Brick.colors[b.colorIndex], char: String(b.word.prefix(1)))

                // Power-up drop
                if Double.random(in: 0...1) < 0.34 {
                    let kind = PowerUpKind.allCases.randomElement()!
                    powerUps.append(PowerUp(kind: kind, x: b.x + b.width / 2, y: b.y + b.height))
                }

                // Relayout
                relayoutBricks(areaWidth: playAreaRect.width)

                // Check wave clear
                if bricks.allSatisfy({ !$0.alive }) {
                    mode = .waveCleared
                }

                break // one brick per frame
            }
        }
    }

    func updatePowerUps(dt: Double) {
        var consumed: Set<UUID> = []
        for i in powerUps.indices {
            powerUps[i].y += powerUps[i].vy * dt

            // Check paddle catch
            let pu = powerUps[i]
            if pu.y >= paddle.y &&
                pu.y <= paddle.y + paddle.height + 20 &&
                pu.x >= paddle.x &&
                pu.x <= paddle.x + paddle.currentWidth {
                applyPowerUp(pu.kind)
                consumed.insert(pu.id)
                spawnParticles(x: pu.x, y: pu.y, count: 5, color: pu.kind.color, char: "*")
            }

            // Off screen
            if pu.y > playAreaRect.maxY + 20 {
                consumed.insert(pu.id)
            }
        }
        powerUps.removeAll { consumed.contains($0.id) }
    }

    func applyPowerUp(_ kind: PowerUpKind) {
        switch kind {
        case .widen:
            paddle.expanded = true
            paddle.expandTimer = 12
        case .guard_:
            hasGuard = true
        case .extraLife:
            lives += 1
        case .slow:
            slowTimer = 10
        }
    }

    func spawnParticles(x: Double, y: Double, count: Int, color: Color, char: String) {
        for _ in 0..<count {
            let angle = Double.random(in: 0..<Double.pi * 2)
            let speed = Double.random(in: 30...120)
            let life = Double.random(in: 0.4...1.0)
            particles.append(Particle(
                x: x, y: y,
                vx: cos(angle) * speed,
                vy: sin(angle) * speed - 40,
                life: life, maxLife: life,
                char: char, color: color
            ))
        }
    }

    // MARK: - Drawing

    let bgColor = Color(red: 0.01, green: 0.02, blue: 0.05)
    let warmText = Color(red: 0.96, green: 0.95, blue: 0.87)
    let dimText = Color(red: 0.5, green: 0.5, blue: 0.45)

    func drawBackground(context: inout GraphicsContext, size: CGSize) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(bgColor))
    }

    func drawPlayArea(context: inout GraphicsContext) {
        let r = playAreaRect
        let path = Path(roundedRect: r, cornerRadius: 6)
        context.stroke(path, with: .color(warmText.opacity(0.15)), lineWidth: 1)
        context.fill(path, with: .color(Color.white.opacity(0.01)))
    }

    func drawBricks(context: inout GraphicsContext) {
        for brick in bricks where brick.alive {
            let rect = CGRect(x: brick.x, y: brick.y, width: brick.width, height: brick.height)
            let color = Brick.colors[brick.colorIndex]
            let path = Path(roundedRect: rect, cornerRadius: 4)
            context.fill(path, with: .color(color.opacity(0.18)))
            context.stroke(path, with: .color(color.opacity(0.6)), lineWidth: 1.5)

            let text = Text(brick.word)
                .font(.custom("Menlo-Bold", size: 20))
                .foregroundColor(color)
            context.draw(text, at: CGPoint(x: brick.x + brick.width / 2, y: brick.y + brick.height / 2))
        }
    }

    func drawPaddle(context: inout GraphicsContext) {
        // Glow
        let glowRect = CGRect(x: paddle.x - 4, y: paddle.y - 4, width: paddle.currentWidth + 8, height: paddle.height + 8)
        context.fill(Path(roundedRect: glowRect, cornerRadius: 6), with: .color(warmText.opacity(0.06)))

        let text = Text(paddle.displayString)
            .font(.custom("Menlo", size: 18))
            .foregroundColor(warmText)
        context.draw(text, at: CGPoint(x: paddle.x + paddle.currentWidth / 2, y: paddle.y + paddle.height / 2))
    }

    func drawBall(context: inout GraphicsContext) {
        // Glow layers
        let glowChar = Text("\u{25C9}")
            .font(.custom("Menlo", size: 28))
            .foregroundColor(warmText.opacity(0.15))
        context.draw(glowChar, at: CGPoint(x: ball.x, y: ball.y))

        let ballChar = Text("\u{25C9}")
            .font(.custom("Menlo", size: 22))
            .foregroundColor(warmText)
        context.draw(ballChar, at: CGPoint(x: ball.x, y: ball.y))
    }

    func drawParticles(context: inout GraphicsContext) {
        for p in particles {
            let alpha = p.life / p.maxLife
            let text = Text(p.char)
                .font(.custom("Menlo", size: 14))
                .foregroundColor(p.color.opacity(alpha))
            context.draw(text, at: CGPoint(x: p.x, y: p.y))
        }
    }

    func drawPowerUps(context: inout GraphicsContext) {
        for pu in powerUps {
            let text = Text(pu.kind.label)
                .font(.custom("Menlo-Bold", size: 13))
                .foregroundColor(pu.kind.color)
            // Background pill
            let pillW: Double = 50
            let pillH: Double = 20
            let pillRect = CGRect(x: pu.x - pillW / 2, y: pu.y - pillH / 2, width: pillW, height: pillH)
            context.fill(Path(roundedRect: pillRect, cornerRadius: 10), with: .color(pu.kind.color.opacity(0.15)))
            context.stroke(Path(roundedRect: pillRect, cornerRadius: 10), with: .color(pu.kind.color.opacity(0.5)), lineWidth: 1)
            context.draw(text, at: CGPoint(x: pu.x, y: pu.y))
        }
    }

    func drawGuardNet(context: inout GraphicsContext) {
        guard hasGuard else { return }
        let y = paddle.y + paddle.height + 22
        let dashes: [CGFloat] = [6, 4]
        var path = Path()
        path.move(to: CGPoint(x: playAreaRect.minX + 10, y: y))
        path.addLine(to: CGPoint(x: playAreaRect.maxX - 10, y: y))
        context.stroke(path, with: .color(PowerUpKind.guard_.color.opacity(0.6)), style: StrokeStyle(lineWidth: 2, dash: dashes))
    }

    // HUD is now drawn by SwiftUI toolbar — no Canvas HUD needed

    func drawOverlay(context: inout GraphicsContext, size: CGSize) {
        let cx = size.width / 2
        let cy = size.height / 2

        switch mode {
        case .serve:
            let text = Text("TAP TO LAUNCH")
                .font(.custom("Menlo-Bold", size: 20))
                .foregroundColor(warmText.opacity(0.7))
            context.draw(text, at: CGPoint(x: cx, y: cy + 40))

        case .gameOver:
            let bg = Path(CGRect(origin: .zero, size: size))
            context.fill(bg, with: .color(bgColor.opacity(0.8)))

            let title = Text("GAME OVER")
                .font(.custom("Menlo-Bold", size: 36))
                .foregroundColor(Color(red: 1.0, green: 0.4, blue: 0.4))
            context.draw(title, at: CGPoint(x: cx, y: cy - 30))

            let scoreLabel = Text("FINAL SCORE: \(score)")
                .font(.custom("Menlo-Bold", size: 20))
                .foregroundColor(warmText)
            context.draw(scoreLabel, at: CGPoint(x: cx, y: cy + 20))

            let restart = Text("TAP TO RESTART")
                .font(.custom("Menlo", size: 16))
                .foregroundColor(warmText.opacity(0.6))
            context.draw(restart, at: CGPoint(x: cx, y: cy + 60))

        case .waveCleared:
            let bg = Path(CGRect(origin: .zero, size: size))
            context.fill(bg, with: .color(bgColor.opacity(0.7)))

            let title = Text("WAVE CLEARED!")
                .font(.custom("Menlo-Bold", size: 32))
                .foregroundColor(Color(red: 0.518, green: 0.851, blue: 0.424))
            context.draw(title, at: CGPoint(x: cx, y: cy - 20))

            let next = Text("TAP FOR LEVEL \(level + 1)")
                .font(.custom("Menlo", size: 16))
                .foregroundColor(warmText.opacity(0.6))
            context.draw(next, at: CGPoint(x: cx, y: cy + 30))

        case .playing:
            break
        }
    }

    // MARK: - Text Wall

    func drawTextWall(context: inout GraphicsContext, fieldRect: CGRect) {
        guard let prepared = wallPrepared else { return }
        let margin: Double = 14
        let left = fieldRect.minX + margin
        let right = fieldRect.maxX - margin
        let lineH: Double = 16

        var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
        var y = fieldRect.minY + margin
        var rowIndex = 0

        while y + lineH <= fieldRect.maxY - margin {
            var blocked: [(left: Double, right: Double)] = []

            // Bricks block text
            for brick in bricks where brick.alive {
                if y + lineH < brick.y - 3 || y > brick.y + brick.height + 3 { continue }
                blocked.append((brick.x - 8, brick.x + brick.width + 8))
            }

            // Ball blocks text (circle)
            let ballRadius: Double = 38
            let dy = (y + lineH / 2) - ball.y
            if abs(dy) < ballRadius {
                let dx = sqrt(ballRadius * ballRadius - dy * dy)
                blocked.append((ball.x - dx, ball.x + dx))
            }

            // Paddle blocks text
            if y + lineH > paddle.y - 6 && y < paddle.y + paddle.height + 6 {
                blocked.append((paddle.x - 12, paddle.x + paddle.currentWidth + 12))
            }

            // Wake holes
            for hole in wakeHoles {
                let hdy = (y + lineH / 2) - hole.y
                let fadeRadius = hole.radius * (1 - hole.life / hole.maxLife)
                if abs(hdy) < fadeRadius {
                    let hdx = sqrt(fadeRadius * fadeRadius - hdy * hdy)
                    blocked.append((hole.x - hdx, hole.x + hdx))
                }
            }

            let slots = carveSlots(left: left, right: right, blocked: blocked)

            for slot in slots {
                let slotWidth = slot.1 - slot.0
                if slotWidth < 18 { continue }

                if let line = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: slotWidth) {
                    let color = wallColors[rowIndex % wallColors.count]
                    context.draw(
                        Text(line.text).font(.custom("Menlo", size: 12)).foregroundColor(color.opacity(0.52)),
                        at: CGPoint(x: slot.0, y: y),
                        anchor: .topLeading
                    )
                    cursor = line.end
                } else {
                    cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
                }
            }

            y += lineH
            rowIndex += 1
        }
    }

    func carveSlots(left: Double, right: Double, blocked: [(left: Double, right: Double)]) -> [(Double, Double)] {
        if blocked.isEmpty { return [(left, right)] }

        let clamped = blocked.map { (max(left, $0.left), min(right, $0.right)) }
            .filter { $0.1 > $0.0 }
            .sorted { $0.0 < $1.0 }

        var merged: [(Double, Double)] = []
        for interval in clamped {
            if merged.isEmpty || interval.0 > merged.last!.1 {
                merged.append(interval)
            } else {
                merged[merged.count - 1].1 = max(merged.last!.1, interval.1)
            }
        }

        var slots: [(Double, Double)] = []
        var cur = left
        for interval in merged {
            if interval.0 - cur >= 18 { slots.append((cur, interval.0)) }
            cur = max(cur, interval.1)
        }
        if right - cur >= 18 { slots.append((cur, right)) }
        return slots
    }

    // MARK: - Input

    func handleDrag(location: CGPoint) {
        let halfW = paddle.currentWidth / 2
        var newX = location.x - halfW
        newX = max(playAreaRect.minX, min(newX, playAreaRect.maxX - paddle.currentWidth))
        paddle.x = newX

        if mode == .serve {
            resetBallOnPaddle()
        }
    }

    func handleTap() {
        switch mode {
        case .serve:
            launchBall()
        case .gameOver:
            score = 0
            lives = 3
            level = 1
            setupLevel()
        case .waveCleared:
            level += 1
            setupLevel()
        case .playing:
            break
        }
    }
}

// MARK: - SwiftUI View

struct PretextBreakerView: View {
    @StateObject private var game = BreakerGame()
    @State private var totalDragDistance: Double = 0
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            // Toolbar — always below the notch
            HStack {
                Button {
                    dismiss()
                } label: {
                    Text("EXIT")
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundStyle(.white.opacity(0.8))
                }
                .padding(.leading, 4)

                Spacer()

                Text("SCORE: \(game.score)")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color(red: 0.96, green: 0.95, blue: 0.87))

                Spacer()

                Text(String(repeating: "\u{2665}", count: max(game.lives, 0)))
                    .font(.system(size: 16, design: .monospaced))
                    .foregroundStyle(Color(red: 1.0, green: 0.4, blue: 0.4))

                Spacer()

                Text("LEVEL \(game.level)")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color(red: 0.96, green: 0.95, blue: 0.87))
                    .padding(.trailing, 4)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color(red: 0.02, green: 0.04, blue: 0.08))

            // Game canvas — fills the rest
            TimelineView(.animation) { timeline in
                Canvas { context, size in
                    game.safeAreaTop = 0
                    game.safeAreaBottom = 0
                    var ctx = context
                    game.updateAndDraw(context: &ctx, size: size, date: timeline.date)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let dx = abs(value.translation.width)
                        let dy = abs(value.translation.height)
                        totalDragDistance = max(dx, dy)
                        game.handleDrag(location: value.location)
                    }
                    .onEnded { value in
                        if totalDragDistance < 10 {
                            game.handleTap()
                        }
                        totalDragDistance = 0
                    }
            )
        }
        .background(Color(red: 0.02, green: 0.04, blue: 0.08))
        #if os(iOS)
        .persistentSystemOverlays(.hidden)
        #endif
    }
}
