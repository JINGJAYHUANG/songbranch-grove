// Kotlin port of track-e-reddit/engine/src/solve.ts.
// Exhaustive solver: groves have at most 20 birds and par at most 5, so trying
// every whistle set is fast. Bird sets are bit masks in an Int (n <= 20).
package songbranch.engine

const val MAX_BIRDS = 20

private class Ctx(
    val n: Int,
    val full: Int,
    val larkMask: Int,
    val need: IntArray,
    val nbrs: List<IntArray>,
    val weight: IntArray,
    val eligible: IntArray,
)

private fun context(grove: Grove): Ctx {
    val n = grove.birds.size
    require(n in 1..MAX_BIRDS) { "grove must have 1-$MAX_BIRDS birds" }
    var larkMask = 0
    for (bird in grove.birds) if (bird.kind == Kind.LARK) larkMask = larkMask or (1 shl bird.id)
    return Ctx(
        n = n,
        full = (1 shl n) - 1,
        larkMask = larkMask,
        need = IntArray(n) { grove.birds[it].need },
        nbrs = adjacency(grove).map { it.toIntArray() },
        weight = IntArray(n) { voiceWeight(grove.birds[it].kind) },
        eligible = grove.birds.filter { canWhistle(it) }.map { it.id }.toIntArray(),
    )
}

/** Returns (singing mask, song length). */
private fun dawn(ctx: Ctx, whistleMask: Int): Pair<Int, Int> {
    var singing = whistleMask or ctx.larkMask
    if (singing == 0) return 0 to 0
    var song = 1
    while (true) {
        var joining = 0
        for (i in 0 until ctx.n) {
            val bit = 1 shl i
            if (singing and bit != 0) continue
            var heard = 0
            for (j in ctx.nbrs[i]) if (singing and (1 shl j) != 0) heard += ctx.weight[j]
            if (heard >= ctx.need[i]) joining = joining or bit
        }
        if (joining == 0) break
        singing = singing or joining
        song++
    }
    return singing to song
}

/** Visits every k-subset of items in lexicographic order, with its bit mask and ids. */
private fun forEachCombo(items: IntArray, k: Int, visit: (mask: Int, ids: List<Int>) -> Unit) {
    val ids = ArrayList<Int>(k)
    fun walk(start: Int, mask: Int) {
        if (ids.size == k) {
            visit(mask, ids)
            return
        }
        var i = start
        while (i <= items.size - (k - ids.size)) {
            val id = items[i]
            ids.add(id)
            walk(i + 1, mask or (1 shl id))
            ids.removeAt(ids.size - 1)
            i++
        }
    }
    walk(0, 0)
}

/**
 * Find par (fewest whistles that wake every bird) by trying k = 0..maxK.
 * Also reports how many par-sized whistle sets work and their song lengths.
 */
fun solve(grove: Grove, maxK: Int = 5): SolveInfo {
    val ctx = context(grove)
    val limit = minOf(maxK, ctx.eligible.size)
    for (k in 0..limit) {
        var combos = 0
        var solutions = 0
        var maxSong = 0
        var minSong = Int.MAX_VALUE
        var longSongs = 0
        var example: List<Int> = emptyList()
        forEachCombo(ctx.eligible, k) { mask, ids ->
            combos++
            val (resultMask, song) = dawn(ctx, mask)
            if (resultMask != ctx.full) return@forEachCombo
            solutions++
            if (song > maxSong) {
                maxSong = song
                longSongs = 1
            } else if (song == maxSong) {
                longSongs++
            }
            if (song < minSong) minSong = song
            if (solutions == 1) example = ids.toList()
        }
        if (solutions > 0) return SolveInfo(k, solutions, combos, maxSong, minSong, longSongs, example)
    }
    return SolveInfo(-1, 0, 0, 0, 0, 0, emptyList())
}
