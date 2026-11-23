package net.hordecraft.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_ONGOING = "hordecraft_ongoing"
    const val CHANNEL_RESULTS = "hordecraft_results"
    const val CHANNEL_OUTSIDE = "hordecraft_outside"
    const val CHANNEL_QUEUE = "hordecraft_queue"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_ONGOING,
                "Generation Progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress while active image requests are running."
            },
            NotificationChannel(
                CHANNEL_RESULTS,
                "Finished & Failed Images",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifies when generated WebP images are saved or fail."
            },
            NotificationChannel(
                CHANNEL_OUTSIDE,
                "Other App Checks",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Reserved for later background checks from external requests."
            },
            NotificationChannel(
                CHANNEL_QUEUE,
                "Queue & Rate Alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Pausable queue updates and worker wait warnings."
            },
        )
        nm.createNotificationChannels(channels)
    }
}
