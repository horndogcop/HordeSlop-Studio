package net.hordeslop.studio.domain

sealed interface GalleryGridEntry {
    val stableKey: String
    val createdAtMillis: Long

    data class Completed(val image: SavedImage) : GalleryGridEntry {
        override val stableKey: String = "img:${image.id}"
        override val createdAtMillis: Long = image.createdAtMillis
    }

    data class JobItem(val job: HordeJob) : GalleryGridEntry {
        override val stableKey: String = "job:${job.id}"
        override val createdAtMillis: Long = job.createdAtMillis
    }
}

data class GalleryPageAnchor(
    val anchorKey: String? = null,
    val fallbackKeys: List<String> = emptyList(),
    val fallbackIndex: Int = 0,
)

data class GalleryPageResult<T : GalleryGridEntry>(
    val items: List<T>,
    val pageNumber: Int,
    val totalPages: Int,
    val totalItems: Int,
    val startIndex: Int,
    val endIndex: Int,
    val anchorKey: String?,
) {
    val hasPrevious: Boolean get() = startIndex > 0
    val hasNext: Boolean get() = endIndex < totalItems
}

object GalleryPaginationEngine {
    const val DEFAULT_FALLBACK_WINDOW_SIZE: Int = 48

    val COMPARATOR: Comparator<GalleryGridEntry> =
        compareByDescending<GalleryGridEntry> { it.createdAtMillis }
            .thenBy { it.stableKey }

    fun <T : GalleryGridEntry> sortEntries(entries: List<T>): List<T> =
        entries.distinctBy { it.stableKey }.sortedWith(COMPARATOR)

    fun filterCompleted(
        images: List<SavedImage>,
        searchQuery: String,
        favoritesOnly: Boolean,
        filterState: GalleryFilterState,
    ): List<SavedImage> {
        if (!filterState.includesCompleteGenerations) {
            return emptyList()
        }
        return images.filter { img ->
            if (favoritesOnly && !img.isFavorite) return@filter false
            GallerySearchFilter.matches(img, searchQuery)
        }
    }

    fun filterJobs(
        jobs: List<HordeJob>,
        searchQuery: String,
        favoritesOnly: Boolean,
        filterState: GalleryFilterState,
    ): List<HordeJob> {
        if (filterState.includesCompleteGenerations && favoritesOnly) {
            return emptyList()
        }
        return jobs.filter { job ->
            filterState.matchesJob(job) && GallerySearchFilter.matchesJob(job, searchQuery)
        }
    }

    fun buildEntries(
        images: List<SavedImage>,
        jobs: List<HordeJob>,
        searchQuery: String,
        favoritesOnly: Boolean,
        filterState: GalleryFilterState,
    ): List<GalleryGridEntry> {
        val completed = filterCompleted(images, searchQuery, favoritesOnly, filterState)
            .map { GalleryGridEntry.Completed(it) }
        val jobItems = filterJobs(jobs, searchQuery, favoritesOnly, filterState)
            .map { GalleryGridEntry.JobItem(it) }
        return sortEntries(completed + jobItems)
    }

    fun calculateItemsPerPage(
        availableWidthDp: Float,
        availableHeightDp: Float,
        columns: Int,
        paginationBarHeightDp: Float = 0f,
        horizontalSpacingDp: Float = 8f,
        verticalSpacingDp: Float = 8f,
    ): Int {
        val safeColumns = columns.coerceIn(2, 6)
        val availableGridHeight = (availableHeightDp - paginationBarHeightDp).coerceAtLeast(1f)
        val totalHorizontalSpacing = horizontalSpacingDp * (safeColumns - 1)
        val itemWidth = ((availableWidthDp - totalHorizontalSpacing) / safeColumns).coerceAtLeast(1f)
        val rowHeight = itemWidth + verticalSpacingDp
        val rows = ((availableGridHeight + verticalSpacingDp) / rowHeight).toInt().coerceAtLeast(1)
        return (rows * safeColumns).coerceAtLeast(1)
    }

    fun <T : GalleryGridEntry> anchorAt(
        entries: List<T>,
        startIndex: Int,
        fallbackWindowSize: Int = DEFAULT_FALLBACK_WINDOW_SIZE,
    ): GalleryPageAnchor? {
        val sorted = sortEntries(entries)
        if (sorted.isEmpty()) return null
        val safeStart = startIndex.coerceIn(0, sorted.lastIndex)
        val anchorKey = sorted[safeStart].stableKey
        val fallbackEnd = (safeStart + 1 + fallbackWindowSize.coerceAtLeast(0)).coerceAtMost(sorted.size)
        val fallbackKeys = if (safeStart + 1 < fallbackEnd) {
            sorted.subList(safeStart + 1, fallbackEnd).map { it.stableKey }
        } else {
            emptyList()
        }
        return GalleryPageAnchor(
            anchorKey = anchorKey,
            fallbackKeys = fallbackKeys,
            fallbackIndex = safeStart,
        )
    }

    fun <T : GalleryGridEntry> paginateAnchored(
        entries: List<T>,
        pageSize: Int,
        anchor: GalleryPageAnchor?,
    ): GalleryPageResult<T> = paginate(
        entries = entries,
        pageSize = pageSize,
        anchorKey = anchor?.anchorKey,
        targetPage = null,
        fallbackIndex = anchor?.fallbackIndex ?: 0,
        fallbackKeys = anchor?.fallbackKeys ?: emptyList(),
    )

    fun <T : GalleryGridEntry> paginate(
        entries: List<T>,
        pageSize: Int,
        anchorKey: String? = null,
        targetPage: Int? = null,
        fallbackIndex: Int = 0,
        fallbackKeys: List<String> = emptyList(),
    ): GalleryPageResult<T> {
        val sorted = sortEntries(entries)
        val totalItems = sorted.size
        val safePageSize = pageSize.coerceAtLeast(1)
        val totalPages = if (totalItems == 0) 1 else ((totalItems + safePageSize - 1) / safePageSize).coerceAtLeast(1)

        if (totalItems == 0) {
            return GalleryPageResult(
                items = emptyList(),
                pageNumber = 1,
                totalPages = 1,
                totalItems = 0,
                startIndex = 0,
                endIndex = 0,
                anchorKey = null,
            )
        }

        val resolvedIndex: Int = when {
            targetPage != null -> {
                val clampedPage = targetPage.coerceIn(1, totalPages)
                (clampedPage - 1) * safePageSize
            }
            anchorKey != null -> {
                val foundIndex = sorted.indexOfFirst { it.stableKey == anchorKey }
                if (foundIndex >= 0) {
                    foundIndex
                } else {
                    val neighborIndex = if (fallbackKeys.isNotEmpty()) {
                        val keyToIndex = HashMap<String, Int>(sorted.size)
                        sorted.forEachIndexed { index, entry -> keyToIndex[entry.stableKey] = index }
                        fallbackKeys.firstNotNullOfOrNull { keyToIndex[it] }
                    } else {
                        null
                    }
                    when {
                        neighborIndex != null -> neighborIndex
                        fallbackIndex >= totalItems -> ((totalPages - 1) * safePageSize).coerceAtLeast(0)
                        else -> fallbackIndex.coerceIn(0, totalItems - 1)
                    }
                }
            }
            else -> {
                if (fallbackIndex >= totalItems) {
                    ((totalPages - 1) * safePageSize).coerceAtLeast(0)
                } else {
                    fallbackIndex.coerceIn(0, totalItems - 1)
                }
            }
        }

        val clampedIndex = resolvedIndex.coerceIn(0, totalItems - 1)
        val endIndex = (clampedIndex + safePageSize).coerceAtMost(totalItems)
        val pageItems = sorted.subList(clampedIndex, endIndex)
        val newAnchorKey = pageItems.firstOrNull()?.stableKey ?: sorted.firstOrNull()?.stableKey
        val pageNumber = (clampedIndex / safePageSize) + 1

        return GalleryPageResult(
            items = pageItems,
            pageNumber = pageNumber.coerceIn(1, totalPages),
            totalPages = totalPages,
            totalItems = totalItems,
            startIndex = clampedIndex,
            endIndex = endIndex,
            anchorKey = newAnchorKey,
        )
    }

    fun paginateFiltered(
        images: List<SavedImage>,
        jobs: List<HordeJob>,
        searchQuery: String,
        favoritesOnly: Boolean,
        filterState: GalleryFilterState,
        pageSize: Int,
        anchorKey: String? = null,
        targetPage: Int? = null,
        fallbackIndex: Int = 0,
        fallbackKeys: List<String> = emptyList(),
    ): GalleryPageResult<GalleryGridEntry> {
        val entries = buildEntries(
            images = images,
            jobs = jobs,
            searchQuery = searchQuery,
            favoritesOnly = favoritesOnly,
            filterState = filterState,
        )
        return paginate(
            entries = entries,
            pageSize = pageSize,
            anchorKey = anchorKey,
            targetPage = targetPage,
            fallbackIndex = fallbackIndex,
            fallbackKeys = fallbackKeys,
        )
    }
}
