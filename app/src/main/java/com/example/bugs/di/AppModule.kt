package com.example.bugs.di

import androidx.room.Room
import com.example.bugs.data.BugsDatabase
import com.example.bugs.data.BugsRepository
import com.example.bugs.data.GoldRepository
import com.example.bugs.data.PlayerUniquenessMigration
import com.example.bugs.feature.game.presentation.GameViewModel
import com.example.bugs.feature.gold.widget.GoldWidgetProvider
import com.example.bugs.feature.profile.presentation.ProfileViewModel
import com.example.bugs.feature.records.presentation.RecordsViewModel
import com.example.bugs.feature.registration.presentation.RegistrationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(androidContext(), BugsDatabase::class.java, "bugs.db")
            .addMigrations(PlayerUniquenessMigration)
            .build()
    }
    single { BugsRepository(get(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    single {
        val context = androidContext()
        GoldRepository(context) { GoldWidgetProvider.updateAll(context) }
    }

    viewModelOf(::GameViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::RecordsViewModel)
    viewModelOf(::RegistrationViewModel)
}
