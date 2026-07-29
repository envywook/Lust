package com.envy.dualcorevpn.subscription

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.envy.dualcorevpn.R
import java.util.concurrent.TimeUnit

class SubscriptionRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = SubscriptionRepository(applicationContext)
        val subscriptions = repository.subscriptions()
        if (subscriptions.isEmpty()) return Result.success()
        var changed = 0
        val errors = mutableListOf<String>()
        subscriptions.forEach { subscription ->
            val before = repository.servers().filter { it.subscriptionId == subscription.id }
                .associate { it.id to it.config }
            runCatching { repository.update(subscription) }
                .onSuccess {
                    val after = repository.servers().filter { it.subscriptionId == subscription.id }
                        .associate { it.id to it.config }
                    if (before != after) changed++
                }
                .onFailure { errors += subscription.name }
        }
        if (changed > 0 || errors.isNotEmpty()) notifyResult(changed, errors)
        return if (errors.size == subscriptions.size) Result.retry() else Result.success()
    }

    private fun notifyResult(changed: Int, errors: List<String>) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.subscription_refresh_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val text = when {
            errors.isEmpty() -> applicationContext.getString(R.string.subscription_refresh_changed, changed)
            changed == 0 -> applicationContext.getString(R.string.subscription_refresh_failed, errors.joinToString())
            else -> applicationContext.getString(R.string.subscription_refresh_partial, changed, errors.joinToString())
        }
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_vpn)
                .setContentTitle(applicationContext.getString(R.string.subscriptions_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val WORK_NAME = "subscription-refresh"
        private const val CHANNEL_ID = "subscription-refresh"
        private const val NOTIFICATION_ID = 47

        fun schedule(context: Context, hours: Int) {
            val manager = WorkManager.getInstance(context)
            if (hours <= 0) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
