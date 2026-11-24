// Kotlin port of track-e-reddit/engine/src/lattice.ts and names.ts.
// The grove sits on a 4-column x 5-row lattice. Links join lattice neighbors
// (orthogonal or diagonal); the two diagonals of one unit cell never both exist.
package songbranch.engine

import kotlin.math.abs
import kotlin.math.min

const val COLS = 4
const val ROWS = 5
const val SLOTS = COLS * ROWS

fun colOf(slot: Int): Int = slot % COLS
fun rowOf(slot: Int): Int = slot / COLS
fun slotAt(col: Int, row: Int): Int = row * COLS + col
fun isSlot(slot: Int): Boolean = slot in 0 until SLOTS

enum class Relation { ORTH, DIAG }

fun relation(a: Int, b: Int): Relation? {
    val dc = abs(colOf(a) - colOf(b))
    val dr = abs(rowOf(a) - rowOf(b))
    if (dc + dr == 1) return Relation.ORTH
    if (dc == 1 && dr == 1) return Relation.DIAG
    return null
}

/** Top-left slot of the unit cell a diagonal link runs through. */
fun diagonalCell(a: Int, b: Int): Int = slotAt(min(colOf(a), colOf(b)), min(rowOf(a), rowOf(b)))

/** True when two links are the two different diagonals of the same cell. */
fun linksCross(l1: Pair<Int, Int>, l2: Pair<Int, Int>): Boolean {
    if (relation(l1.first, l1.second) != Relation.DIAG || relation(l2.first, l2.second) != Relation.DIAG) return false
    if (diagonalCell(l1.first, l1.second) != diagonalCell(l2.first, l2.second)) return false
    val same = (l1.first == l2.first && l1.second == l2.second) || (l1.first == l2.second && l1.second == l2.first)
    return !same
}

/** Up, left, right, down: the same order as the TypeScript engine (it affects generation). */
fun orthNeighbors(slot: Int): List<Int> {
    val c = colOf(slot)
    val r = rowOf(slot)
    val out = ArrayList<Int>(4)
    if (r > 0) out.add(slotAt(c, r - 1))
    if (c > 0) out.add(slotAt(c - 1, r))
    if (c < COLS - 1) out.add(slotAt(c + 1, r))
    if (r < ROWS - 1) out.add(slotAt(c, r + 1))
    return out
}

// Word lists (original to Songbranch). Order matters: names are picked by index.
val ADJECTIVES: List<String> = listOf(
    "Mossy", "Copper", "Willow", "Amber", "Misty", "Silver", "Bramble", "Quiet",
    "Dappled", "Hazel", "Russet", "Golden", "Pale", "Windy", "Frosty", "Sunny",
    "Rainy", "Velvet", "Clover", "Juniper", "Cobble", "Hollow", "Lantern", "Saffron",
)

val NOUNS: List<String> = listOf(
    "Hedge", "Bend", "Copse", "Glen", "Thicket", "Orchard", "Meadow", "Ridge",
    "Brook", "Dell", "Lane", "Bough", "Green", "Heath", "Spinney", "Coppice",
    "Knoll", "Hollow", "Common", "Mill", "Ford", "Rise", "Wold", "Vale",
)

/** Plain birds only differ in looks. */
val SPECIES: List<String> = listOf("sparrow", "robin", "finch", "wren", "tit", "warbler", "bunting", "thrush")

fun groveName(adjective: Int, noun: Int): String {
    val a = ADJECTIVES.getOrNull(adjective)
    val b = NOUNS.getOrNull(noun)
    require(a != null && b != null) { "name index out of range" }
    return if (a == b) "$a Hill" else "$a $b"
}
