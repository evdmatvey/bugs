package com.example.bugs.feature.gold.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bugs.BugsApplication

class GoldRefreshWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as BugsApplication).goldRepository

        return if (repository.refresh(force = true)) {
            Result.success()
        } else if (runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.failure()
        }
    }
}
