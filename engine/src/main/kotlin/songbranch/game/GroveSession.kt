// Game flow for the PICO version of Songbranch: placing whistles, the dawn, the result.
// Pure Kotlin, no PICO SDK or Android imports, so the spatial UI only renders this state.
// Every transition returns a new immutable state (easy to hold in a Compose state holder).
package songbranch.game

import songbranch.engine.Bird
import songbranch.engine.Grove
import songbranch.engine.Kind
import songbranch.engine.SimResult
import songbranch.engine.adjacency
import songbranch.engine.canWhistle
import songbranch.engine.simulate
import songbranch.engine.sparkline
import songbranch.engine.starsFor

/** Milliseconds between two waves of the dawn animation. */
const val WAVE_INTERVAL_MS = 600L

sealed interface Phase {
    /** The player is placing whistles. */
    data object Placing : Phase

    /** The dawn is playing; the UI animates `timeline` and then calls finishDawn(). */
    data class Dawning(val sim: SimResult, val timeline: List<WaveCue>) : Phase

    /** The dawn is over. */
    data class Result(val sim: SimResult, val stars: Int, val sparkline: String) : Phase
}

/** One wave of the dawn: the birds that start singing at `atMs` after the dawn begins. */
data class WaveCue(val index: Int, val atMs: Long, val birds: List<Int>)

/** Why a tap on a bird did nothing. */
enum class Rejection { OWL_IGNORES_WHISTLES, LARK_ALREADY_SINGS, NO_WHISTLES_LEFT, NOT_PLACING, NO_SUCH_BIRD }

data class TapResult(val session: GroveSession, val rejection: Rejection?)

data class GroveSession(
    val grove: Grove,
    /** The longest possible song for this grove (for the third star). */
    val maxSong: Int,
    val whistled: Set<Int> = emptySet(),
    /** Dawns started so far (the first dawn is attempt 1). */
    val attempts: Int = 0,
    val phase: Phase = Phase.Placing,
) {
    val whistlesLeft: Int get() = grove.whistles - whistled.size

    /** Tap a bird: place a whistle, or take it back if it already has one. */
    fun tap(birdId: Int): TapResult {
        if (phase != Phase.Placing) return TapResult(this, Rejection.NOT_PLACING)
        val bird = grove.birds.getOrNull(birdId) ?: return TapResult(this, Rejection.NO_SUCH_BIRD)
        if (birdId in whistled) return TapResult(copy(whistled = whistled - birdId), null)
        if (!canWhistle(bird)) {
            val why = if (bird.kind == Kind.OWL) Rejection.OWL_IGNORES_WHISTLES else Rejection.LARK_ALREADY_SINGS
            return TapResult(this, why)
        }
        if (whistlesLeft <= 0) return TapResult(this, Rejection.NO_WHISTLES_LEFT)
        return TapResult(copy(whistled = whistled + birdId), null)
    }

    fun clearWhistles(): GroveSession = if (phase == Phase.Placing) copy(whistled = emptySet()) else this

    /** Start the dawn with the current whistles. Larks sing even with no whistles. */
    fun startDawn(): GroveSession {
        if (phase != Phase.Placing) return this
        val sim = simulate(grove, whistled.sorted())
        val timeline = sim.waves.mapIndexed { i, birds -> WaveCue(i, i * WAVE_INTERVAL_MS, birds) }
        return copy(attempts = attempts + 1, phase = Phase.Dawning(sim, timeline))
    }

    /** Called when the animation ends (or is skipped). */
    fun finishDawn(): GroveSession {
        val dawning = phase as? Phase.Dawning ?: return this
        val sim = dawning.sim
        val stars = starsFor(sim, maxSong, attempts)
        return copy(phase = Phase.Result(sim, stars, sparkline(sim.waves.map { it.size })))
    }

    /** Back to placing after a result: keep the whistles so the player can adjust them. */
    fun tryAgain(): GroveSession = if (phase is Phase.Result) copy(phase = Phase.Placing) else this

    /** Birds still asleep after the dawn (to highlight them on the result). */
    fun sleepingBirds(): List<Int> {
        val sim = when (val p = phase) {
            is Phase.Result -> p.sim
            is Phase.Dawning -> p.sim
            Phase.Placing -> return emptyList()
        }
        return sim.singing.indices.filter { !sim.singing[it] }
    }

    /** Total length of the dawn animation in milliseconds. */
    fun dawnDurationMs(): Long = when (val p = phase) {
        is Phase.Dawning -> p.timeline.size * WAVE_INTERVAL_MS
        else -> 0L
    }
}

/** Screen-reader label for a bird, matching the Reddit version's wording. */
fun birdLabel(grove: Grove, bird: Bird, whistled: Boolean, singing: Boolean): String {
    val branches = adjacency(grove)[bird.id].size
    val kind = when (bird.kind) {
        Kind.PLAIN -> bird.species
        Kind.ROOSTER -> "rooster, counts as two singers"
        Kind.OWL -> "owl, ignores whistles"
        Kind.LARK -> "lark, already singing"
    }
    val state = when {
        singing -> "singing"
        whistled -> "has a whistle"
        else -> "asleep, needs ${bird.need} singing ${if (bird.need == 1) "neighbor" else "neighbors"}"
    }
    return "$kind, $branches ${if (branches == 1) "branch" else "branches"}, $state"
}
