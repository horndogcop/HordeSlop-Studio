package net.hordeslop.studio.domain

import net.hordeslop.studio.data.remote.GenerationCheckDto
import net.hordeslop.studio.data.remote.GenerationStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationLifecycleClassifierTest {

    private fun progress(
        status: JobStatus = JobStatus.QUEUED,
        total: Int = 4,
        finished: Int = 1,
        processing: Int = 1,
        waitTimeSeconds: Int = 20,
        queuePosition: Int = 3,
        hasRequestId: Boolean = true,
        errorMessage: String? = null,
    ) = JobProgressState(
        status = status,
        total = total,
        finished = finished,
        processing = processing,
        waitTimeSeconds = waitTimeSeconds,
        queuePosition = queuePosition,
        hasRequestId = hasRequestId,
        errorMessage = errorMessage,
    )

    private fun job(
        status: JobStatus,
        requestId: String = "req-123",
        finished: Int = 1,
        total: Int = 4,
        waitTimeSeconds: Int = 12,
        queuePosition: Int = 2,
        errorMessage: String? = null,
    ) = HordeJob(
        id = requestId.ifBlank { "local_1" },
        requestId = requestId,
        prompt = "test prompt",
        negativePrompt = "",
        models = listOf("Deliberate"),
        status = status,
        waitTimeSeconds = waitTimeSeconds,
        queuePosition = queuePosition,
        finished = finished,
        processing = if (status == JobStatus.PROCESSING) 1 else 0,
        total = total,
        submittedPayloadJson = "{}",
        errorMessage = errorMessage,
        createdAtMillis = 10_000L,
    )

    @Test
    fun queuedAndWaitingImpossibleStayNonTerminalAndPreservePolling() {
        val queuedTransition = GenerationLifecycleClassifier.classify(
            current = progress(status = JobStatus.QUEUED, finished = 0, processing = 0),
            outcome = PollApiOutcome.Success(
                GenerationStatusSnapshot(
                    finished = 0,
                    processing = 0,
                    waiting = 2,
                    done = false,
                    faulted = false,
                    isPossible = true,
                    waitTimeSeconds = 18,
                    queuePosition = 5,
                ),
            ),
        )
        assertEquals(JobStatus.QUEUED, queuedTransition.nextStatus)
        assertFalse(queuedTransition.isTerminal)
        assertFalse(queuedTransition.shouldFetchFullStatus)
        assertTrue(queuedTransition.nextStatus.isRunnable)
        assertNull(queuedTransition.errorMessage)
        assertEquals(18, queuedTransition.waitTimeSeconds)
        assertEquals(5, queuedTransition.queuePosition)

        val impossibleTransition = GenerationLifecycleClassifier.classify(
            current = progress(status = JobStatus.QUEUED, finished = 0, processing = 0),
            outcome = PollApiOutcome.Success(
                GenerationStatusSnapshot(
                    finished = 0,
                    processing = 0,
                    waiting = 1,
                    done = false,
                    faulted = false,
                    isPossible = false,
                    waitTimeSeconds = 0,
                    queuePosition = 0,
                ),
            ),
        )
        assertEquals(JobStatus.WAITING_IMPOSSIBLE, impossibleTransition.nextStatus)
        assertFalse(impossibleTransition.isTerminal)
        assertFalse(impossibleTransition.shouldFetchFullStatus)
        assertTrue(impossibleTransition.nextStatus.isRunnable)
        assertTrue(impossibleTransition.nextStatus.isActiveInQueue)
        assertFalse(impossibleTransition.nextStatus.isError)
        assertFalse(impossibleTransition.errorMessage.isNullOrBlank())
    }

    @Test
    fun activeProcessingTakesPrecedenceOverContradictoryIsPossibleFalse() {
        val transition = GenerationLifecycleClassifier.classify(
            current = progress(status = JobStatus.WAITING_IMPOSSIBLE, finished = 1, processing = 0),
            outcome = PollApiOutcome.Success(
                GenerationStatusSnapshot(
                    finished = 2,
                    processing = 1,
                    done = false,
                    faulted = false,
                    isPossible = false,
                    waitTimeSeconds = 9,
                    queuePosition = 1,
                ),
            ),
        )
        assertEquals(JobStatus.PROCESSING, transition.nextStatus)
        assertEquals(2, transition.finished)
        assertEquals(1, transition.processing)
        assertNull(transition.errorMessage)
        assertFalse(transition.isTerminal)
        assertFalse(transition.shouldFetchFullStatus)
    }

    @Test
    fun transientFailureTransitionsToUnobservableAndPreservesExistingProgress() {
        val current = progress(
            status = JobStatus.PROCESSING,
            total = 4,
            finished = 2,
            processing = 1,
            waitTimeSeconds = 14,
            queuePosition = 2,
        )
        val transition = GenerationLifecycleClassifier.classify(
            current = current,
            outcome = PollApiOutcome.TransientFailure("HTTP 503 Service Unavailable", httpCode = 503),
        )
        assertEquals(JobStatus.UNOBSERVABLE, transition.nextStatus)
        assertFalse(transition.isTerminal)
        assertFalse(transition.shouldFetchFullStatus)
        assertTrue(transition.nextStatus.isRunnable)
        assertEquals(2, transition.finished)
        assertEquals(1, transition.processing)
        assertEquals(14, transition.waitTimeSeconds)
        assertEquals(2, transition.queuePosition)
        assertEquals("HTTP 503 Service Unavailable", transition.errorMessage)

        // Subsequent healthy poll recovers cleanly back to QUEUED
        val recovered = GenerationLifecycleClassifier.classify(
            current = current.copy(status = transition.nextStatus, errorMessage = transition.errorMessage),
            outcome = PollApiOutcome.Success(
                GenerationStatusSnapshot(
                    finished = 2,
                    processing = 0,
                    done = false,
                    faulted = false,
                    isPossible = true,
                    waitTimeSeconds = 8,
                    queuePosition = 1,
                ),
            ),
        )
        assertEquals(JobStatus.QUEUED, recovered.nextStatus)
        assertEquals(2, recovered.finished)
        assertNull(recovered.errorMessage)
    }

    @Test
    fun http404BecomesTerminalExpiredStateInsteadOfGenericFailureOrSilentDelete() {
        val noResultsExpired = GenerationLifecycleClassifier.classify(
            current = progress(status = JobStatus.WAITING_IMPOSSIBLE, total = 2, finished = 0, processing = 0),
            outcome = PollApiOutcome.NotFound,
        )
        assertEquals(JobStatus.EXPIRED, noResultsExpired.nextStatus)
        assertTrue(noResultsExpired.isTerminal)
        assertFalse(noResultsExpired.shouldFetchFullStatus)
        assertTrue(noResultsExpired.nextStatus.isError)
        assertEquals(0, noResultsExpired.finished)
        assertEquals("Request expired or was removed from API", noResultsExpired.errorMessage)

        val partialExpired = GenerationLifecycleClassifier.classify(
            current = progress(status = JobStatus.UNOBSERVABLE, total = 4, finished = 2, processing = 1),
            outcome = PollApiOutcome.NotFound,
        )
        assertEquals(JobStatus.EXPIRED, partialExpired.nextStatus)
        assertTrue(partialExpired.isTerminal)
        assertEquals(2, partialExpired.finished)
        assertEquals(0, partialExpired.processing)
        assertTrue(partialExpired.errorMessage!!.contains("2/4"))
    }

    @Test
    fun completionAndFaultRequireExplicitFlagsAndProtectMonotonicFinishedCount() {
        val doneTransition = GenerationLifecycleClassifier.classify(
            current = progress(status = JobStatus.PROCESSING, finished = 3, total = 4),
            outcome = PollApiOutcome.Success(
                GenerationStatusSnapshot(
                    finished = 2, // Stale lower finished count must not regress progress
                    processing = 0,
                    done = true,
                    faulted = false,
                ),
            ),
        )
        assertEquals(JobStatus.COMPLETED, doneTransition.nextStatus)
        assertEquals(3, doneTransition.finished)
        assertTrue(doneTransition.isTerminal)
        assertTrue(doneTransition.shouldFetchFullStatus)
        assertNull(doneTransition.errorMessage)

        val faultEmpty = GenerationLifecycleClassifier.classify(
            current = progress(status = JobStatus.QUEUED, finished = 0, total = 2),
            outcome = PollApiOutcome.Success(
                GenerationStatusSnapshot(finished = 0, done = true, faulted = true),
            ),
        )
        assertEquals(JobStatus.FAILED, faultEmpty.nextStatus)
        assertTrue(faultEmpty.isTerminal)
        assertTrue(faultEmpty.shouldFetchFullStatus)

        val faultPartial = GenerationLifecycleClassifier.classify(
            current = progress(status = JobStatus.PROCESSING, finished = 1, total = 2),
            outcome = PollApiOutcome.Success(
                GenerationStatusSnapshot(finished = 1, done = false, faulted = true),
            ),
        )
        assertEquals(JobStatus.CANCELLED_PARTIAL, faultPartial.nextStatus)
        assertTrue(faultPartial.isTerminal)
        assertTrue(faultPartial.shouldFetchFullStatus)
    }

    @Test
    fun terminalJobsNeverRegressOnSubsequentPolls() {
        val completed = progress(status = JobStatus.COMPLETED, total = 2, finished = 2, processing = 0)
        val transition = GenerationLifecycleClassifier.classify(
            current = completed,
            outcome = PollApiOutcome.NotFound,
        )
        assertEquals(JobStatus.COMPLETED, transition.nextStatus)
        assertEquals(2, transition.finished)
        assertTrue(transition.isTerminal)
        assertFalse(transition.shouldFetchFullStatus)
    }

    @Test
    fun dtoSnapshotsGalleryCategoriesAndUiClassificationStayConsistent() {
        val checkSnapshot = GenerationCheckDto(
            finished = 1,
            processing = 2,
            waiting = 1,
            done = false,
            faulted = false,
            waitTime = 25,
            queuePosition = 4,
            isPossible = true,
            eligibleWorkers = 3,
            eligibleWorkerThreads = 6,
            mightStall = true,
        ).toSnapshot()
        assertEquals(1, checkSnapshot.finished)
        assertEquals(2, checkSnapshot.processing)
        assertEquals(25, checkSnapshot.waitTimeSeconds)
        assertEquals(4, checkSnapshot.queuePosition)
        assertEquals(3, checkSnapshot.eligibleWorkers)
        assertEquals(6, checkSnapshot.eligibleWorkerThreads)
        assertTrue(checkSnapshot.mightStall)

        val statusSnapshot = GenerationStatusDto(
            finished = 2,
            done = true,
            isPossible = true,
            eligibleWorkers = 5,
            eligibleWorkerThreads = 8,
        ).toSnapshot()
        assertEquals(2, statusSnapshot.finished)
        assertTrue(statusSnapshot.done)
        assertEquals(5, statusSnapshot.eligibleWorkers)
        assertEquals(8, statusSnapshot.eligibleWorkerThreads)

        assertEquals(
            GalleryStatusCategory.SENT_TO_SERVER,
            JobStatus.WAITING_IMPOSSIBLE.galleryCategory(hasRequestId = true),
        )
        assertEquals(
            GalleryStatusCategory.SENT_TO_SERVER,
            JobStatus.UNOBSERVABLE.galleryCategory(hasRequestId = true),
        )
        assertEquals(
            GalleryStatusCategory.ERRORS,
            JobStatus.EXPIRED.galleryCategory(hasRequestId = true),
        )
        assertEquals(JobStatus.QUEUED, JobStatus.fromStorage("UNKNOWN_FUTURE_STATUS"))
        assertEquals(JobStatus.WAITING_IMPOSSIBLE, JobStatus.fromStorage("WAITING_IMPOSSIBLE"))

        val impossibleUi = GenerationLifecycleClassifier.classifyUi(
            job(JobStatus.WAITING_IMPOSSIBLE, errorMessage = "No eligible worker currently available (still checking)"),
        )
        assertTrue(impossibleUi.isActive)
        assertTrue(impossibleUi.isRunnable)
        assertFalse(impossibleUi.isTerminal)
        assertFalse(impossibleUi.isError)
        assertEquals("No worker", impossibleUi.badgeLabel)

        val unobservableUi = GenerationLifecycleClassifier.classifyUi(
            job(JobStatus.UNOBSERVABLE, errorMessage = "HTTP 502"),
        )
        assertTrue(unobservableUi.isActive)
        assertTrue(unobservableUi.isRunnable)
        assertFalse(unobservableUi.isTerminal)
        assertEquals("Retrying", unobservableUi.badgeLabel)

        val expiredUi = GenerationLifecycleClassifier.classifyUi(
            job(JobStatus.EXPIRED, errorMessage = "Request expired or was removed from API"),
        )
        assertFalse(expiredUi.isActive)
        assertFalse(expiredUi.isRunnable)
        assertTrue(expiredUi.isTerminal)
        assertTrue(expiredUi.isError)
        assertEquals("Expired", expiredUi.badgeLabel)
        assertEquals("Request Expired", expiredUi.dialogTitle)
    }
}
