package com.example.bugs.feature.game.presentation

import androidx.lifecycle.ViewModel
import com.example.bugs.domain.game.GameRound
import com.example.bugs.domain.game.GameSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GamePhase {
    READY,
    RUNNING,
    PAUSED,
    FINISHED
}

data class GameUiState(
    val settings: GameSettings = GameSettings(),
    val phase: GamePhase = GamePhase.READY,
    val round: GameRound? = null,
    val revision: Long = 0
)

class GameViewModel : ViewModel() {

    private val state = MutableStateFlow(GameUiState())

    val uiState = state.asStateFlow()

    private var lastFrame: Long? = null

    fun applySettings(settings: GameSettings) {
        state.value = state.value.copy(settings = settings)
    }

    fun start() {
        lastFrame = null
        state.value = state.value.copy(
            round = GameRound(state.value.settings),
            phase = GamePhase.RUNNING
        )
    }

    fun pause() {
        if (state.value.phase == GamePhase.RUNNING) {
            state.value = state.value.copy(phase = GamePhase.PAUSED)
        }

        lastFrame = null
    }

    fun resume() {
        if (state.value.phase == GamePhase.PAUSED) {
            lastFrame = null
            state.value = state.value.copy(phase = GamePhase.RUNNING)
        }
    }

    fun frame(nanos: Long) {
        if (state.value.phase != GamePhase.RUNNING) return

        val previous = lastFrame
        if (previous != null && nanos <= previous) return

        lastFrame = nanos
        if (previous != null) {
            state.value.round?.advance(((nanos - previous) / 1e9).coerceAtLeast(0.0))
        }

        publish()
    }

    fun tap(x: Float, y: Float, nanos: Long): Int? {
        frame(nanos)
        if (state.value.phase != GamePhase.RUNNING) return null

        val points = state.value.round?.tap(x, y)
        publish()

        return points
    }

    private fun publish() {
        state.value = state.value.copy(
            phase = if (state.value.round?.finished == true) {
                GamePhase.FINISHED
            } else {
                state.value.phase
            },
            revision = state.value.revision + 1
        )
    }
}
