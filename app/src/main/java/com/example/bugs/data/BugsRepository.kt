package com.example.bugs.data

import androidx.room.withTransaction
import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.domain.game.GameSettings
import com.example.bugs.domain.player.Player
import com.example.bugs.domain.player.cleanPlayerName
import com.example.bugs.domain.player.normalizePlayerName
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DeviceData(
    val loading: Boolean = true,
    val error: Boolean = false,
    val players: List<PlayerEntity> = emptyList(),
    val selection: DeviceStateEntity = DeviceStateEntity()
) {
    val activePlayer: PlayerEntity?
        get() = players.find { it.id == selection.playerId }
}

sealed interface CreatePlayerResult {
    data class Created(val id: Long) : CreatePlayerResult
    data class AlreadyExists(val id: Long) : CreatePlayerResult
}

class BugsRepository(
    private val database: BugsDatabase,
    private val scope: CoroutineScope
) {

    private val dao = database.dao()
    private val state = MutableStateFlow(DeviceData())
    private var observation: Job? = null

    val device = state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        observation?.cancel()
        state.value = state.value.copy(loading = true, error = false)

        observation = scope.launch {
            try {
                database.withTransaction {
                    if (dao.deviceState() == null) {
                        dao.saveDeviceState(DeviceStateEntity())
                    }
                }

                combine(dao.observePlayers(), dao.observeDeviceState()) { players, selection ->
                    DeviceData(
                        loading = false,
                        players = players,
                        selection = selection ?: DeviceStateEntity()
                    )
                }.collect { state.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = state.value.copy(loading = false, error = true)
            }
        }
    }

    suspend fun createPlayer(player: Player): CreatePlayerResult = database.withTransaction {
        val fullName = cleanPlayerName(player.fullName)
        val normalizedName = normalizePlayerName(fullName)
        val birthDate = player.birthDate.toEpochDay()

        require(fullName.isNotBlank())
        require(player.course in 1..4)
        require(!player.birthDate.isAfter(LocalDate.now()))

        dao.findPlayer(normalizedName, birthDate)?.let {
            return@withTransaction CreatePlayerResult.AlreadyExists(it.id)
        }

        val id = dao.insertPlayer(
            PlayerEntity(
                fullName = fullName,
                normalizedFullName = normalizedName,
                gender = player.gender.name,
                course = player.course,
                birthDate = birthDate,
                difficulty = player.difficulty.name
            )
        )

        if (id == -1L) {
            val existing = requireNotNull(dao.findPlayer(normalizedName, birthDate))
            return@withTransaction CreatePlayerResult.AlreadyExists(existing.id)
        }

        val current = dao.deviceState() ?: DeviceStateEntity()
        dao.saveDeviceState(current.copy(playerId = id, difficulty = player.difficulty.name))

        CreatePlayerResult.Created(id)
    }

    suspend fun selectPlayer(id: Long) = database.withTransaction {
        val player = requireNotNull(dao.player(id))
        val current = dao.deviceState() ?: DeviceStateEntity()

        dao.saveDeviceState(current.copy(playerId = id, difficulty = player.difficulty))
    }

    suspend fun applySettings(difficulty: GameDifficulty?, custom: GameSettings) = database.withTransaction {
        val current = dao.deviceState() ?: DeviceStateEntity()

        dao.saveDeviceState(
            current.copy(
                difficulty = difficulty?.name,
                customSettings = if (difficulty == null) custom else current.customSettings
            )
        )

        if (difficulty != null) {
            current.playerId?.let { dao.updateDifficulty(it, difficulty.name) }
        }
    }

    suspend fun saveResult(result: ResultEntity) {
        require(result.hits >= 0 && result.misses >= 0)
        require(
            result.difficulty == null ||
                GameDifficulty.valueOf(result.difficulty).settings() == result.settings
        )

        dao.insertResult(result)
    }

    fun observePlayerRecords(difficulty: GameDifficulty) = dao.observePlayerRecords(difficulty.name)

    fun observeGlobalRating() = dao.observeGlobalRating()
}
