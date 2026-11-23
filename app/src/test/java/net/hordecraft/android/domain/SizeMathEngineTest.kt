package net.hordecraft.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeMathEngineTest {
    @Test
    fun snapTo64EnforcesValidMultiples() {
        assertEquals(64, SizeMathEngine.snapTo64(10))
        assertEquals(1024, SizeMathEngine.snapTo64(1020))
        assertEquals(512, SizeMathEngine.snapTo64(512))
        assertEquals(4096, SizeMathEngine.snapTo64(8000))
    }

    @Test
    fun computesOneMegapixelSizesCleanly() {
        val (w1, h1) = SizeMathEngine.computeNearestSizeForRatioAndMp(1, 1, 1.0)
        assertEquals(1024, w1)
        assertEquals(1024, h1)

        val (w2, h2) = SizeMathEngine.computeNearestSizeForRatioAndMp(16, 9, 1.0)
        assertEquals(0, w2 % 64)
        assertEquals(0, h2 % 64)
    }

    @Test
    fun formatsReducedAspectRatioLabel() {
        assertEquals("1:1", SizeMathEngine.formatAspectRatioLabel(1024, 1024))
        assertEquals("2:3", SizeMathEngine.formatAspectRatioLabel(512, 768))
    }
}
