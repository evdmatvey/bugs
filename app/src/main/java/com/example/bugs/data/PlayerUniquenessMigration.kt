package com.example.bugs.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bugs.domain.player.cleanPlayerName
import com.example.bugs.domain.player.normalizePlayerName

private data class ExistingPlayer(
    val id: Long,
    val fullName: String,
    val birthDate: Long
)

object PlayerUniquenessMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN normalizedFullName TEXT NOT NULL DEFAULT ''")

        val selectedId = db.query("SELECT playerId FROM device_state WHERE id = 1").use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        val players = db.query("SELECT id, fullName, birthDate FROM players ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(ExistingPlayer(cursor.getLong(0), cursor.getString(1), cursor.getLong(2)))
                }
            }
        }
        val groups = players.groupBy { normalizePlayerName(it.fullName) to it.birthDate }

        groups.forEach { (identity, duplicates) ->
            val survivor = duplicates.find { it.id == selectedId } ?: duplicates.first()

            db.execSQL(
                "UPDATE players SET fullName = ?, normalizedFullName = ? WHERE id = ?",
                arrayOf<Any>(cleanPlayerName(survivor.fullName), identity.first, survivor.id)
            )

            duplicates.filter { it.id != survivor.id }.forEach { duplicate ->
                db.execSQL("UPDATE results SET playerId = ? WHERE playerId = ?", arrayOf<Any>(survivor.id, duplicate.id))
                db.execSQL("UPDATE device_state SET playerId = ? WHERE playerId = ?", arrayOf<Any>(survivor.id, duplicate.id))
                db.execSQL("DELETE FROM players WHERE id = ?", arrayOf<Any>(duplicate.id))
            }
        }

        db.execSQL("CREATE UNIQUE INDEX index_players_normalizedFullName_birthDate ON players(normalizedFullName, birthDate)")
    }
}
