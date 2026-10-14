package com.example.bugs.feature.game.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.bugs.BugsApplication
import com.example.bugs.data.DeviceData
import com.example.bugs.data.ResultEntity
import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.domain.game.GameRound
import com.example.bugs.domain.game.GameSettings
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GamePhase {
    READY,
    RUNNING,
    PAUSED,
    FINISHED
}

enum class SaveStatus {
    NONE,
    SAVING,
    SAVED,
    ERROR
}

data class GameUiState(
    val settings: GameSettings = GameSettings(),
    val phase: GamePhase = GamePhase.READY,
    val round: GameRound? = null,
    val revision: Long = 0,
    val device: DeviceData = DeviceData(),
    val result: ResultEntity? = null,
    val roundPlayerName: String = "",
    val saveStatus: SaveStatus = SaveStatus.NONE,
    val settingsSaving: Boolean = false,
    val settingsError: Boolean = false
)

class GameViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = (application as BugsApplication).repository
    private val restored = savedState.get<ResultEntity>("pendingResult")
    private val state = MutableStateFlow(
        GameUiState(
            phase = if (restored == null) GamePhase.READY else GamePhase.FINISHED,
            result = restored,
            roundPlayerName = savedState["roundPlayerName"] ?: ""
        )
    )

    val uiState = state.asStateFlow()

    private var lastFrame: Long? = null
    private var roundId = ""
    private var roundPlayerId = 0L
    private var roundDifficulty: String? = null

    init {
        viewModelScope.launch {
            repository.device.collect { device ->
                state.value = state.value.copy(device = device, settings = device.selection.settings())

                if (!device.loading && !device.error && restored != null && state.value.saveStatus == SaveStatus.NONE) {
                    retrySave()
                }
            }
        }
    }

    fun reload() = repository.reload()

    fun applySettings(settings: GameSettings, difficulty: GameDifficulty?) {
        if (state.value.settingsSaving || state.value.device.loading || state.value.device.error) return

        state.value = state.value.copy(settingsSaving = true, settingsError = false)

        viewModelScope.launch {
            try {
                repository.applySettings(difficulty, settings)
                state.value = state.value.copy(settingsSaving = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = state.value.copy(settingsSaving = false, settingsError = true)
            }
        }
    }

    fun start() {
        val current = state.value
        val player = current.device.activePlayer ?: return

        if (
            current.device.loading || current.device.error || current.settingsSaving ||
            current.phase == GamePhase.RUNNING || current.phase == GamePhase.PAUSED ||
            (current.result != null && current.saveStatus != SaveStatus.SAVED)
        ) return

        lastFrame = null
        roundId = UUID.randomUUID().toString()
        roundPlayerId = player.id
        roundDifficulty = current.device.selection.difficulty
        savedState["roundPlayerName"] = player.fullName

        state.value = current.copy(
            round = GameRound(current.settings),
            phase = GamePhase.RUNNING,
            roundPlayerName = player.fullName,
            result = null,
            saveStatus = SaveStatus.NONE
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
        val round = state.value.round ?: return

        if (round.finished && state.value.phase != GamePhase.FINISHED) {
            val result = ResultEntity(
                roundId = roundId,
                playerId = roundPlayerId,
                difficulty = roundDifficulty,
                settings = round.settings,
                score = round.score,
                hits = round.hits,
                misses = round.misses,
                finishedAt = System.currentTimeMillis()
            )

            savedState["pendingResult"] = result
            state.value = state.value.copy(phase = GamePhase.FINISHED, result = result)
            retrySave()
        }

        state.value = state.value.copy(revision = state.value.revision + 1)
    }

    fun retrySave() {
        val result = state.value.result ?: return

        if (state.value.saveStatus == SaveStatus.SAVING || state.value.saveStatus == SaveStatus.SAVED) return

        state.value = state.value.copy(saveStatus = SaveStatus.SAVING)

        viewModelScope.launch {
            try {
                repository.saveResult(result)
                savedState.remove<ResultEntity>("pendingResult")
                state.value = state.value.copy(saveStatus = SaveStatus.SAVED)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = state.value.copy(saveStatus = SaveStatus.ERROR)
            }
        }
    }
}
