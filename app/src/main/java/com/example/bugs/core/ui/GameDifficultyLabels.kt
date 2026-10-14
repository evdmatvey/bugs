package com.example.bugs.core.ui

import android.content.Context
import com.example.bugs.R
import com.example.bugs.domain.game.GameDifficulty

fun GameDifficulty.labelRes(): Int = when (this) {
    GameDifficulty.EASY -> R.string.registration_difficulty_easy
    GameDifficulty.MEDIUM -> R.string.registration_difficulty_medium
    GameDifficulty.HARD -> R.string.registration_difficulty_hard
}

fun Context.gameModeLabel(difficulty: String?): String = getString(
    difficulty?.let { GameDifficulty.valueOf(it).labelRes() } ?: R.string.game_mode_custom
)
