package net.hordecraft.android.domain

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AspectRatioPreset(val label: String, val wRatio: Int, val hRatio: Int)
data class MegapixelPreset(val label: String, val megapixels: Double)

object SizeMathEngine {
    val ASPECT_RATIO_PRESETS = listOf(
        AspectRatioPreset("1:1", 1, 1),
        AspectRatioPreset("3:4", 3, 4),
        AspectRatioPreset("2:3", 2, 3),
        AspectRatioPreset("16:9", 16, 9),
        AspectRatioPreset("21:9", 21, 9),
        AspectRatioPreset("2:1", 2, 1),
    )

    val MP_PRESETS = listOf(
        MegapixelPreset("0.25 MP", 0.25),
        MegapixelPreset("1 MP", 1.0),
        MegapixelPreset("2.5 MP", 2.5),
        MegapixelPreset("4 MP", 4.0),
    )

    fun snapTo64(value: Double): Int =
        (((value / 64.0).roundToInt()) * 64).coerceIn(64, 4096)

    fun snapTo64(value: Int): Int = snapTo64(value.toDouble())

    fun computeNearestSizeForRatioAndMp(
        wRatio: Int,
        hRatio: Int,
        megapixels: Double,
    ): Pair<Int, Int> {
        val safeW = wRatio.coerceAtLeast(1)
        val safeH = hRatio.coerceAtLeast(1)
        val targetPixels = megapixels * 1_000_000.0
        val idealWidth = sqrt((targetPixels * safeW) / safeH)
        val idealHeight = idealWidth * (safeH.toDouble() / safeW.toDouble())
        return snapTo64(idealWidth) to snapTo64(idealHeight)
    }

    fun formatAspectRatioLabel(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "1:1"
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val d = gcd(width, height)
        return "${width / d}:${height / d}"
    }

    fun formatMegapixels(width: Int, height: Int): String {
        val mp = (width.toLong() * height.toLong()) / 1_000_000.0
        return String.format(Locale.US, "%.2f MP", mp)
    }
}
