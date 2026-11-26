package com.example.bugs.domain.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

object GameSettingsOptions {
    val SPEEDS = listOf(.5f, 1f, 1.5f, 2f)
    val BUG_COUNTS = listOf(5, 10, 15, 20)
    val BONUS_INTERVALS = listOf(5, 10, 15, 30)
    val DURATIONS = listOf(30, 60, 90, 120)
}

data class GameSettings(
    val speed: Float = 1f,
    val maxBugs: Int = 10,
    val bonusInterval: Int = 15,
    val duration: Int = 60
) : java.io.Serializable {
    init {
        require(speed in GameSettingsOptions.SPEEDS)
        require(maxBugs in GameSettingsOptions.BUG_COUNTS)
        require(bonusInterval in GameSettingsOptions.BONUS_INTERVALS)
        require(duration in GameSettingsOptions.DURATIONS)
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
    RARE(110f, 120f, 50, "rare"),
    GOLD(110f, 120f, 0, "gold")
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

data class TiltBonus(
    val x: Float,
    val y: Float,
    val expiresAt: Double
) {
    companion object {
        const val RADIUS = 90f
    }

    fun contains(x: Float, y: Float): Boolean {
        val dx = x - this.x
        val dy = y - this.y

        return dx * dx + dy * dy <= RADIUS * RADIUS
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

    var tiltBonus: TiltBonus? = null
        private set

    var offer: List<GameAbility> = emptyList()
        private set

    var pendingAbility: GameAbility? = null
        private set

    var bait: Bait? = null
        private set

    var animationTime = 0.0
        private set

    var abilityEffects: List<AbilityEffect> = emptyList()
        private set

    private val offers = AbilityOffers(random)
    private val activeUntil = mutableMapOf<GameAbility, Double>()
    private var nextOffer = 10.0

    fun abilityRemaining(ability: GameAbility): Double =
        if (finished) 0.0 else ((activeUntil[ability] ?: 0.0) - elapsed).coerceAtLeast(0.0)

    fun buyAbility(ability: GameAbility): Boolean {
        if (finished || ability !in offer || score < ability.price) return false

        score -= ability.price
        offer = emptyList()

        when (ability) {
            GameAbility.BAIT, GameAbility.BOMB, GameAbility.CHAIN -> pendingAbility = ability
            else -> {
                activeUntil[ability] = elapsed + ability.duration
                abilityEffects = abilityEffects + AbilityEffect(ability, WIDTH / 2, HEIGHT / 2, elapsed)
            }
        }

        return true
    }

    fun skipOffer(): Boolean {
        if (offer.isEmpty()) return false
        offer = emptyList()

        return true
    }

    val tiltRemaining get() = if (finished) 0.0 else (tiltUntil - elapsed).coerceAtLeast(0.0)

    val finished get() = elapsed >= settings.duration

    val remaining get() = (settings.duration - elapsed).coerceAtLeast(0.0)

    val accuracy get() = if (hits + misses == 0) 0.0 else hits * 100.0 / (hits + misses)

    private var nextId = 0L
    private var nextBonus = settings.bonusInterval.toDouble()
    private var nextTiltBonus = 15.0
    private var nextGold = 20.0
    private var goldPoints: Int? = null
    private var tiltAvailable = false
    private var tiltUntil = 0.0
    private var tiltX = 0f
    private var tiltY = 0f
    private val replacements = mutableListOf<Double>()

    init {
        repeat(settings.maxBugs) { spawnRegular() }
    }

    fun configureGold(points: Int?) {
        require(points == null || points > 0)
        goldPoints = points
    }

    fun configureTilt(available: Boolean) {
        tiltAvailable = available

        if (!available) {
            tiltBonus = null
            tiltUntil = 0.0
            setTilt(0f, 0f)
        }
    }

    fun setTilt(x: Float, y: Float) {
        tiltX = if (x.isFinite()) x.coerceIn(-1f, 1f) else 0f
        tiltY = if (y.isFinite()) y.coerceIn(-1f, 1f) else 0f
    }

    fun tapBonus(x: Float, y: Float): Boolean {
        val bonus = tiltBonus ?: return false
        if (finished || offer.isNotEmpty() || pendingAbility != null || bonus.expiresAt <= elapsed || !bonus.contains(x, y)) return false

        tiltBonus = null
        tiltUntil = elapsed + 5

        return true
    }

    private fun spawnRegular() {
        spawn(if (random.nextInt(4) == 0) BugType.FAST else BugType.NORMAL)
    }

    private fun spawn(type: BugType, points: Int = type.points) {
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
            points,
            type.visual,
            if (type == BugType.RARE || type == BugType.GOLD) elapsed + 5 else null
        )
    }

    private fun spawnBonus(type: BugType, points: Int = type.points) {
        if (bugs.size >= settings.maxBugs) {
            val replaced = bugs.firstOrNull { it.type == BugType.NORMAL || it.type == BugType.FAST }
                ?: if (type == BugType.GOLD) bugs.firstOrNull { it.type == BugType.RARE } else null

            if (replaced != null) bugs = bugs.filterNot { it.id == replaced.id }
        }

        if (bugs.size < settings.maxBugs) spawn(type, points)
    }

    fun advance(seconds: Double) {
        require(seconds.isFinite() && seconds >= 0)

        if (offer.isNotEmpty()) return

        var left = seconds.coerceAtMost(remaining)

        while (left > 0 && !finished) {
            val effectEnd = (activeUntil.values + listOfNotNull(bait?.expiresAt))
                .filter { it > elapsed }.minOrNull() ?: Double.POSITIVE_INFINITY
            val step = minOf(left, .05, nextOffer - elapsed, effectEnd - elapsed)
            val movement = when {
                abilityRemaining(GameAbility.FREEZE) > 0 -> 0f
                abilityRemaining(GameAbility.SLOW) > 0 -> .5f
                else -> 1f
            }
            val tiltStep = (tiltUntil - elapsed).coerceIn(0.0, step).toFloat() * movement
            animationTime += step * movement
            elapsed = (elapsed + step).coerceAtMost(settings.duration.toDouble())
            if (abs(elapsed - nextOffer) < 1e-8) elapsed = nextOffer
            if (abs(elapsed - settings.duration) < 1e-8) elapsed = settings.duration.toDouble()
            left = (left - step).coerceAtLeast(0.0)

            bugs = bugs.map { bug ->
                val radius = bug.size * .8f
                val lure = bait
                val distance = lure?.let { hypot(it.x - bug.x, it.y - bug.y) } ?: 0f
                val speed = hypot(bug.velocityX, bug.velocityY)
                val travel = (speed * step.toFloat() * movement).coerceAtMost(distance)
                val dx = if (lure != null && distance > 0) (lure.x - bug.x) / distance * travel else 0f
                val dy = if (lure != null && distance > 0) (lure.y - bug.y) / distance * travel else 0f
                val x = bug.x + (if (lure == null) bug.velocityX * step.toFloat() * movement else dx) + tiltX * 700 * tiltStep
                val y = bug.y + (if (lure == null) bug.velocityY * step.toFloat() * movement else dy) + tiltY * 700 * tiltStep

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

            if (finished) {
                tiltBonus = null
                pendingAbility = null
                bait = null
                activeUntil.clear()
                abilityEffects = emptyList()
                break
            }

            if (bait?.let { it.expiresAt <= elapsed } == true) bait = null
            abilityEffects = abilityEffects.filter { elapsed - it.born < .45 }

            if (tiltBonus?.let { it.expiresAt <= elapsed } == true) tiltBonus = null

            if (elapsed >= nextTiltBonus) {
                if (tiltAvailable) {
                    val radius = TiltBonus.RADIUS
                    tiltBonus = TiltBonus(
                        radius + random.nextFloat() * (WIDTH - radius * 2),
                        radius + random.nextFloat() * (HEIGHT - radius * 2),
                        elapsed + 5
                    )
                }

                nextTiltBonus += 15
            }

            val expired = bugs.count { it.expiresAt?.let { end -> end <= elapsed } == true }
            bugs = bugs.filter { it.expiresAt?.let { end -> end <= elapsed } != true }
            repeat(expired) { replacements.add(elapsed) }

            if (elapsed >= nextGold) {
                goldPoints?.let { spawnBonus(BugType.GOLD, it) }
                nextGold += 20
            }

            if (elapsed >= nextBonus) {
                spawnBonus(BugType.RARE)
                nextBonus += settings.bonusInterval
            }

            val due = replacements.count { it <= elapsed }
            replacements.removeAll { it <= elapsed }
            repeat(due) {
                if (bugs.size < settings.maxBugs) spawnRegular()
            }

            if (elapsed >= nextOffer) {
                nextOffer += 10
                if (pendingAbility == null) offer = offers.next(score)
                if (offer.isNotEmpty()) break
            }
        }
    }

    fun tap(x: Float, y: Float): AttackResult? {
        if (
            finished || offer.isNotEmpty() ||
            !x.isFinite() || !y.isFinite() ||
            x !in 0f..WIDTH || y !in 0f..HEIGHT
        ) return null

        val ability = pendingAbility
        pendingAbility = null

        if (ability == GameAbility.BAIT) {
            bait = Bait(x, y, elapsed + GameAbility.BAIT.duration)
            return AttackResult(0)
        }

        val selected = bugs.lastOrNull { it.contains(x, y) }
        val targets = when (ability) {
            GameAbility.BOMB -> bugs.filter { hypot(it.x - x, it.y - y) <= 240f }
            GameAbility.CHAIN -> {
                val chain = mutableListOf<Bug>()
                var current = selected

                while (current != null && chain.size < 5) {
                    val origin = current
                    chain.add(origin)
                    current = bugs.filter { it !in chain && hypot(it.x - origin.x, it.y - origin.y) <= 280f }
                        .minWithOrNull(compareBy<Bug> { hypot(it.x - origin.x, it.y - origin.y) }.thenBy { it.id })
                }

                chain
            }
            else -> listOfNotNull(selected)
        }

        if (ability != null) {
            abilityEffects = abilityEffects + AbilityEffect(ability, x, y, elapsed, targets)
        }

        val points = if (targets.isEmpty()) {
            misses++
            -5
        } else {
            hits += targets.size
            val ids = targets.map { it.id }.toSet()
            bugs = bugs.filterNot { it.id in ids }
            repeat(targets.size) { replacements.add(elapsed + .6) }
            targets.sumOf { it.points }
        }

        score += points

        return AttackResult(points, targets)
    }
}
