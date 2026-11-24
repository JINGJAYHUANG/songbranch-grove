// Kotlin port of track-e-reddit/engine/src/types.ts (Songbranch engine).
// Pure data. Must stay byte-compatible with the TypeScript engine: see GoldenTest.
package songbranch.engine

/** Rule-bearing kinds. Plain birds differ only in looks (species). */
enum class Kind(val wire: String) {
    PLAIN("plain"), ROOSTER("rooster"), OWL("owl"), LARK("lark");

    companion object {
        fun fromWire(value: String): Kind = entries.first { it.wire == value }
    }
}

/** Guest birds the community can vote into tomorrow's grove. */
enum class Guest(val wire: String, val kind: Kind) {
    ROOSTER("rooster", Kind.ROOSTER), OWL("owl", Kind.OWL), LARK("lark", Kind.LARK);

    companion object {
        fun fromWire(value: String): Guest = entries.first { it.wire == value }
    }
}

/** Same order as GUESTS in the TypeScript engine (it matters for defaultGuest). */
val GUESTS: List<Guest> = listOf(Guest.ROOSTER, Guest.OWL, Guest.LARK)

data class Bird(
    /** Index in Grove.birds. Birds are ordered by slot (reading order). */
    val id: Int,
    /** Lattice slot: row * COLS + col. */
    val slot: Int,
    /** Position for drawing, in cell units. */
    val x: Double,
    val y: Double,
    /** How many singing neighbors (roosters count 2) wake this bird. */
    val need: Int,
    val kind: Kind,
    /** Cosmetic species name, e.g. "robin". */
    val species: String,
)

data class Grove(
    val birds: List<Bird>,
    /** Undirected links between bird ids, each pair stored once with first < second. */
    val links: List<Pair<Int, Int>>,
    /** Whistles the player may place (equals par for daily groves). */
    val whistles: Int,
)

data class DailyGrove(
    val v: Int,
    val date: String,
    val number: Int,
    val name: String,
    val guest: Guest,
    val grove: Grove,
    val par: Int,
    val maxSong: Int,
    val minSong: Int,
    val solutions: Int,
    val combos: Int,
    /** Solutions whose song reaches maxSong. */
    val longSongs: Int,
    /** Generator attempt that produced this grove. */
    val attempt: Int,
)

data class SimResult(
    /** Bird ids that started singing in each wave. waves[0] = whistled birds + larks. */
    val waves: List<List<Int>>,
    val singing: List<Boolean>,
    val allSinging: Boolean,
    /** Number of waves, including wave 0. Zero when nobody sings. */
    val song: Int,
)

data class SolveInfo(
    /** Fewest whistles that wake every bird, or -1 if none up to the search limit. */
    val par: Int,
    val solutions: Int,
    val combos: Int,
    val maxSong: Int,
    val minSong: Int,
    val longSongs: Int,
    /** First solution in lexicographic order of bird ids. */
    val example: List<Int>,
)
