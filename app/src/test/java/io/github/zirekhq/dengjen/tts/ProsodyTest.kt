package io.github.zirekhq.dengjen.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class ProsodyTest {
    @Test
    fun normalRateIsTheEnginesNormalSpeed() = assertEquals(10, Prosody.engineRate(100))

    @Test
    fun doubleRateMapsToTwiceTheSpeed() = assertEquals(30, Prosody.engineRate(200))

    @Test
    fun ratesBelowHalfSpeedClampToTheSlowest() {
        assertEquals(0, Prosody.engineRate(50))
        assertEquals(0, Prosody.engineRate(10))
    }

    @Test
    fun ratesAboveTheEnginesTopSpeedClamp() = assertEquals(100, Prosody.engineRate(600))

    @Test
    fun normalPitchIsTheEnginesMiddle() = assertEquals(50, Prosody.enginePitch(100))

    @Test
    fun pitchFollowsTheFactorAndClamps() {
        assertEquals(75, Prosody.enginePitch(125))
        assertEquals(0, Prosody.enginePitch(25))
        assertEquals(100, Prosody.enginePitch(200))
    }
}
