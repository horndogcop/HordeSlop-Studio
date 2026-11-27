package net.hordeslop.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPaginationEngineTest {

    private fun completedImage(
        id: String,
        createdAtMillis: Long,
        prompt: String = "test prompt $id",
        isFavorite: Boolean = false,
    ): SavedImage = SavedImage(
        id = id,
        requestId = "req-$id",
        generationId = "gen-$id",
        fileUri = "content://media/$id.webp",
        prompt = prompt,
        negativePrompt = "",
        model = "Deliberate",
        seed = "12345",
        steps = 20,
        cfg = 7.0f,
        clipSkip = 1,
        sampler = "k_euler",
        scheduler = "normal",
        width = 512,
        height = 512,
        destinationLabel = "Downloads",
        isFavorite = isFavorite,
        partialFromCancel = false,
        requestJson = "{}",
        statusMetadataJson = "{}",
        createdAtMillis = createdAtMillis,
    )

    private fun queuedJob(
        id: String,
        createdAtMillis: Long,
        status: JobStatus = JobStatus.QUEUED,
        prompt: String = "queued prompt $id",
        requestId: String = "srv-$id",
    ): HordeJob = HordeJob(
        id = id,
        requestId = requestId,
        prompt = prompt,
        negativePrompt = "",
        models = listOf("Deliberate"),
        status = status,
        waitTimeSeconds = 10,
        queuePosition = 2,
        finished = 0,
        processing = 0,
        total = 1,
        submittedPayloadJson = "{}",
        errorMessage = null,
        createdAtMillis = createdAtMillis,
    )

    @Test
    fun sortOrderUsesDescendingTimestampAndStableKeyTieBreakerWithoutDuplicates() {
        val imgA = GalleryGridEntry.Completed(completedImage("a", 1_000L))
        val imgB = GalleryGridEntry.Completed(completedImage("b", 2_000L))
        val imgC = GalleryGridEntry.Completed(completedImage("c", 1_000L))
        val job1 = GalleryGridEntry.JobItem(queuedJob("1", 1_000L))
        val imgADup = GalleryGridEntry.Completed(completedImage("a", 1_000L))

        val sorted = GalleryPaginationEngine.sortEntries(listOf(job1, imgC, imgA, imgB, imgADup))

        assertEquals(listOf("img:b", "img:a", "img:c", "job:1"), sorted.map { it.stableKey })
        assertEquals(sorted.size, sorted.map { it.stableKey }.distinct().size)
    }

    @Test
    fun calculateItemsPerPageFitsWholeRowsForScreenAndColumns() {
        // availableWidth = 400dp, availableHeight = 800dp, columns = 4
        // gridHeight = 800 - 52 - 8 = 740dp, spacing = 8dp * 3 = 24dp -> itemWidth = 94dp
        // rowHeight = 102dp -> rows = (740 + 8) / 102 = 7 rows * 4 cols = 28 items
        assertEquals(
            28,
            GalleryPaginationEngine.calculateItemsPerPage(
                availableWidthDp = 400f,
                availableHeightDp = 800f,
                columns = 4,
            ),
        )
        // Minimum fallback on tiny layout
        assertEquals(
            2,
            GalleryPaginationEngine.calculateItemsPerPage(
                availableWidthDp = 100f,
                availableHeightDp = 40f,
                columns = 2,
            ),
        )
    }

    @Test
    fun filteredPageBoundariesApplyCategoryFavoritesAndSearchBeforePaginating() {
        val images = (1..10).map { idx ->
            completedImage(
                id = "img_$idx",
                createdAtMillis = idx * 100L,
                prompt = if (idx % 2 == 0) "cyberpunk city $idx" else "forest cabin $idx",
                isFavorite = idx >= 6,
            )
        }
        val jobs = listOf(
            queuedJob("j1", 1_500L, status = JobStatus.LOCAL_QUEUED, prompt = "cyberpunk neon", requestId = ""),
            queuedJob("j2", 1_200L, status = JobStatus.QUEUED, prompt = "cyberpunk rain", requestId = "srv-2"),
            queuedJob("j3", 1_100L, status = JobStatus.FAILED, prompt = "cyberpunk crash", requestId = ""),
        )

        // Filter: COMPLETE_GENERATIONS + SENT_TO_SERVER, favoritesOnly = false, search = "cyberpunk"
        val filterState = GalleryFilterState(
            setOf(GalleryStatusCategory.COMPLETE_GENERATIONS, GalleryStatusCategory.SENT_TO_SERVER),
        )
        // Matching images: img_10, img_8, img_6, img_4, img_2 (5). Matching job: j2 (1). Total = 6.
        val page1 = GalleryPaginationEngine.paginateFiltered(
            images = images,
            jobs = jobs,
            searchQuery = "cyberpunk",
            favoritesOnly = false,
            filterState = filterState,
            pageSize = 4,
            targetPage = 1,
        )
        assertEquals(6, page1.totalItems)
        assertEquals(2, page1.totalPages)
        assertEquals(listOf("job:j2", "img:img_10", "img:img_8", "img:img_6"), page1.items.map { it.stableKey })

        // With favoritesOnly = true, jobs are excluded even if SENT_TO_SERVER is enabled, matching only fav cyberpunk images: img_10, img_8, img_6
        val favPage = GalleryPaginationEngine.paginateFiltered(
            images = images,
            jobs = jobs,
            searchQuery = "cyberpunk",
            favoritesOnly = true,
            filterState = filterState,
            pageSize = 2,
            targetPage = 2,
        )
        assertEquals(3, favPage.totalItems)
        assertEquals(2, favPage.totalPages)
        assertEquals(listOf("img:img_6"), favPage.items.map { it.stableKey })
    }

    @Test
    fun fullPaginationTraversalYieldsEveryMatchingItemExactlyOnce() {
        val entries = (1..17).map { idx ->
            GalleryGridEntry.Completed(completedImage("item_$idx", createdAtMillis = idx * 100L))
        } + (1..6).map { idx ->
            GalleryGridEntry.JobItem(queuedJob("job_$idx", createdAtMillis = idx * 150L))
        }

        val collectedKeys = mutableListOf<String>()
        var nextIndex = 0
        var anchor: String? = null
        val sortedAll = GalleryPaginationEngine.sortEntries(entries)

        while (true) {
            val page = GalleryPaginationEngine.paginate(
                entries = entries,
                pageSize = 5,
                anchorKey = anchor,
                fallbackIndex = nextIndex,
            )
            collectedKeys += page.items.map { it.stableKey }
            if (!page.hasNext) break
            nextIndex = page.endIndex
            anchor = sortedAll[nextIndex].stableKey
        }

        assertEquals(sortedAll.map { it.stableKey }, collectedKeys)
        assertEquals(collectedKeys.size, collectedKeys.distinct().size)
    }

    @Test
    fun incomingGenerationsDoNotAffectDisplayedImagesOnCurrentPage() {
        val initial = (1..12).map { GalleryGridEntry.Completed(completedImage("img_$it", it * 100L)) }
        val page2Before = GalleryPaginationEngine.paginate(
            entries = initial,
            pageSize = 4,
            targetPage = 2,
        )
        val displayedKeysBefore = page2Before.items.map { it.stableKey }

        // 3 new completed generations arrive with higher timestamps
        val incoming = (13..15).map { GalleryGridEntry.Completed(completedImage("img_$it", it * 100L)) }
        val page2After = GalleryPaginationEngine.paginate(
            entries = initial + incoming,
            pageSize = 4,
            anchorKey = page2Before.anchorKey,
            fallbackIndex = page2Before.startIndex,
        )

        assertEquals(displayedKeysBefore, page2After.items.map { it.stableKey })
        assertTrue(page2After.hasPrevious)
    }

    @Test
    fun deletionsDoNotPullPrecedingItemsAndClampTrailingPages() {
        val entries = (1..10).map { GalleryGridEntry.Completed(completedImage("img_$it", it * 100L)) }
        // Descending order: img_10, img_9, img_8, img_7, img_6, img_5, img_4, img_3, img_2, img_1
        // Page 2 (size 4): img_6, img_5, img_4, img_3 (startIndex = 4)
        val page2 = GalleryPaginationEngine.paginate(entries = entries, pageSize = 4, targetPage = 2)
        assertEquals(listOf("img:img_6", "img:img_5", "img:img_4", "img:img_3"), page2.items.map { it.stableKey })

        // Delete non-anchor item img_5 -> img_10..img_7 stay off screen; page stays anchored at img_6
        val withoutImg5 = entries.filterNot { it.image.id == "img_5" }
        val afterNonAnchorDelete = GalleryPaginationEngine.paginate(
            entries = withoutImg5,
            pageSize = 4,
            anchorKey = page2.anchorKey,
            fallbackIndex = page2.startIndex,
        )
        assertEquals(listOf("img:img_6", "img:img_4", "img:img_3", "img:img_2"), afterNonAnchorDelete.items.map { it.stableKey })

        // Delete anchor item img_6 -> fallbackIndex (4) lands cleanly on img_5 without pulling img_7
        val withoutImg6 = entries.filterNot { it.image.id == "img_6" }
        val afterAnchorDelete = GalleryPaginationEngine.paginate(
            entries = withoutImg6,
            pageSize = 4,
            anchorKey = page2.anchorKey,
            fallbackIndex = page2.startIndex,
        )
        assertEquals(listOf("img:img_5", "img:img_4", "img:img_3", "img:img_2"), afterAnchorDelete.items.map { it.stableKey })

        // Last page (size 4): img_2, img_1 (startIndex = 8). Delete both -> clamps to page 2 (img_6..img_3)
        val lastPage = GalleryPaginationEngine.paginate(entries = entries, pageSize = 4, targetPage = 3)
        val withoutLastTwo = entries.filterNot { it.image.id in setOf("img_2", "img_1") }
        val afterTrailingDelete = GalleryPaginationEngine.paginate(
            entries = withoutLastTwo,
            pageSize = 4,
            anchorKey = lastPage.anchorKey,
            fallbackIndex = lastPage.startIndex,
        )
        assertEquals(2, afterTrailingDelete.pageNumber)
        assertEquals(listOf("img:img_6", "img:img_5", "img:img_4", "img:img_3"), afterTrailingDelete.items.map { it.stableKey })
        assertFalse(afterTrailingDelete.hasNext)
    }

    @Test
    fun columnAndPageSizeChangesKeepDisplayedAnchorAndResizeItemCount() {
        val entries = (1..20).map { GalleryGridEntry.Completed(completedImage("img_$it", it * 100L)) }
        val page2 = GalleryPaginationEngine.paginate(entries = entries, pageSize = 4, targetPage = 2)
        // Page 2 starts at img_16
        assertEquals("img:img_16", page2.anchorKey)

        // Increase pageSize from 4 -> 8 (e.g., more columns): starts at img_16 and shows 8 items
        val expanded = GalleryPaginationEngine.paginate(
            entries = entries,
            pageSize = 8,
            anchorKey = page2.anchorKey,
            fallbackIndex = page2.startIndex,
        )
        assertEquals("img:img_16", expanded.items.first().stableKey)
        assertEquals(8, expanded.items.size)
        assertEquals(page2.items.map { it.stableKey }, expanded.items.take(4).map { it.stableKey })

        // Decrease pageSize from 8 -> 3 (e.g., fewer columns): still starts at img_16 and shows 3 items
        val shrunk = GalleryPaginationEngine.paginate(
            entries = entries,
            pageSize = 3,
            anchorKey = expanded.anchorKey,
            fallbackIndex = expanded.startIndex,
        )
        assertEquals("img:img_16", shrunk.items.first().stableKey)
        assertEquals(3, shrunk.items.size)
    }

    @Test
    fun emptyOutOfRangeAndFilterChangesOnLaterPageAreHandledPredictably() {
        val emptyPage = GalleryPaginationEngine.paginate(
            entries = emptyList<GalleryGridEntry.Completed>(),
            pageSize = 6,
            targetPage = 4,
        )
        assertEquals(0, emptyPage.totalItems)
        assertEquals(1, emptyPage.totalPages)
        assertEquals(1, emptyPage.pageNumber)
        assertNull(emptyPage.anchorKey)
        assertTrue(emptyPage.items.isEmpty())
        assertFalse(emptyPage.hasPrevious)
        assertFalse(emptyPage.hasNext)

        val images = (1..12).map {
            completedImage("img_$it", it * 100L, isFavorite = it <= 2)
        }
        val entries = images.map { GalleryGridEntry.Completed(it) }

        val clampedHigh = GalleryPaginationEngine.paginate(entries = entries, pageSize = 5, targetPage = 99)
        assertEquals(3, clampedHigh.pageNumber)
        assertEquals(2, clampedHigh.items.size)

        val clampedLow = GalleryPaginationEngine.paginate(entries = entries, pageSize = 5, targetPage = -3)
        assertEquals(1, clampedLow.pageNumber)
        assertEquals(5, clampedLow.items.size)

        // Simulate filter change while on page 3: resets anchor/fallback to page 1 cleanly
        val filteredAfterChange = GalleryPaginationEngine.paginateFiltered(
            images = images,
            jobs = emptyList(),
            searchQuery = "",
            favoritesOnly = true,
            filterState = GalleryFilterState(),
            pageSize = 5,
            anchorKey = null,
            fallbackIndex = 0,
        )
        assertEquals(1, filteredAfterChange.pageNumber)
        assertEquals(1, filteredAfterChange.totalPages)
        assertEquals(2, filteredAfterChange.totalItems)
        assertEquals(listOf("img:img_2", "img:img_1"), filteredAfterChange.items.map { it.stableKey })
    }
}
