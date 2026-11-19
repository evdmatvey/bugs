package com.example.bugs

import android.app.Application
import com.example.bugs.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BugsApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@BugsApplication)
            modules(appModule)
        }
    }
}
