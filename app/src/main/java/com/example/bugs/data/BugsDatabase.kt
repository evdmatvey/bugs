package com.example.bugs.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.domain.game.GameSettings
import java.io.Serializable
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "players",
    indices = [Index(value = ["normalizedFullName", "birthDate"], unique = true)]
)
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    @ColumnInfo(defaultValue = "''") val normalizedFullName: String,
    val gender: String,
    val course: Int,
    val birthDate: Long,
    val difficulty: String
)

@Entity(
    tableName = "results",
    foreignKeys = [ForeignKey(
        entity = PlayerEntity::class,
        parentColumns = ["id"],
        childColumns = ["playerId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("playerId"), Index(value = ["difficulty", "score"])]
)
data class ResultEntity(
    @PrimaryKey val roundId: String,
    val playerId: Long,
    val difficulty: String?,
    @Embedded(prefix = "settings_") val settings: GameSettings,
    val score: Int,
    val hits: Int,
    val misses: Int,
    val finishedAt: Long
) : Serializable {
    val accuracy: Double
        get() = if (hits + misses == 0) 0.0 else hits * 100.0 / (hits + misses)
}

data class NamedResult(
    @Embedded val result: ResultEntity,
    val fullName: String,
    val course: Int,
    val birthDate: Long
)

@Entity(tableName = "device_state")
data class DeviceStateEntity(
    @PrimaryKey val id: Int = 1,
    val playerId: Long? = null,
    val difficulty: String? = GameDifficulty.MEDIUM.name,
    @Embedded(prefix = "custom_") val customSettings: GameSettings = GameSettings()
) {
    fun settings(): GameSettings = difficulty?.let {
        GameDifficulty.valueOf(it).settings()
    } ?: customSettings
}

private const val RESULT_ORDER =
    "score DESC, COALESCE(1.0 * hits / NULLIF(hits + misses, 0), 0) DESC, finishedAt, roundId"

@Dao
interface BugsDao {
    @Query("SELECT * FROM players ORDER BY normalizedFullName, id")
    fun observePlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM device_state WHERE id = 1")
    fun observeDeviceState(): Flow<DeviceStateEntity?>

    @Query("SELECT * FROM device_state WHERE id = 1")
    suspend fun deviceState(): DeviceStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlayer(player: PlayerEntity): Long

    @Query("SELECT * FROM players WHERE normalizedFullName = :name AND birthDate = :birthDate")
    suspend fun findPlayer(name: String, birthDate: Long): PlayerEntity?

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun player(id: Long): PlayerEntity?

    @Query("UPDATE players SET difficulty = :difficulty WHERE id = :id")
    suspend fun updateDifficulty(id: Long, difficulty: String)

    @Upsert
    suspend fun saveDeviceState(state: DeviceStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResult(result: ResultEntity): Long

    @Query(
        "WITH ranked AS (SELECT roundId, ROW_NUMBER() OVER (PARTITION BY playerId ORDER BY " +
            RESULT_ORDER + ") AS place FROM results WHERE difficulty = :difficulty) " +
            "SELECT r.*, p.fullName, p.course, p.birthDate FROM results r " +
            "JOIN ranked ON ranked.roundId = r.roundId JOIN players p ON p.id = r.playerId " +
            "WHERE ranked.place = 1 ORDER BY " + RESULT_ORDER
    )
    fun observePlayerRecords(difficulty: String): Flow<List<NamedResult>>

    @Query(
        "WITH ranked AS (SELECT roundId, ROW_NUMBER() OVER (PARTITION BY difficulty ORDER BY " +
            RESULT_ORDER + ") AS place FROM results WHERE difficulty IN ('EASY', 'MEDIUM', 'HARD')) " +
            "SELECT r.*, p.fullName, p.course, p.birthDate FROM results r " +
            "JOIN ranked ON ranked.roundId = r.roundId JOIN players p ON p.id = r.playerId " +
            "WHERE ranked.place = 1 ORDER BY CASE r.difficulty WHEN 'EASY' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END"
    )
    fun observeGlobalRating(): Flow<List<NamedResult>>
}

@Database(
    entities = [PlayerEntity::class, ResultEntity::class, DeviceStateEntity::class],
    version = 2,
    exportSchema = true
)
abstract class BugsDatabase : RoomDatabase() {
    abstract fun dao(): BugsDao
}
