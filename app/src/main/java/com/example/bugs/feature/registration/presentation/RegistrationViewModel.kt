package com.example.bugs.feature.registration.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bugs.BugsApplication
import com.example.bugs.data.CreatePlayerResult
import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.domain.player.Gender
import com.example.bugs.domain.player.Player
import com.example.bugs.domain.player.cleanPlayerName
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as BugsApplication).repository

    private val _uiState = MutableStateFlow(RegistrationUiState())

    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    fun onFullNameChanged(fullName: String) {
        _uiState.update { currentState ->
            currentState.copy(
                fullName = fullName,
                fullNameError = false,
                duplicatePlayer = false
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
                birthDateError = false,
                duplicatePlayer = false
            )
        }
    }

    fun onSubmit() {
        val currentState = _uiState.value

        if (currentState.saving || currentState.submittedPlayer != null) return

        val fullNameMissing = currentState.fullName.isBlank()
        val genderMissing = currentState.gender == null
        val courseMissing = currentState.course !in 1..4
        val birthDateMissing = currentState.birthDate == null || currentState.birthDate.isAfter(LocalDate.now())

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
            fullName = cleanPlayerName(currentState.fullName),
            gender = requireNotNull(currentState.gender),
            course = requireNotNull(currentState.course),
            difficulty = currentState.difficulty,
            birthDate = requireNotNull(currentState.birthDate)
        )

        _uiState.update {
            it.copy(saving = true, saveError = false, duplicatePlayer = false, submittedPlayer = null)
        }

        viewModelScope.launch {
            try {
                when (repository.createPlayer(player)) {
                    is CreatePlayerResult.Created -> _uiState.update {
                        it.copy(
                            saving = false,
                            submittedPlayer = player,
                            fullNameError = false,
                            genderError = false,
                            courseError = false,
                            birthDateError = false
                        )
                    }

                    is CreatePlayerResult.AlreadyExists -> _uiState.update {
                        it.copy(saving = false, duplicatePlayer = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(saving = false, saveError = true) }
            }
        }
    }
}
