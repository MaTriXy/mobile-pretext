package io.github.matrixy.pretext.sample.breaker

data class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, var speed: Float, val radius: Float)
data class Paddle(var x: Float, var y: Float, var halfW: Float, val height: Float, var widenTimer: Float = 0f)
data class Brick(var x: Float, var y: Float, var xTarget: Float, var yTarget: Float, var width: Float, var height: Float, val word: String, val color: Int, var alive: Boolean = true, var value: Int = 10)
data class PowerUp(var x: Float, var y: Float, var vy: Float, val kind: PUKind, val label: String, val color: Int)
data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val ch: String, val color: Int)
data class WakeHole(var x: Float, var y: Float, var life: Float, val maxLife: Float = 0.42f, val radius: Float = 30f)
enum class PUKind { WIDEN, GUARD, LIFE, SLOW }
enum class GState { SERVE, PLAYING, OVER, CLEARED }
