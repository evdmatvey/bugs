package com.example.bugs

import android.app.Application
import androidx.room.Room
import com.example.bugs.data.BugsDatabase
import com.example.bugs.data.BugsRepository
import com.example.bugs.data.GoldRepository
import com.example.bugs.data.PlayerUniquenessMigration
import com.example.bugs.feature.gold.widget.GoldWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BugsApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database by lazy {
        Room.databaseBuilder(this, BugsDatabase::class.java, "bugs.db")
            .addMigrations(PlayerUniquenessMigration)
            .build()
    }

    val repository by lazy {
        BugsRepository(database, applicationScope)
    }

    val goldRepository by lazy {
        GoldRepository(this) { GoldWidgetProvider.updateAll(this) }
    }
}
