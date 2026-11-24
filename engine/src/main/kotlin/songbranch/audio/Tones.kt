// Birdsong synthesized in code, like the Reddit version: each dawn wave climbs a pentatonic scale
// and a full chorus ends on a chord. Produces 16-bit mono PCM so the app needs no audio files.
package songbranch.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object Tones {
    const val SAMPLE_RATE = 44_100

    /** Major pentatonic steps in semitones above the root. */
    private val PENTATONIC = intArrayOf(0, 2, 4, 7, 9)
    /** Root note A4 = 440 Hz moved up to D5 for a brighter, bird-like register. */
    private const val ROOT_HZ = 587.33

    /** Frequency of the [step]-th note of the pentatonic scale (step 0 = root, wraps by octave). */
    fun pentatonicHz(step: Int): Double {
        val s = step.coerceAtLeast(0)
        val semis = PENTATONIC[s % PENTATONIC.size] + 12 * (s / PENTATONIC.size)
        return ROOT_HZ * 2.0.pow(semis / 12.0)
    }

    /**
     * A short chirp: a sine with a quick upward glide, a soft attack and an exponential decay.
     * [durationMs] is clamped to 40..1200 ms; [gain] to 0..1.
     */
    fun chirp(hz: Double, durationMs: Int = 260, gain: Double = 0.6): ShortArray {
        val ms = durationMs.coerceIn(40, 1200)
        val g = gain.coerceIn(0.0, 1.0)
        val n = SAMPLE_RATE * ms / 1000
        val out = ShortArray(n)
        val attack = (SAMPLE_RATE * 0.012).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val glide = 1.0 + 0.06 * (1.0 - exp(-t * 40.0))        // rises 6% in the first ~50 ms
            phase += 2.0 * PI * hz * glide / SAMPLE_RATE
            val env = min(1.0, i.toDouble() / attack) * exp(-t * 7.5)
            val sample = (sin(phase) + 0.18 * sin(2.0 * phase)) / 1.18 * env * g
            out[i] = (sample * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** The closing chord for a full chorus: root, third and fifth of the scale together. */
    fun chord(durationMs: Int = 900, gain: Double = 0.5): ShortArray {
        val parts = listOf(chirp(pentatonicHz(0), durationMs, 1.0), chirp(pentatonicHz(2), durationMs, 1.0), chirp(pentatonicHz(3), durationMs, 1.0))
        val n = parts.minOf { it.size }
        val g = gain.coerceIn(0.0, 1.0) / parts.size
        return ShortArray(n) { i -> (parts.sumOf { it[i].toDouble() } * g).roundToInt().coerceIn(-32767, 32767).toShort() }
    }
}
