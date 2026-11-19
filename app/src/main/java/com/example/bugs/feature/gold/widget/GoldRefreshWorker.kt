package com.example.bugs.feature.gold.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bugs.data.GoldRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GoldRefreshWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters), KoinComponent {

    private val repository: GoldRepository by inject()

    override suspend fun doWork(): Result {
        return if (repository.refresh(force = true)) {
            Result.success()
        } else if (runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.failure()
        }
    }
}
