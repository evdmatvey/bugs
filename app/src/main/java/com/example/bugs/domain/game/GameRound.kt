package com.example.bugs.domain.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class GameSettings(
    val speed: Float = 1f,
    val maxBugs: Int = 10,
    val bonusInterval: Int = 15,
    val duration: Int = 60
) {
    init {
        require(speed in listOf(.5f, 1f, 1.5f, 2f))
        require(maxBugs in listOf(5, 10, 15, 20))
        require(bonusInterval in listOf(5, 10, 15, 30))
        require(duration in listOf(30, 60, 90, 120))
    }
}

enum class BugType(
    val size: Float,
    val speed: Float,
    val points: Int,
    val visual: String
) {
    NORMAL(100f, 100f, 10, "normal"),
    FAST(80f, 180f, 20, "fast"),
    RARE(110f, 120f, 50, "rare")
}

data class Bug(
    val id: Long,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val size: Float,
    val type: BugType,
    val points: Int,
    val visualResource: String,
    val expiresAt: Double? = null
) {
    fun contains(x: Float, y: Float): Boolean {
        val angle = kotlin.math.atan2(velocityY, velocityX) + Math.PI.toFloat() / 2
        val dx = x - this.x
        val dy = y - this.y

        val localX = dx * cos(angle) + dy * sin(angle)
        val localY = -dx * sin(angle) + dy * cos(angle)

        return (localX / (size * .36f)).let { it * it } +
            (localY / (size * .5f)).let { it * it } <= 1f
    }
}

class GameRound(
    val settings: GameSettings,
    private val random: Random = Random.Default
) {

    companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 1400f
    }

    var bugs: List<Bug> = emptyList()
        private set

    var elapsed = 0.0
        private set

    var score = 0
        private set

    var hits = 0
        private set

    var misses = 0
        private set

    val finished get() = elapsed >= settings.duration

    val remaining get() = (settings.duration - elapsed).coerceAtLeast(0.0)

    val accuracy get() = if (hits + misses == 0) 0.0 else hits * 100.0 / (hits + misses)

    private var nextId = 0L
    private var nextBonus = settings.bonusInterval.toDouble()
    private val replacements = mutableListOf<Double>()

    init {
        repeat(settings.maxBugs) { spawnRegular() }
    }

    private fun spawnRegular() {
        spawn(if (random.nextInt(4) == 0) BugType.FAST else BugType.NORMAL)
    }

    private fun spawn(type: BugType) {
        val angle = random.nextFloat() * Math.PI.toFloat() * 2
        val radius = type.size * .8f

        bugs = bugs + Bug(
            ++nextId,
            radius + random.nextFloat() * (WIDTH - radius * 2),
            radius + random.nextFloat() * (HEIGHT - radius * 2),
            cos(angle) * type.speed * settings.speed,
            sin(angle) * type.speed * settings.speed,
            type.size,
            type,
            type.points,
            type.visual,
            if (type == BugType.RARE) elapsed + 5 else null
        )
    }

    fun advance(seconds: Double) {
        require(seconds.isFinite() && seconds >= 0)

        var left = seconds.coerceAtMost(remaining)

        while (left > 0 && !finished) {
            val step = left.coerceAtMost(.05)
            elapsed = (elapsed + step).coerceAtMost(settings.duration.toDouble())
            left = (left - step).coerceAtLeast(0.0)

            bugs = bugs.map { bug ->
                val radius = bug.size * .8f
                val x = bug.x + bug.velocityX * step.toFloat()
                val y = bug.y + bug.velocityY * step.toFloat()

                bug.copy(
                    x = x.coerceIn(radius, WIDTH - radius),
                    y = y.coerceIn(radius, HEIGHT - radius),
                    velocityX = if (x < radius) {
                        abs(bug.velocityX)
                    } else if (x > WIDTH - radius) {
                        -abs(bug.velocityX)
                    } else {
                        bug.velocityX
                    },
                    velocityY = if (y < radius) {
                        abs(bug.velocityY)
                    } else if (y > HEIGHT - radius) {
                        -abs(bug.velocityY)
                    } else {
                        bug.velocityY
                    }
                )
            }

            if (finished) break

            val expired = bugs.count { it.expiresAt?.let { end -> end <= elapsed } == true }
            bugs = bugs.filter { it.expiresAt?.let { end -> end <= elapsed } != true }
            repeat(expired) { replacements.add(elapsed) }

            if (elapsed >= nextBonus) {
                if (bugs.size >= settings.maxBugs) {
                    val replaced = bugs.firstOrNull { it.type != BugType.RARE }
                    if (replaced != null) bugs = bugs.filterNot { it.id == replaced.id }
                }

                if (bugs.size < settings.maxBugs) spawn(BugType.RARE)
                nextBonus += settings.bonusInterval
            }

            val due = replacements.count { it <= elapsed }
            replacements.removeAll { it <= elapsed }
            repeat(due) {
                if (bugs.size < settings.maxBugs) spawnRegular()
            }
        }
    }

    /** null means the tap was outside the field or the round has ended. */
    fun tap(x: Float, y: Float): Int? {
        if (
            finished ||
            !x.isFinite() ||
            !y.isFinite() ||
            x !in 0f..WIDTH ||
            y !in 0f..HEIGHT
        ) return null

        val bug = bugs.lastOrNull { it.contains(x, y) }
        val points = if (bug == null) {
            misses++
            -5
        } else {
            hits++
            bugs = bugs.filterNot { it.id == bug.id }
            replacements.add(elapsed + .6)
            bug.points
        }

        score += points

        return points
    }
}
