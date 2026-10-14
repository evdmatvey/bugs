package com.example.bugs.feature.records.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.bugs.BugsApplication
import com.example.bugs.data.NamedResult
import com.example.bugs.domain.game.GameDifficulty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

enum class RecordsSection {
    RECORDS,
    RATING
}

data class RecordRow(
    val difficulty: GameDifficulty,
    val result: NamedResult?,
    val place: Int? = null
)

data class RecordsUiState(
    val section: RecordsSection = RecordsSection.RECORDS,
    val difficulty: GameDifficulty = GameDifficulty.MEDIUM,
    val loading: Boolean = true,
    val error: Boolean = false,
    val rows: List<RecordRow> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = (application as BugsApplication).repository
    private val section = savedState.getStateFlow("recordsSection", RecordsSection.RECORDS)
    private val difficulty = savedState.getStateFlow("recordsDifficulty", GameDifficulty.MEDIUM)
    private val retry = MutableStateFlow(0)

    private val records = combine(section, difficulty, retry) { section, difficulty, _ ->
        section to difficulty
    }.flatMapLatest { (section, difficulty) ->
        val results = when (section) {
            RecordsSection.RECORDS -> repository.observePlayerRecords(difficulty)
            RecordsSection.RATING -> repository.observeGlobalRating()
        }

        results.map { results ->
            val rows = when (section) {
                RecordsSection.RECORDS -> results.mapIndexed { index, result ->
                    RecordRow(difficulty, result, index + 1)
                }

                RecordsSection.RATING -> GameDifficulty.entries.map { level ->
                    RecordRow(level, results.find { it.result.difficulty == level.name })
                }
            }

            RecordsUiState(section, difficulty, loading = false, rows = rows)
        }.onStart {
            emit(RecordsUiState(section, difficulty))
        }.catch {
            emit(RecordsUiState(section, difficulty, loading = false, error = true))
        }
    }

    val uiState = combine(records, repository.device) { records, device ->
        records.copy(
            loading = records.loading || device.loading,
            error = records.error || device.error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RecordsUiState(section.value, difficulty.value)
    )

    fun setSection(value: RecordsSection) {
        savedState["recordsSection"] = value
    }

    fun setDifficulty(value: GameDifficulty) {
        savedState["recordsDifficulty"] = value
    }

    fun reload() {
        repository.reload()
        retry.value++
    }
}
