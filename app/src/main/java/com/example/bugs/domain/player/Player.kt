package com.example.bugs.domain.player

import com.example.bugs.domain.game.GameDifficulty
import java.time.LocalDate

data class Player(
    val fullName: String,
    val gender: Gender,
    val course: Int,
    val difficulty: GameDifficulty,
    val birthDate: LocalDate
) {
    val zodiacSign: ZodiacSign
        get() = ZodiacSign.from(birthDate)
}
