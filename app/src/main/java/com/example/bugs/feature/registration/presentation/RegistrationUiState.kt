package com.example.bugs.feature.registration.presentation

import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.domain.player.Gender
import com.example.bugs.domain.player.Player
import java.time.LocalDate

data class RegistrationUiState(
    val fullName: String = "",
    val gender: Gender? = null,
    val course: Int? = null,
    val difficulty: GameDifficulty = GameDifficulty.MEDIUM,
    val birthDate: LocalDate? = null,
    val submittedPlayer: Player? = null,
    val fullNameError: Boolean = false,
    val genderError: Boolean = false,
    val courseError: Boolean = false,
    val birthDateError: Boolean = false
)