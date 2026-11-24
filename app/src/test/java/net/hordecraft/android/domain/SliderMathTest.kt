package net.hordeslop.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderMathTest {
    @Test
    fun stepsSliderMapsMiddleHalfTo10Through25() {
        assertEquals(1, SliderMath.sliderToSteps(0f))
        assertEquals(10, SliderMath.sliderToSteps(0.25f))
        assertEquals(25, SliderMath.sliderToSteps(0.75f))
        assertEquals(50, SliderMath.sliderToSteps(1f))
        assertTrue(SliderMath.stepsToSlider(10) in 0.24f..0.26f)
        assertTrue(SliderMath.stepsToSlider(25) in 0.74f..0.76f)
    }

    @Test
    fun cfgSliderMapsFirstTwoThirdsTo1Through6() {
        assertEquals(1.0f, SliderMath.sliderToCfg(0f), 0.01f)
        assertEquals(6.0f, SliderMath.sliderToCfg(2f / 3f), 0.01f)
        assertEquals(20.0f, SliderMath.sliderToCfg(1f), 0.01f)
        assertTrue(SliderMath.cfgToSlider(6.0f) in 0.66f..0.67f)
    }

    @Test
    fun strengthSliderMapsMiddleThirdTo0Through1() {
        assertEquals(-5.0f, SliderMath.sliderToStrength(0f), 0.01f)
        assertEquals(0.0f, SliderMath.sliderToStrength(1f / 3f), 0.01f)
        assertEquals(1.0f, SliderMath.sliderToStrength(2f / 3f), 0.01f)
        assertEquals(5.0f, SliderMath.sliderToStrength(1f), 0.01f)
    }

    @Test
    fun colorZonesReflectSuggestedAndSliderBounds() {
        assertEquals(SliderZone.NORMAL, SliderMath.stepsZone(28, 20, 40))
        assertEquals(SliderZone.YELLOW, SliderMath.stepsZone(45, 20, 40))
        assertEquals(SliderZone.RED, SliderMath.stepsZone(75, 20, 40))
        assertEquals(SliderZone.RED, SliderMath.strengthZone(6.5f))
    }
}
