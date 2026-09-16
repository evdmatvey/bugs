package com.example.bugs.feature.registration.presentation

import androidx.lifecycle.ViewModel
import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.domain.player.Gender
import com.example.bugs.domain.player.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class RegistrationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())

    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    fun onFullNameChanged(fullName: String) {
        _uiState.update { currentState ->
            currentState.copy(
                fullName = fullName,
                fullNameError = false
            )
        }
    }

    fun onGenderChanged(gender: Gender) {
        _uiState.update { currentState ->
            currentState.copy(
                gender = gender,
                genderError = false
            )
        }
    }

    fun onCourseChanged(course: Int) {
        _uiState.update { currentState ->
            currentState.copy(
                course = course,
                courseError = false
            )
        }
    }

    fun onDifficultyChanged(difficulty: GameDifficulty) {
        _uiState.update { currentState ->
            currentState.copy(difficulty = difficulty)
        }
    }

    fun onBirthDateChanged(birthDate: LocalDate) {
        _uiState.update { currentState ->
            currentState.copy(
                birthDate = birthDate,
                birthDateError = false
            )
        }
    }

    fun onSubmit() {
        val currentState = _uiState.value

        val fullNameMissing = currentState.fullName.isBlank()
        val genderMissing = currentState.gender == null
        val courseMissing = currentState.course == null
        val birthDateMissing = currentState.birthDate == null

        if (
            fullNameMissing ||
            genderMissing ||
            courseMissing ||
            birthDateMissing
        ) {
            _uiState.update {
                it.copy(
                    submittedPlayer = null,
                    fullNameError = fullNameMissing,
                    genderError = genderMissing,
                    courseError = courseMissing,
                    birthDateError = birthDateMissing
                )
            }
            return
        }

        val player = Player(
            fullName = currentState.fullName.trim(),
            gender = requireNotNull(currentState.gender),
            course = requireNotNull(currentState.course),
            difficulty = currentState.difficulty,
            birthDate = requireNotNull(currentState.birthDate)
        )

        _uiState.update {
            it.copy(
                submittedPlayer = player,
                fullNameError = false,
                genderError = false,
                courseError = false,
                birthDateError = false
            )
        }
    }
}