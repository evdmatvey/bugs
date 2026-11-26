package com.example.bugs.domain.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbilityOffersTest {

    @Test
    fun nextReturnsNoOfferWhenScoreIsBelowCheapestAbility() {
        val offers = AbilityOffers(Random(42))

        for (score in listOf(0, 29)) {
            assertTrue("No ability is affordable at score $score", offers.next(score).isEmpty())
        }
    }

    @Test
    fun nextOffersTwoDistinctAbilitiesIncludingBaitAtExactMinimumScore() {
        val offers = AbilityOffers(Random(42))

        val offer = offers.next(30)

        assertEquals(2, offer.size)
        assertEquals(2, offer.toSet().size)
        assertTrue(offer.contains(GameAbility.BAIT))
        assertEquals(listOf(GameAbility.BAIT), offer.filter { it.price <= 30 })
    }

    @Test
    fun nextMinimizesOverlapWithPreviousOffer() {
        for (score in listOf(80, 30)) {
            val offers = AbilityOffers(Random(42))
            val expectedOverlap = if (score == 30) setOf(GameAbility.BAIT) else emptySet()
            var previous = offers.next(score)

            repeat(10) {
                val current = offers.next(score)

                assertEquals("Offer size at score $score", 2, current.size)
                assertEquals(
                    "Overlap at score $score, transition ${it + 1}",
                    expectedOverlap,
                    previous.toSet().intersect(current.toSet())
                )
                previous = current
            }
        }
    }

    @Test
    fun nextShowsEveryAbilityOnceAcrossFirstThreeOffersWhenAllAreAffordable() {
        val offers = AbilityOffers(Random(42))

        val shown = List(3) { offers.next(80) }.flatten()

        assertEquals(
            GameAbility.entries.associateWith { 1 },
            shown.groupingBy { it }.eachCount()
        )
    }
}
