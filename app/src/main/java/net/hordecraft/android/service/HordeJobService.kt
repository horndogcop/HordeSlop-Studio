package net.hordeslop.studio.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.hordeslop.studio.HordecraftApp
import net.hordeslop.studio.R
import net.hordeslop.studio.domain.ActivityDestination
import net.hordeslop.studio.domain.JobStatus
import net.hordeslop.studio.ui.components.buildIntentForDestination

class HordeJobService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                scope.launch {
                    appContainer().repository.pauseQueue()
                    updateProgressNotification("Queue paused")
                }
            }
            ACTION_RESUME -> {
                scope.launch {
                    appContainer().repository.resumeQueue()
                    ensurePollingLoop()
                }
            }
            ACTION_CANCEL_JOB -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID)
                if (!jobId.isNullOrBlank()) {
                    scope.launch {
                        runCatching { appContainer().repository.cancelJobWithPartialRecovery(jobId) }
                    }
                }
            }
            else -> {
                val notif = buildProgressNotification("Checking queue...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIF_ID_ONGOING,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(NOTIF_ID_ONGOING, notif)
                }
                ensurePollingLoop()
            }
        }
        return START_STICKY
    }

    private fun ensurePollingLoop() {
        if (pollingJob?.isActive == true) return
        val repo = appContainer().repository

        pollingJob = scope.launch {
            while (isActive) {
                repo.syncDismissedJobs().forEach { notif ->
                    postResultNotification(notif.title, notif.body)
                }

                val visiblePending = repo.getTrackedJobsForPolling().filter { !it.dismissed }
                val activeRunnable = visiblePending.filter {
                    JobStatus.fromStorage(it.status).isRunnable
                }
                if (visiblePending.isEmpty()) {
                    ServiceCompat.stopForeground(this@HordeJobService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                if (activeRunnable.isEmpty()) {
                    updateProgressNotification("Paused (${visiblePending.size} waiting)")
                    delay(2_500L)
                    continue
                }

                updateProgressNotification("${activeRunnable.size} active request(s)")

                for (job in activeRunnable) {
                    if (!isActive) break
                    val notif = if (job.status == JobStatus.LOCAL_QUEUED.name) {
                        runCatching { repo.dispatchLocalQueuedJob(job) }.getOrNull()
                    } else {
                        runCatching { repo.pollVisibleRunnableJob(job) }.getOrNull()
                    }
                    if (notif != null) {
                        postResultNotification(notif.title, notif.body)
                    }
                }
                delay(2_000L)
            }
        }
    }

    private fun appContainer() = (application as HordecraftApp).container

    private fun buildProgressNotification(text: String): Notification {
        val openIntent = buildIntentForDestination(this, ActivityDestination.IMAGE_GEN)
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_robot)
            .setContentTitle("HordeSlop Studio")
            .setContentText(text)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateProgressNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_ONGOING, buildProgressNotification(text))
    }

    private fun postResultNotification(title: String, body: String) {
        val galleryIntent = buildIntentForDestination(this, ActivityDestination.GALLERY)
        val pendingGallery = PendingIntent.getActivity(
            this,
            101,
            galleryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_launcher_robot)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingGallery)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() % 100_000).toInt(), notif)
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID_ONGOING = 4001
        const val ACTION_START_OR_POLL = "net.hordeslop.studio.action.START_POLL"
        const val ACTION_PAUSE = "net.hordeslop.studio.action.PAUSE"
        const val ACTION_RESUME = "net.hordeslop.studio.action.RESUME"
        const val ACTION_CANCEL_JOB = "net.hordeslop.studio.action.CANCEL_JOB"
        const val EXTRA_JOB_ID = "extra_job_id"

        fun startPolling(context: Context) {
            val intent = Intent(context, HordeJobService::class.java).setAction(ACTION_START_OR_POLL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun sendPause(context: Context) {
            context.startService(Intent(context, HordeJobService::class.java).setAction(ACTION_PAUSE))
        }

        fun sendResume(context: Context) {
            startPolling(context)
            context.startService(Intent(context, HordeJobService::class.java).setAction(ACTION_RESUME))
        }

        fun cancelJob(context: Context, jobId: String) {
            context.startService(
                Intent(context, HordeJobService::class.java)
                    .setAction(ACTION_CANCEL_JOB)
                    .putExtra(EXTRA_JOB_ID, jobId),
            )
        }
    }
}
