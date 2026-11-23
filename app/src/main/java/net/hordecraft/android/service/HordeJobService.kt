package net.hordecraft.android.service

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
import net.hordecraft.android.HordecraftApp
import net.hordecraft.android.R
import net.hordecraft.android.domain.JobStatus
import net.hordecraft.android.ui.activities.GalleryActivity
import net.hordecraft.android.ui.activities.ImageGenActivity

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
                val jobs = repo.getPendingJobs()
                val activeRunnable = jobs.filter {
                    it.status == JobStatus.QUEUED.name || it.status == JobStatus.PROCESSING.name
                }
                if (jobs.isEmpty()) {
                    ServiceCompat.stopForeground(this@HordeJobService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                if (activeRunnable.isEmpty()) {
                    updateProgressNotification("Paused (${jobs.size} waiting)")
                    delay(2_500L)
                    continue
                }

                updateProgressNotification("${activeRunnable.size} active request(s)")

                for (job in activeRunnable) {
                    if (!isActive) break
                    val checkResult = runCatching { repo.checkJobProgress(job.id) }.getOrNull() ?: continue

                    repo.updateJobRecord(
                        job.copy(
                            status = JobStatus.PROCESSING.name,
                            waitTimeSeconds = checkResult.waitTime,
                            queuePosition = checkResult.queuePosition,
                            finished = checkResult.finished,
                            processing = checkResult.processing,
                        ),
                    )

                    if (checkResult.faulted || !checkResult.isPossible) {
                        val saved = runCatching {
                            repo.fetchFullStatusAndSave(job, partialFromCancel = true)
                        }.getOrDefault(0)
                        repo.updateJobRecord(
                            job.copy(
                                status = if (saved > 0) JobStatus.CANCELLED_PARTIAL.name else JobStatus.FAILED.name,
                                finished = saved,
                                waitTimeSeconds = 0,
                                errorMessage = "Request could not be completed",
                            ),
                        )
                        postResultNotification(
                            title = if (saved > 0) "Partial images saved" else "Generation failed",
                            body = if (saved > 0) "Saved $saved image(s) before stop." else "Workers could not process this request.",
                        )
                    } else if (checkResult.done) {
                        val saved = runCatching { repo.fetchFullStatusAndSave(job) }.getOrDefault(0)
                        repo.updateJobRecord(
                            job.copy(
                                status = JobStatus.COMPLETED.name,
                                finished = saved.coerceAtLeast(checkResult.finished),
                                processing = 0,
                                waitTimeSeconds = 0,
                            ),
                        )
                        postResultNotification(
                            title = "Saved $saved WebP image(s)",
                            body = job.modelsJoined.replace("||", ", "),
                        )
                    }
                }
                delay(2_000L)
            }
        }
    }

    private fun appContainer() = (application as HordecraftApp).container

    private fun buildProgressNotification(text: String): Notification {
        val openIntent = Intent(this, ImageGenActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_robot)
            .setContentTitle("Hordecraft")
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
        val galleryIntent = Intent(this, GalleryActivity::class.java)
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
        const val ACTION_START_OR_POLL = "net.hordecraft.android.action.START_POLL"
        const val ACTION_PAUSE = "net.hordecraft.android.action.PAUSE"
        const val ACTION_RESUME = "net.hordecraft.android.action.RESUME"
        const val ACTION_CANCEL_JOB = "net.hordecraft.android.action.CANCEL_JOB"
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
