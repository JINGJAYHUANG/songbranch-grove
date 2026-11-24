// Kotlin port of track-e-reddit/engine/src/rng.ts.
// xmur3 (string hash) and mulberry32 (PRNG) are small public-domain algorithms.
// JavaScript's Math.imul, >>> and | operate on 32-bit integers; Kotlin Int arithmetic
// wraps the same way, so every step below keeps the exact bits of the TypeScript version.
package songbranch.engine

import kotlin.math.floor

/** Returns a generator of unsigned 32-bit hashes (as Long in 0..2^32-1). */
fun xmur3(text: String): () -> Long {
    var h = 1779033703 xor text.length
    for (ch in text) { // UTF-16 code units, like charCodeAt
        h = (h xor ch.code) * -862048943 // 3432918353 as a signed 32-bit int
        h = (h shl 13) or (h ushr 19)
    }
    return {
        h = (h xor (h ushr 16)) * -2048144789 // 2246822507
        h = (h xor (h ushr 13)) * -1028477387 // 3266489909
        h = h xor (h ushr 16)
        h.toLong() and 0xFFFFFFFFL
    }
}

/** Returns a generator of doubles in [0, 1). */
fun mulberry32(seed: Long): () -> Double {
    var a = seed.toInt() // low 32 bits, same as seed >>> 0
    return {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

class Rng(seedText: String) {
    private val nextFn: () -> Double = mulberry32(xmur3(seedText)())

    /** Uniform double in [0, 1). */
    fun next(): Double = nextFn()

    /** Uniform integer in [min, max], both inclusive. */
    fun int(min: Int, max: Int): Int = min + floor(next() * (max - min + 1)).toInt()

    fun chance(p: Double): Boolean = next() < p

    fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty()) { "pick from empty list" }
        return items[floor(next() * items.size).toInt()]
    }

    /** Index chosen with probability proportional to its weight. */
    fun weighted(weights: List<Double>): Int {
        var total = 0.0
        for (w in weights) total += w // same left-to-right order as Array.reduce
        var r = next() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r < 0) return i
        }
        return weights.size - 1
    }
}
