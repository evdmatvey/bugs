package com.example.bugs.feature.gold.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.bugs.BugsApplication
import com.example.bugs.R
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class GoldWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val REFRESH = "com.example.bugs.REFRESH_GOLD"
        private const val PERIODIC_WORK = "gold_periodic"
        private const val REFRESH_WORK = "gold_refresh"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GoldWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val state = (context.applicationContext as BugsApplication).goldRepository.gold.value
            val quote = state.quote
            val price = if (quote == null) {
                context.getString(R.string.gold_widget_empty)
            } else {
                val format = NumberFormat.getNumberInstance().apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
                context.getString(R.string.gold_widget_price, format.format(quote.price))
            }
            val date = quote?.date?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val message = when {
                state.loading -> context.getString(R.string.gold_loading)
                state.error && quote != null -> context.getString(R.string.gold_widget_cached, date)
                state.error -> context.getString(R.string.gold_widget_error)
                quote != null -> requireNotNull(date)
                else -> context.getString(R.string.gold_widget_retry)
            }
            val refresh = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, GoldWidgetProvider::class.java).setAction(REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_gold).apply {
                setTextViewText(R.id.gold_price, price)
                setTextViewText(R.id.gold_date, message)
                setContentDescription(
                    R.id.gold_sphere,
                    context.getString(R.string.gold_widget_accessibility, price, message)
                )
                setOnClickPendingIntent(R.id.gold_sphere, refresh)
            }

            manager.updateAppWidget(ids, views)
        }

        private fun refresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                REFRESH_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GoldRefreshWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<GoldRefreshWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == REFRESH) refresh(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK)
            cancelUniqueWork(REFRESH_WORK)
        }
    }
}
