// Kotlin port of track-e-reddit/engine/src/generate.ts and the parts of score.ts the game needs.
// Same date + same guest => the same grove as the TypeScript engine (checked by GoldenTest).
// Every random draw happens in the same order as the TypeScript code; do not reorder.
package songbranch.engine

import kotlin.math.ceil
import kotlin.math.max

const val GENERATOR_VERSION = 1
/** Puzzle #1. */
const val EPOCH = "2026-11-20"
const val MAX_ATTEMPTS = 400

data class DayParams(
    val birdsMin: Int,
    val birdsMax: Int,
    val parMin: Int,
    val parMax: Int,
    /** Upper bound on solutions / combos at par. Lower = harder. */
    val ratioMax: Double,
    val minSong: Int,
    /** Relative odds of need 1, 2, 3 before capping by what a bird can hear. */
    val needWeights: List<Double>,
    val guestsMin: Int,
    val guestsMax: Int,
    val pOrth: Double,
    val pDiag: Double,
)

/** Index 0 = Sunday. Monday is gentlest, the weekend is hardest. */
val WEEKDAY_PARAMS: List<DayParams> = listOf(
    DayParams(14, 16, 3, 4, 0.05, 5, listOf(0.35, 0.42, 0.23), 2, 2, 0.8, 0.3),
    DayParams(8, 10, 2, 2, 0.25, 4, listOf(0.55, 0.35, 0.1), 1, 1, 0.8, 0.3),
    DayParams(9, 11, 2, 2, 0.2, 4, listOf(0.5, 0.38, 0.12), 1, 1, 0.8, 0.3),
    DayParams(10, 12, 2, 3, 0.12, 4, listOf(0.45, 0.4, 0.15), 1, 2, 0.8, 0.3),
    DayParams(11, 13, 3, 3, 0.1, 4, listOf(0.42, 0.4, 0.18), 1, 2, 0.8, 0.3),
    DayParams(12, 14, 3, 3, 0.08, 5, listOf(0.4, 0.4, 0.2), 1, 2, 0.8, 0.3),
    DayParams(13, 15, 3, 4, 0.06, 5, listOf(0.38, 0.42, 0.2), 1, 2, 0.8, 0.3),
)

/** Weekday parameters, loosened step by step if many attempts fail. */
fun paramsFor(date: String, attempt: Int): DayParams {
    val base = WEEKDAY_PARAMS[weekday(date)]
    var p = base
    if (attempt >= 150) p = p.copy(ratioMax = base.ratioMax * 2)
    if (attempt >= 250) p = p.copy(minSong = max(3, base.minSong - 1))
    if (attempt >= 325) p = p.copy(ratioMax = base.ratioMax * 3, parMax = minOf(5, base.parMax + 1))
    return p
}

private fun pickSlots(rng: Rng, count: Int): List<Int> {
    val chosen = LinkedHashSet<Int>()
    val frontier = ArrayList<Int>()
    fun grow(slot: Int) {
        chosen.add(slot)
        for (next in orthNeighbors(slot)) {
            if (next !in chosen && next !in frontier) frontier.add(next)
        }
    }
    grow(rng.int(0, SLOTS - 1))
    while (chosen.size < count) {
        val slot = frontier.removeAt(rng.int(0, frontier.size - 1))
        grow(slot)
    }
    return chosen.sorted()
}

private fun pickLinks(rng: Rng, slots: List<Int>, pOrth: Double, pDiag: Double): List<Pair<Int, Int>> {
    val present = slots.toHashSet()
    val links = ArrayList<Pair<Int, Int>>()
    for (s in slots) {
        // && short-circuits exactly like the TypeScript: chance() is drawn only when both birds exist.
        if (colOf(s) < COLS - 1 && (s + 1) in present && rng.chance(pOrth)) links.add(s to s + 1)
        if (rowOf(s) < ROWS - 1 && (s + COLS) in present && rng.chance(pOrth)) links.add(s to s + COLS)
    }
    for (r in 0 until ROWS - 1) {
        for (c in 0 until COLS - 1) {
            val tl = slotAt(c, r)
            val tr = slotAt(c + 1, r)
            val bl = slotAt(c, r + 1)
            val br = slotAt(c + 1, r + 1)
            val down = tl in present && br in present && rng.chance(pDiag)
            val up = tr in present && bl in present && rng.chance(pDiag)
            if (down && up) links.add(if (rng.chance(0.5)) tl to br else tr to bl)
            else if (down) links.add(tl to br)
            else if (up) links.add(tr to bl)
        }
    }
    return links
}

/** Math.round(value * 1000) / 1000 with JavaScript's tie rule (ties toward +infinity). */
private fun round3(value: Double): Double = java.lang.Math.round(value * 1000).toDouble() / 1000.0

/** Build one candidate grove (not yet solved). Returns null if it is not connected. */
fun candidateGrove(rng: Rng, params: DayParams, guest: Guest): Grove? {
    val count = rng.int(params.birdsMin, params.birdsMax)
    val slots = pickSlots(rng, count)
    val idOfSlot = HashMap<Int, Int>()
    slots.forEachIndexed { id, slot -> idOfSlot[slot] = id }
    val links = pickLinks(rng, slots, params.pOrth, params.pDiag)
        .map { (a, b) -> idOfSlot.getValue(a) to idOfSlot.getValue(b) }
        .sortedWith(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
    if (!isConnected(count, links)) return null

    val kinds = MutableList(count) { Kind.PLAIN }
    val open = MutableList(count) { it }
    val guestCount = rng.int(params.guestsMin, params.guestsMax)
    var g = 0
    while (g < guestCount && open.isNotEmpty()) {
        val id = open.removeAt(rng.int(0, open.size - 1))
        kinds[id] = guest.kind
        g++
    }

    // Per bird, in id order: x, y, then a species only for plain birds.
    val unsized = slots.mapIndexed { id, slot ->
        val x = round3(colOf(slot) + 0.5 + (rng.next() - 0.5) * 0.32)
        val y = round3(rowOf(slot) + 0.5 + (rng.next() - 0.5) * 0.32)
        val kind = kinds[id]
        val species = if (kind == Kind.PLAIN) rng.pick(SPECIES) else kind.wire
        Bird(id, slot, x, y, 1, kind, species)
    }
    val hearing = weightedDegree(Grove(unsized, links, 0))
    val birds = unsized.map { bird ->
        if (bird.kind == Kind.LARK) {
            bird
        } else {
            val wanted = rng.weighted(params.needWeights) + 1
            bird.copy(need = max(1, minOf(wanted, 3, hearing[bird.id])))
        }
    }
    return Grove(birds, links, 0)
}

fun generateDaily(date: String, guest: Guest): DailyGrove {
    require(isIsoDate(date)) { "not a date: $date" }
    for (attempt in 0 until MAX_ATTEMPTS) {
        val params = paramsFor(date, attempt)
        val rng = Rng("songbranch|v$GENERATOR_VERSION|$date|${guest.wire}|$attempt")
        val grove = candidateGrove(rng, params, guest) ?: continue
        val info = solve(grove, params.parMax)
        if (info.par < params.parMin) continue
        if (info.solutions.toDouble() / info.combos > params.ratioMax) continue
        if (info.maxSong < params.minSong) continue
        val name = groveName(rng.int(0, ADJECTIVES.size - 1), rng.int(0, NOUNS.size - 1))
        return DailyGrove(
            v = GENERATOR_VERSION,
            date = date,
            number = daysBetween(EPOCH, date) + 1,
            name = name,
            guest = guest,
            grove = grove.copy(whistles = info.par),
            par = info.par,
            maxSong = info.maxSong,
            minSong = info.minSong,
            solutions = info.solutions,
            combos = info.combos,
            longSongs = info.longSongs,
            attempt = attempt,
        )
    }
    error("no grove for $date (${guest.wire}) after $MAX_ATTEMPTS attempts")
}

/** Guest used when nobody voted, or votes are tied. Deterministic per date. */
fun defaultGuest(date: String, tied: List<Guest> = GUESTS): Guest =
    Rng("songbranch|guest|$date").pick(tied.ifEmpty { GUESTS })

/** Stars for one attempt (attemptNumber starts at 1): chorus, clear head, long song. */
fun starsFor(sim: SimResult, maxSong: Int, attemptNumber: Int): Int {
    if (!sim.allSinging || sim.song == 0) return 0
    var stars = 1
    if (attemptNumber <= 3) stars++
    if (sim.song >= maxSong) stars++
    return stars
}

private const val BARS = "▁▂▃▄▅▆▇█"

/** One bar per wave, height = birds that joined in that wave. */
fun sparkline(waveSizes: List<Int>): String {
    val top = waveSizes.fold(0) { m, s -> max(m, s) }
    if (top == 0) return ""
    return waveSizes.joinToString("") { size ->
        BARS[max(0, ceil(size.toDouble() / top * BARS.length).toInt() - 1)].toString()
    }
}
