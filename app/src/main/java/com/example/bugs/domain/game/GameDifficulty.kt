package com.example.bugs.domain.game

enum class GameDifficulty {
    EASY,
    MEDIUM,
    HARD;

    fun settings(): GameSettings = when (this) {
        EASY -> GameSettings(speed = .5f, maxBugs = 15, bonusInterval = 10)
        MEDIUM -> GameSettings()
        HARD -> GameSettings(speed = 2f, maxBugs = 5, bonusInterval = 30)
    }
}
