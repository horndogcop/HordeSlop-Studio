package net.hordeslop.studio.data.repository

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.hordeslop.studio.data.local.ActiveJobEntity
import net.hordeslop.studio.data.local.PromptHistoryEntity
import net.hordeslop.studio.domain.HordeStyle
import net.hordeslop.studio.domain.MultiModelDispatchMode
import net.hordeslop.studio.domain.StudioSubmissionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SubmissionQueueCoordinatorTest {

    private fun baseSpec(
        selectedModels: List<String> = listOf("Deliberate"),
        dispatchMode: MultiModelDispatchMode = MultiModelDispatchMode.COMBINED,
        styles: List<HordeStyle> = emptyList(),
        imageCount: Int = 1,
    ) = StudioSubmissionSpec(
        prompt = "alpine observatory",
        negativePrompt = "blurry",
        selectedModels = selectedModels,
        dispatchMode = dispatchMode,
        activeStyle = styles.firstOrNull(),
        activeStyles = styles,
        seed = "42",
        steps = 28,
        cfg = 7.0f,
        clipSkip = 1,
        width = 512,
        height = 768,
        sampler = "k_euler",
        scheduler = "normal",
        nsfw = false,
        censorNsfw = true,
        imageCount = imageCount,
        loras = emptyList(),
        tis = emptyList(),
    )

    private fun seqIdGen(): () -> String {
        val counter = AtomicInteger(0)
        return { "id${counter.incrementAndGet()}" }
    }

    @Test
    fun normalQueueingPersistsJobAndMatchingHistoryAtomically() = runTest {
        val plan = SubmissionQueueCoordinator.planStudioSubmission(
            spec = baseSpec(),
            nowMillis = 1_000L,
            idGenerator = seqIdGen(),
        )
        val persistedJobs = mutableListOf<ActiveJobEntity>()
        val persistedHistory = mutableListOf<PromptHistoryEntity>()

        SubmissionQueueCoordinator.persistAtomically(
            plan = plan,
            runInTransaction = { block -> block() },
            insertJobs = { persistedJobs.addAll(it) },
            insertHistory = { persistedHistory.addAll(it) },
        )

        assertEquals(1, persistedJobs.size)
        assertEquals(1, persistedHistory.size)
        assertEquals("alpine observatory", persistedHistory[0].prompt)
        assertEquals("blurry", persistedHistory[0].negativePrompt)
        assertEquals("Deliberate", persistedHistory[0].model)
        assertEquals(28, persistedHistory[0].steps)
    }

    @Test
    fun queuePersistenceFailureLeavesZeroHistoryEntries() = runTest {
        val plan = SubmissionQueueCoordinator.planStudioSubmission(
            spec = baseSpec(),
            nowMillis = 2_000L,
            idGenerator = seqIdGen(),
        )
        val committedJobs = mutableListOf<ActiveJobEntity>()
        val committedHistory = mutableListOf<PromptHistoryEntity>()

        suspend fun simulatedTransaction(block: suspend () -> Unit) {
            val stagedJobs = committedJobs.toMutableList()
            val stagedHistory = committedHistory.toMutableList()
            block()
            // Only committed if block completes without throwing; our insert lambdas write directly below
            // to prove insertHistory is never even reached when insertJobs throws.
            stagedJobs.clear()
            stagedHistory.clear()
        }

        try {
            SubmissionQueueCoordinator.persistAtomically(
                plan = plan,
                runInTransaction = ::simulatedTransaction,
                insertJobs = { throw IllegalStateException("Disk full") },
                insertHistory = { committedHistory.addAll(it) },
            )
            fail("Expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertEquals("Disk full", expected.message)
        }

        assertTrue(committedJobs.isEmpty())
        assertTrue(committedHistory.isEmpty())
    }

    @Test
    fun combinedMultiModelHistoryRecordsAllSelectedModelsNotJustFirst() {
        val plan = SubmissionQueueCoordinator.planStudioSubmission(
            spec = baseSpec(
                selectedModels = listOf("Deliberate", "Juggernaut XL"),
                dispatchMode = MultiModelDispatchMode.COMBINED,
            ),
            nowMillis = 3_000L,
            idGenerator = seqIdGen(),
        )

        assertEquals(1, plan.jobs.size)
        assertEquals("Deliberate||Juggernaut XL", plan.jobs[0].modelsJoined)
        assertEquals(1, plan.historyEntries.size)
        assertEquals("Deliberate, Juggernaut XL", plan.historyEntries[0].model)
    }

    @Test
    fun fanOutSubmissionRecordsOneHistoryEntryPerTargetModelAndDeduplicatesCountChunks() {
        val plan = SubmissionQueueCoordinator.planStudioSubmission(
            spec = baseSpec(
                selectedModels = listOf("Deliberate", "DreamShaper"),
                dispatchMode = MultiModelDispatchMode.FAN_OUT,
                imageCount = 25,
            ),
            nowMillis = 4_000L,
            idGenerator = seqIdGen(),
        )

        // 2 models * 2 count chunks (20 + 5) = 4 queued jobs
        assertEquals(4, plan.jobs.size)
        // Deduplicated by distinct request parameters -> 2 history entries (1 per fanned-out model)
        assertEquals(2, plan.historyEntries.size)
        assertEquals(listOf("Deliberate", "DreamShaper"), plan.historyEntries.map { it.model })
    }

    @Test
    fun multiStyleSubmissionRecordsExpandedPromptAndStyleOverridesPerStyle() {
        val styleA = HordeStyle(
            id = "s_a",
            name = "Painterly",
            source = "Repo",
            model = "Deliberate",
            promptTemplate = "{p}, oil painting",
            negativeTemplate = "{np}, photograph",
            rawPromptTemplate = "{p}, oil painting###{np}, photograph",
            steps = 30,
            cfg = 6.5f,
            sampler = "k_dpmpp_2m",
            scheduler = "karras",
        )
        val styleB = HordeStyle(
            id = "s_b",
            name = "Pixel",
            source = "Repo",
            model = "PixelMix",
            promptTemplate = "pixel art {p}",
            negativeTemplate = "{np}, smooth",
            rawPromptTemplate = "pixel art {p}###{np}, smooth",
            steps = 20,
            cfg = 8.0f,
            sampler = "k_euler_a",
            scheduler = "normal",
        )

        val plan = SubmissionQueueCoordinator.planStudioSubmission(
            spec = baseSpec(
                selectedModels = listOf("FallbackModel"),
                styles = listOf(styleA, styleB),
            ),
            nowMillis = 5_000L,
            idGenerator = seqIdGen(),
        )

        assertEquals(2, plan.jobs.size)
        assertEquals(2, plan.historyEntries.size)

        val hA = plan.historyEntries[0]
        assertEquals("Painterly", hA.title)
        assertEquals("alpine observatory, oil painting", hA.prompt)
        assertEquals("blurry, photograph", hA.negativePrompt)
        assertEquals("Deliberate", hA.model)
        assertEquals(30, hA.steps)
        assertEquals("k_dpmpp_2m", hA.sampler)

        val hB = plan.historyEntries[1]
        assertEquals("Pixel", hB.title)
        assertEquals("pixel art alpine observatory", hB.prompt)
        assertEquals("blurry, smooth", hB.negativePrompt)
        assertEquals("PixelMix", hB.model)
        assertEquals(20, hB.steps)
        assertEquals("k_euler_a", hB.sampler)
    }

    @Test
    fun rawSubmissionDerivesHistoryFromActualQueuedPayloads() {
        val json = Json { ignoreUnknownKeys = true }
        val payloads = listOf(
            """
            {
              "prompt": "edited dark prompt ### edited neg",
              "models": ["ModelOne", "ModelTwo"],
              "params": {
                "n": 2,
                "steps": 35,
                "cfg_scale": 5.5,
                "clip_skip": 2,
                "sampler_name": "dpmsolver",
                "scheduler": "sgm_uniform",
                "width": 768,
                "height": 768
              }
            }
            """.trimIndent(),
        )

        val plan = SubmissionQueueCoordinator.planRawSubmission(
            spec = baseSpec(),
            rawPayloadJsons = payloads,
            json = json,
            nowMillis = 6_000L,
            idGenerator = seqIdGen(),
        )

        assertEquals(1, plan.jobs.size)
        assertEquals(1, plan.historyEntries.size)
        val entry = plan.historyEntries[0]
        assertEquals("edited dark prompt", entry.prompt)
        assertEquals("edited neg", entry.negativePrompt)
        assertEquals("ModelOne, ModelTwo", entry.model)
        assertEquals(35, entry.steps)
        assertEquals(5.5f, entry.cfg)
        assertEquals(2, entry.clipSkip)
        assertEquals("dpmsolver", entry.sampler)
        assertEquals("sgm_uniform", entry.scheduler)
    }
}
