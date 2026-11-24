package dev.songbranch.grove.content

import com.pico.spatial.core.math.Color4
import songbranch.engine.Kind

/** Colour tokens for the grove, in linear RGBA. One knitted, dawn-lit look throughout. */
object Palette {
    // Birds
    val asleep = Color4(0.30f, 0.36f, 0.55f, 1f)        // dusky blue, eyes closed
    val asleepOwl = Color4(0.42f, 0.30f, 0.20f, 1f)     // brown owl
    val asleepRooster = Color4(0.62f, 0.20f, 0.14f, 1f) // deep red rooster
    val whistled = Color4(0.96f, 0.90f, 0.74f, 1f)      // cream: a whistle is waiting here
    val singing = Color4(1.00f, 0.78f, 0.25f, 1f)       // dawn gold
    val stillAsleep = Color4(0.20f, 0.22f, 0.32f, 1f)   // after a failed dawn
    val beak = Color4(0.98f, 0.62f, 0.18f, 1f)
    val comb = Color4(0.90f, 0.12f, 0.10f, 1f)
    val ring = Color4(1.00f, 0.84f, 0.35f, 1f)          // whistle marker ring
    val note = Color4(1.00f, 0.95f, 0.70f, 1f)          // floating note when a bird sings

    // Branches
    val branch = Color4(0.36f, 0.25f, 0.16f, 1f)
    val branchLit = Color4(0.95f, 0.70f, 0.30f, 1f)

    fun asleepFor(kind: Kind): Color4 = when (kind) {
        Kind.OWL -> asleepOwl
        Kind.ROOSTER -> asleepRooster
        Kind.LARK -> singing
        Kind.PLAIN -> asleep
    }
}
