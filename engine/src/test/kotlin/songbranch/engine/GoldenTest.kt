// Checks the Kotlin port against golden.json, produced from the tested TypeScript engine by
// track-g-pico-space/engine-port/make-golden.ts. Every value must match exactly.
// Test dependencies: JUnit 5 and kotlinx-serialization-json (JsonElement API only).
// Point -Dgolden=/path/to/golden.json at the file, or run from the engine-port folder.
package songbranch.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class GoldenTest {
    private val root: JsonObject by lazy {
        val path = System.getProperty("golden") ?: "golden.json"
        Json.parseToJsonElement(File(path).readText()).jsonObject
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonArray.ints() = map { it.jsonPrimitive.int }

    @Test
    fun rngMatchesBitForBit() {
        for (entry in root.getValue("rng").jsonArray) {
            val e = entry.jsonObject
            val seed = e.str("seed")
            val h = xmur3(seed)
            val hashes = e.getValue("xmur3").jsonArray.map { it.jsonPrimitive.long }
            val mine = List(hashes.size) { h() }
            assertEquals(hashes, mine, "xmur3('$seed')")

            val m = mulberry32(hashes[0])
            val floats = e.getValue("mulberry32").jsonArray.map { it.jsonPrimitive.double }
            assertEquals(floats, List(floats.size) { m() }, "mulberry32('$seed')")

            val r = Rng(seed)
            val ints = e.getValue("makeRngInt0to99").jsonArray.ints()
            assertEquals(ints, List(ints.size) { r.int(0, 99) }, "Rng('$seed').int(0, 99)")
        }
    }

    @Test
    fun dailyGrovesSolverAndSimulationMatch() {
        val groves = root.getValue("groves").jsonArray
        for (item in groves) {
            val o = item.jsonObject
            val date = o.str("date")
            val guest = Guest.fromWire(o.str("guest"))
            val label = "$date/${guest.wire}"
            assertEquals(o.int("weekday"), weekday(date), "$label weekday")

            val expected = o.getValue("grove").jsonObject
            val g = generateDaily(date, guest)
            assertEquals(expected.int("v"), g.v, "$label v")
            assertEquals(expected.int("number"), g.number, "$label number")
            assertEquals(expected.str("name"), g.name, "$label name")
            assertEquals(expected.int("attempt"), g.attempt, "$label attempt")
            assertEquals(expected.int("par"), g.par, "$label par")
            assertEquals(expected.int("whistles"), g.grove.whistles, "$label whistles")
            assertEquals(expected.int("maxSong"), g.maxSong, "$label maxSong")
            assertEquals(expected.int("minSong"), g.minSong, "$label minSong")
            assertEquals(expected.int("solutions"), g.solutions, "$label solutions")
            assertEquals(expected.int("combos"), g.combos, "$label combos")
            assertEquals(expected.int("longSongs"), g.longSongs, "$label longSongs")

            val birds = expected.getValue("birds").jsonArray
            assertEquals(birds.size, g.grove.birds.size, "$label bird count")
            birds.forEachIndexed { i, b ->
                val eb = b.jsonObject
                val mb = g.grove.birds[i]
                assertEquals(eb.int("id"), mb.id, "$label bird $i id")
                assertEquals(eb.int("slot"), mb.slot, "$label bird $i slot")
                assertEquals(eb.getValue("x").jsonPrimitive.double, mb.x, "$label bird $i x")
                assertEquals(eb.getValue("y").jsonPrimitive.double, mb.y, "$label bird $i y")
                assertEquals(eb.int("need"), mb.need, "$label bird $i need")
                assertEquals(eb.str("kind"), mb.kind.wire, "$label bird $i kind")
                assertEquals(eb.str("species"), mb.species, "$label bird $i species")
            }
            val links = expected.getValue("links").jsonArray.map { l -> l.jsonArray.ints().let { it[0] to it[1] } }
            assertEquals(links, g.grove.links, "$label links")

            val s = o.getValue("solve").jsonObject
            val info = solve(g.grove)
            assertEquals(s.int("par"), info.par, "$label solve par")
            assertEquals(s.int("solutions"), info.solutions, "$label solve solutions")
            assertEquals(s.int("combos"), info.combos, "$label solve combos")
            assertEquals(s.int("maxSong"), info.maxSong, "$label solve maxSong")
            assertEquals(s.int("minSong"), info.minSong, "$label solve minSong")
            assertEquals(s.int("longSongs"), info.longSongs, "$label solve longSongs")
            assertEquals(s.getValue("example").jsonArray.ints(), info.example, "$label solve example")

            val ex = o.getValue("simExample").jsonObject
            val simEx = simulate(g.grove, info.example)
            assertSim(ex, simEx, "$label simExample")
            assertEquals(o.int("starsFirstTry"), starsFor(simEx, g.maxSong, 1), "$label stars")
            assertEquals(o.str("sparklineExample"), sparkline(simEx.waves.map { it.size }), "$label sparkline")

            val part = o.getValue("simPartial").jsonObject
            assertSim(part, simulate(g.grove, part.getValue("whistled").jsonArray.ints()), "$label simPartial")
        }
    }

    @Test
    fun defaultGuestMatches() {
        for (item in root.getValue("defaults").jsonArray) {
            val o = item.jsonObject
            assertEquals(o.str("guest"), defaultGuest(o.str("date")).wire, "defaultGuest ${o.str("date")}")
        }
    }

    private fun assertSim(expected: JsonObject, actual: SimResult, label: String) {
        val waves = expected.getValue("waves").jsonArray.map { it.jsonArray.ints() }
        assertEquals(waves, actual.waves, "$label waves")
        val singing = expected.getValue("singing").jsonArray.map { it.jsonPrimitive.boolean }
        assertEquals(singing, actual.singing, "$label singing")
        assertEquals(expected.getValue("allSinging").jsonPrimitive.boolean, actual.allSinging, "$label allSinging")
        assertEquals(expected.int("song"), actual.song, "$label song")
    }
}
