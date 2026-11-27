package net.hordeslop.studio.domain

data class GenerationStatusSnapshot(
    val finished: Int = 0,
    val processing: Int = 0,
    val restarted: Int = 0,
    val waiting: Int = 0,
    val done: Boolean = false,
    val faulted: Boolean = false,
    val isPossible: Boolean = true,
    val waitTimeSeconds: Int = 0,
    val queuePosition: Int = 0,
    val kudos: Double = 0.0,
    val eligibleWorkers: Int = 0,
    val eligibleWorkerThreads: Int = 0,
    val mightStall: Boolean = false,
)

sealed interface PollApiOutcome {
    data class Success(val snapshot: GenerationStatusSnapshot) : PollApiOutcome
    data object NotFound : PollApiOutcome
    data class TransientFailure(
        val message: String,
        val httpCode: Int? = null,
    ) : PollApiOutcome
}

data class JobProgressState(
    val status: JobStatus,
    val total: Int,
    val finished: Int,
    val processing: Int,
    val waitTimeSeconds: Int,
    val queuePosition: Int,
    val hasRequestId: Boolean,
    val errorMessage: String? = null,
)

data class ClassifiedJobTransition(
    val nextStatus: JobStatus,
    val finished: Int,
    val processing: Int,
    val waitTimeSeconds: Int,
    val queuePosition: Int,
    val errorMessage: String?,
    val shouldFetchFullStatus: Boolean,
    val isTerminal: Boolean,
)

data class GenerationUiLifecycle(
    val status: JobStatus,
    val isActive: Boolean,
    val isRunnable: Boolean,
    val isTerminal: Boolean,
    val isError: Boolean,
    val isLocal: Boolean,
    val badgeLabel: String,
    val shortStatusText: String,
    val detailStatusText: String,
    val dialogTitle: String,
)

object GenerationLifecycleClassifier {

    fun classify(
        current: JobProgressState,
        outcome: PollApiOutcome,
    ): ClassifiedJobTransition {
        if (current.status.isTerminal) {
            return ClassifiedJobTransition(
                nextStatus = current.status,
                finished = current.finished,
                processing = 0,
                waitTimeSeconds = 0,
                queuePosition = 0,
                errorMessage = current.errorMessage,
                shouldFetchFullStatus = false,
                isTerminal = true,
            )
        }

        return when (outcome) {
            is PollApiOutcome.NotFound -> {
                val msg = if (current.finished > 0) {
                    "Request expired from API (${current.finished}/${current.total} saved)"
                } else {
                    "Request expired or was removed from API"
                }
                ClassifiedJobTransition(
                    nextStatus = JobStatus.EXPIRED,
                    finished = current.finished,
                    processing = 0,
                    waitTimeSeconds = 0,
                    queuePosition = 0,
                    errorMessage = msg,
                    shouldFetchFullStatus = false,
                    isTerminal = true,
                )
            }

            is PollApiOutcome.TransientFailure -> {
                ClassifiedJobTransition(
                    nextStatus = JobStatus.UNOBSERVABLE,
                    finished = current.finished,
                    processing = current.processing,
                    waitTimeSeconds = current.waitTimeSeconds,
                    queuePosition = current.queuePosition,
                    errorMessage = outcome.message.ifBlank { "Temporarily unable to reach API" },
                    shouldFetchFullStatus = false,
                    isTerminal = false,
                )
            }

            is PollApiOutcome.Success -> classifySnapshot(current, outcome.snapshot)
        }
    }

    private fun classifySnapshot(
        current: JobProgressState,
        snapshot: GenerationStatusSnapshot,
    ): ClassifiedJobTransition {
        val safeFinished = maxOf(current.finished, snapshot.finished.coerceAtLeast(0))
        val safeProcessing = snapshot.processing.coerceAtLeast(0)
        val safeWait = snapshot.waitTimeSeconds.coerceAtLeast(0)
        val safeQueuePos = snapshot.queuePosition.coerceAtLeast(0)

        if (snapshot.faulted) {
            val terminalStatus = if (safeFinished > 0) JobStatus.CANCELLED_PARTIAL else JobStatus.FAILED
            return ClassifiedJobTransition(
                nextStatus = terminalStatus,
                finished = safeFinished,
                processing = 0,
                waitTimeSeconds = 0,
                queuePosition = 0,
                errorMessage = "Request faulted on AI Horde",
                shouldFetchFullStatus = true,
                isTerminal = true,
            )
        }

        if (snapshot.done) {
            return ClassifiedJobTransition(
                nextStatus = JobStatus.COMPLETED,
                finished = safeFinished,
                processing = 0,
                waitTimeSeconds = 0,
                queuePosition = 0,
                errorMessage = null,
                shouldFetchFullStatus = true,
                isTerminal = true,
            )
        }

        // Non-terminal active states (done == false && faulted == false).
        // Actively processing takes precedence over contradictory isPossible=false flags.
        return when {
            safeProcessing > 0 -> ClassifiedJobTransition(
                nextStatus = JobStatus.PROCESSING,
                finished = safeFinished,
                processing = safeProcessing,
                waitTimeSeconds = safeWait,
                queuePosition = safeQueuePos,
                errorMessage = null,
                shouldFetchFullStatus = false,
                isTerminal = false,
            )

            !snapshot.isPossible -> ClassifiedJobTransition(
                nextStatus = JobStatus.WAITING_IMPOSSIBLE,
                finished = safeFinished,
                processing = 0,
                waitTimeSeconds = safeWait,
                queuePosition = safeQueuePos,
                errorMessage = "No eligible worker currently available (still checking)",
                shouldFetchFullStatus = false,
                isTerminal = false,
            )

            else -> ClassifiedJobTransition(
                nextStatus = JobStatus.QUEUED,
                finished = safeFinished,
                processing = 0,
                waitTimeSeconds = safeWait,
                queuePosition = safeQueuePos,
                errorMessage = null,
                shouldFetchFullStatus = false,
                isTerminal = false,
            )
        }
    }

    fun classifyUi(job: HordeJob): GenerationUiLifecycle {
        val status = job.status
        val isLocal = status == JobStatus.LOCAL_QUEUED ||
            (status == JobStatus.PAUSED && job.requestId.isBlank())

        val badgeLabel = when (status) {
            JobStatus.LOCAL_QUEUED -> "Local"
            JobStatus.PAUSED -> if (job.requestId.isBlank()) "Paused (local)" else "Paused"
            JobStatus.QUEUED -> "Sent"
            JobStatus.WAITING_IMPOSSIBLE -> "No worker"
            JobStatus.PROCESSING -> if (job.waitTimeSeconds > 0) "~${job.waitTimeSeconds}s" else "Running"
            JobStatus.UNOBSERVABLE -> "Retrying"
            JobStatus.COMPLETED -> "Done"
            JobStatus.CANCELLED_PARTIAL -> "Partial"
            JobStatus.FAILED -> "Error"
            JobStatus.EXPIRED -> "Expired"
        }

        val shortStatusText = when (status) {
            JobStatus.LOCAL_QUEUED -> "Queued locally"
            JobStatus.QUEUED -> if (job.queuePosition > 0 || job.waitTimeSeconds > 0) {
                "Queued · #${job.queuePosition} · ~${job.waitTimeSeconds}s"
            } else {
                "Queued"
            }
            JobStatus.WAITING_IMPOSSIBLE -> "Waiting for eligible worker"
            JobStatus.PROCESSING -> if (job.waitTimeSeconds > 0) {
                "Wait ~${job.waitTimeSeconds}s · #${job.queuePosition}"
            } else {
                "Processing"
            }
            JobStatus.PAUSED -> "Paused"
            JobStatus.UNOBSERVABLE -> job.errorMessage?.ifBlank { null } ?: "Temporarily unreachable (retrying)"
            JobStatus.COMPLETED -> "Completed"
            JobStatus.CANCELLED_PARTIAL -> "Partial"
            JobStatus.FAILED -> job.errorMessage?.ifBlank { null } ?: "Failed"
            JobStatus.EXPIRED -> job.errorMessage?.ifBlank { null } ?: "Expired from API"
        }

        val detailStatusText = when (status) {
            JobStatus.LOCAL_QUEUED -> "Queued locally (awaiting dispatch)"
            JobStatus.QUEUED -> if (job.queuePosition > 0 || job.waitTimeSeconds > 0) {
                "Sent to server (#${job.queuePosition}, ~${job.waitTimeSeconds}s)"
            } else {
                "Sent to server (queued)"
            }
            JobStatus.WAITING_IMPOSSIBLE -> "Waiting on server (no eligible worker yet)"
            JobStatus.PROCESSING -> "Processing (#${job.queuePosition}, ~${job.waitTimeSeconds}s)"
            JobStatus.PAUSED -> if (job.requestId.isBlank()) "Paused (local)" else "Paused (server)"
            JobStatus.UNOBSERVABLE -> "Temporarily unreachable (keeping job active)"
            JobStatus.COMPLETED -> "Completed"
            JobStatus.CANCELLED_PARTIAL -> "Stopped early (${job.finished}/${job.total})"
            JobStatus.FAILED -> "Error"
            JobStatus.EXPIRED -> "Expired / removed from API"
        }

        val dialogTitle = when (status) {
            JobStatus.FAILED -> "Generation Error"
            JobStatus.EXPIRED -> "Request Expired"
            else -> "Queued Generation"
        }

        return GenerationUiLifecycle(
            status = status,
            isActive = status.isActiveInQueue,
            isRunnable = status.isRunnable,
            isTerminal = status.isTerminal,
            isError = status.isError,
            isLocal = isLocal,
            badgeLabel = badgeLabel,
            shortStatusText = shortStatusText,
            detailStatusText = detailStatusText,
            dialogTitle = dialogTitle,
        )
    }
}
