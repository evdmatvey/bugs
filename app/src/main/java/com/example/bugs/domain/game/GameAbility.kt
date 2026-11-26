package com.example.bugs.domain.game

import kotlin.random.Random

enum class GameAbility(val price: Int, val duration: Double = 0.0) {
    BAIT(30, 5.0),
    SLOW(40, 7.0),
    FREEZE(50, 3.0),
    MULTITOUCH(60, 6.0),
    BOMB(70),
    CHAIN(80)
}

class AbilityOffers(private val random: Random = Random.Default) {

    private val shown = IntArray(GameAbility.entries.size)
    private var previous = emptyList<GameAbility>()

    fun next(score: Int): List<GameAbility> {
        val abilities = GameAbility.entries
        val pairs = abilities.flatMapIndexed { index, first ->
            abilities.drop(index + 1).map { second -> listOf(first, second) }
        }.filter { pair -> pair.any { it.price <= score } }

        if (pairs.isEmpty()) return emptyList()

        val overlap = pairs.minOf { pair -> pair.count { it in previous } }
        val fresh = pairs.filter { pair -> pair.count { it in previous } == overlap }
        val count = fresh.minOf { pair -> pair.sumOf { shown[it.ordinal] } }
        val selected = fresh.filter { pair -> pair.sumOf { shown[it.ordinal] } == count }
            .random(random).shuffled(random)

        selected.forEach { shown[it.ordinal]++ }
        previous = selected

        return selected
    }
}

data class Bait(val x: Float, val y: Float, val expiresAt: Double)

data class AbilityEffect(
    val ability: GameAbility,
    val x: Float,
    val y: Float,
    val born: Double,
    val targets: List<Bug> = emptyList()
)

data class AttackResult(val points: Int, val bugs: List<Bug> = emptyList())
