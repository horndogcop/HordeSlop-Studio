package net.hordeslop.studio.domain

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object SliderMath {
    const val STEPS_SLIDER_MAX = 50
    const val STEPS_MANUAL_MAX = 100
    const val CFG_SLIDER_MIN = 1.0f
    const val CFG_SLIDER_MAX = 20.0f
    const val CLIP_SKIP_MIN = 1
    const val CLIP_SKIP_MAX = 12
    const val STRENGTH_SLIDER_MIN = -5.0f
    const val STRENGTH_SLIDER_MAX = 5.0f

    // Logarithmic Steps slider: t in [0, 1] -> [1, 50], middle half [0.25, 0.75] maps 10..25
    fun sliderToSteps(t: Float): Int {
        val c = t.coerceIn(0f, 1f).toDouble()
        val raw = when {
            c <= 0.25 -> 1.0 * 10.0.pow(c / 0.25)
            c <= 0.75 -> 10.0 * 2.5.pow((c - 0.25) / 0.5)
            else -> 25.0 * 2.0.pow((c - 0.75) / 0.25)
        }
        return raw.roundToInt().coerceIn(1, STEPS_SLIDER_MAX)
    }

    fun stepsToSlider(steps: Int): Float {
        val s = steps.coerceIn(1, STEPS_SLIDER_MAX).toDouble()
        val res = when {
            s <= 10.0 -> 0.25 * (ln(s) / ln(10.0))
            s <= 25.0 -> 0.25 + 0.5 * (ln(s / 10.0) / ln(2.5))
            else -> 0.75 + 0.25 * (ln(s / 25.0) / ln(2.0))
        }
        return res.toFloat().coerceIn(0f, 1f)
    }

    // Logarithmic CFG slider: t in [0, 1] -> [1.0, 20.0], first 2/3 [0, 2/3] maps 1..6 (0.5 step)
    fun sliderToCfg(t: Float): Float {
        val c = t.coerceIn(0f, 1f).toDouble()
        val twoThirds = 2.0 / 3.0
        val raw = if (c <= twoThirds) {
            1.0 * 6.0.pow(c / twoThirds)
        } else {
            6.0 * (20.0 / 6.0).pow((c - twoThirds) / (1.0 / 3.0))
        }
        return ((raw * 2.0).roundToInt() / 2.0f).coerceAtLeast(1.0f)
    }

    fun cfgToSlider(cfg: Float): Float {
        val v = cfg.coerceIn(CFG_SLIDER_MIN, CFG_SLIDER_MAX).toDouble()
        val twoThirds = 2.0 / 3.0
        val res = if (v <= 6.0) {
            twoThirds * (ln(v) / ln(6.0))
        } else {
            twoThirds + (1.0 / 3.0) * (ln(v / 6.0) / ln(20.0 / 6.0))
        }
        return res.toFloat().coerceIn(0f, 1f)
    }

    // Logarithmic LoRA & Embedding strength slider:
    // t in [0, 1] -> [-5, 5], middle third [1/3, 2/3] maps 0..1 logarithmically, 0.05 step
    fun sliderToStrength(t: Float): Float {
        val c = t.coerceIn(0f, 1f).toDouble()
        val oneThird = 1.0 / 3.0
        val twoThirds = 2.0 / 3.0
        val raw = when {
            c < oneThird -> {
                val u = (oneThird - c) * 3.0
                -(6.0.pow(u) - 1.0)
            }
            c <= twoThirds -> {
                val u = (c - oneThird) * 3.0
                2.0.pow(u) - 1.0
            }
            else -> {
                val u = (c - twoThirds) * 3.0
                5.0.pow(u)
            }
        }
        return ((raw * 20.0).roundToInt() / 20.0f)
    }

    fun strengthToSlider(value: Float): Float {
        val v = value.coerceIn(STRENGTH_SLIDER_MIN, STRENGTH_SLIDER_MAX).toDouble()
        val oneThird = 1.0 / 3.0
        val twoThirds = 2.0 / 3.0
        val res = when {
            v < 0.0 -> {
                val u = ln(1.0 - v) / ln(6.0)
                oneThird - u / 3.0
            }
            v <= 1.0 -> {
                val u = ln(1.0 + v) / ln(2.0)
                oneThird + u / 3.0
            }
            else -> {
                val u = ln(v) / ln(5.0)
                twoThirds + u / 3.0
            }
        }
        return res.toFloat().coerceIn(0f, 1f)
    }

    fun stepsZone(steps: Int, suggestedMin: Int, suggestedMax: Int): SliderZone = when {
        steps > STEPS_SLIDER_MAX || steps < 1 -> SliderZone.RED
        steps < suggestedMin || steps > suggestedMax -> SliderZone.YELLOW
        else -> SliderZone.NORMAL
    }

    fun cfgZone(cfg: Float, suggestedMin: Float, suggestedMax: Float): SliderZone = when {
        cfg < CFG_SLIDER_MIN || cfg > CFG_SLIDER_MAX -> SliderZone.RED
        cfg < suggestedMin || cfg > suggestedMax -> SliderZone.YELLOW
        else -> SliderZone.NORMAL
    }

    fun clipSkipZone(clipSkip: Int): SliderZone = when {
        clipSkip < CLIP_SKIP_MIN || clipSkip > CLIP_SKIP_MAX -> SliderZone.RED
        clipSkip > 4 -> SliderZone.YELLOW
        else -> SliderZone.NORMAL
    }

    fun strengthZone(value: Float, suggestedMin: Float = 0.0f, suggestedMax: Float = 1.5f): SliderZone = when {
        value < STRENGTH_SLIDER_MIN || value > STRENGTH_SLIDER_MAX -> SliderZone.RED
        value < suggestedMin || value > suggestedMax -> SliderZone.YELLOW
        else -> SliderZone.NORMAL
    }
}
