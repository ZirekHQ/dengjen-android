package io.github.zirekhq.dengjen.tts

import kotlin.math.roundToInt

/**
 * Maps Android's speech rate and pitch (100 is normal) onto dengjen's 0-100 engine scales, the same
 * scales the SAPI and Speech Dispatcher frontends use.
 */
object Prosody {
    /** dengjen's speed runs from 0.5x at 0 to 5.5x at 100, so normal speed is 10. */
    private const val SPEED_MIN = 0.5f
    private const val SPEED_SPAN = 5.0f

    /** dengjen's pitch runs from 0.5x at 0 to 1.5x at 100, so normal pitch is 50. */
    private const val PITCH_MIN = 0.5f

    const val FULL_VOLUME = 100

    fun engineRate(androidRate: Int): Int {
        val speed = androidRate / 100f
        return percent((speed - SPEED_MIN) / SPEED_SPAN * 100f)
    }

    fun enginePitch(androidPitch: Int): Int {
        val factor = androidPitch / 100f
        return percent((factor - PITCH_MIN) * 100f)
    }

    private fun percent(value: Float): Int = value.roundToInt().coerceIn(0, 100)
}
