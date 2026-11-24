package songbranch.spatial

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import songbranch.audio.Tones
import songbranch.engine.GUESTS
import songbranch.engine.generateDaily
import java.time.LocalDate
import kotlin.math.abs

class GroveLayoutTest {
    private val days = (0 until 21).map { LocalDate.of(2026, 12, 1).plusDays(it.toLong()).toString() }

    @Test
    fun birdsSitInFrontOfTheUserAtReachableHeights() {
        for (date in days) for (guest in GUESTS) {
            val scene = GroveLayout.layout(generateDaily(date, guest).grove)
            for (p in scene.birds) {
                assertTrue(p.z < -1.3, "bird must be in front of the user: $p")
                assertTrue(p.y in 0.95..2.3, "bird height must be reachable: $p")
                assertTrue(abs(p.x) < 1.35, "bird must be inside the horizontal spread: $p")
            }
        }
    }

    @Test
    fun branchesRunExactlyFromOneBirdToTheOther() {
        for (date in days) {
            val grove = generateDaily(date, GUESTS[0]).grove
            val scene = GroveLayout.layout(grove)
            assertEquals(grove.links.size, scene.branches.size)
            for (br in scene.branches) {
                val tip = br.center + br.rotation.rotate(Vec3(0.0, br.length / 2, 0.0))
                val tail = br.center - br.rotation.rotate(Vec3(0.0, br.length / 2, 0.0))
                assertTrue((tip - scene.birds[br.b]).length() < 1e-9, "branch end must meet bird ${br.b}")
                assertTrue((tail - scene.birds[br.a]).length() < 1e-9, "branch start must meet bird ${br.a}")
                assertTrue(br.length in 0.1..1.3, "branch length looks wrong: ${br.length}")
            }
        }
    }

    @Test
    fun birdsDoNotOverlap() {
        for (date in days) {
            val scene = GroveLayout.layout(generateDaily(date, GUESTS[1]).grove)
            for (i in scene.birds.indices) for (j in i + 1 until scene.birds.size) {
                assertTrue((scene.birds[i] - scene.birds[j]).length() > 0.15, "birds $i and $j are too close")
            }
        }
    }

    @Test
    fun quaternionRotatesUpOntoAnyDirection() {
        val up = Vec3(0.0, 1.0, 0.0)
        for (d in listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, -1.0, 0.0), Vec3(0.3, 0.4, -0.5), Vec3(0.0, 1.0, 0.0))) {
            val r = Quat.between(up, d).rotate(up)
            assertTrue((r - d.normalized()).length() < 1e-9, "rotate up onto $d gave $r")
        }
    }

    @Test
    fun tonesAreAudibleAndBounded() {
        val chirp = Tones.chirp(Tones.pentatonicHz(3), 260, 0.6)
        assertEquals(Tones.SAMPLE_RATE * 260 / 1000, chirp.size)
        val peak = chirp.maxOf { abs(it.toInt()) }
        assertTrue(peak in 8000..32767, "peak $peak")
        assertTrue(Tones.pentatonicHz(5) > Tones.pentatonicHz(4), "scale must climb across the octave")
        assertEquals(2 * Tones.pentatonicHz(0), Tones.pentatonicHz(5), 1e-6)
        val chord = Tones.chord()
        assertTrue(chord.maxOf { abs(it.toInt()) } <= 32767)
    }
}
