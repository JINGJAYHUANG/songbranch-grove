package songbranch.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import songbranch.engine.Guest
import songbranch.engine.generateDaily
import songbranch.engine.solve

class GroveSessionTest {
    @Test
    fun tutorialGrovesAreSolvableWithTheirWhistlesAndMaxSong() {
        for (level in TUTORIAL) {
            val info = solve(level.grove)
            assertEquals(level.grove.whistles, info.par, "${level.title}: par equals the whistles given")
            assertEquals(level.maxSong, info.maxSong, "${level.title}: maxSong")
        }
    }

    @Test
    fun tapPlacesAndRemovesWhistlesAndRespectsTheRules() {
        val level = TUTORIAL[2] // rooster, owl, finch; 1 whistle
        var s = GroveSession(level.grove, level.maxSong)
        assertEquals(Rejection.OWL_IGNORES_WHISTLES, s.tap(1).rejection)
        val placed = s.tap(0)
        assertNull(placed.rejection)
        s = placed.session
        assertEquals(setOf(0), s.whistled)
        assertEquals(Rejection.NO_WHISTLES_LEFT, s.tap(2).rejection)
        s = s.tap(0).session // take it back
        assertEquals(emptySet<Int>(), s.whistled)
        assertEquals(Rejection.NO_SUCH_BIRD, s.tap(99).rejection)
    }

    @Test
    fun fullDawnGivesStarsAndATimeline() {
        val level = TUTORIAL[0] // a chain of 4; a whistle at the end gives the longest song
        var s = GroveSession(level.grove, level.maxSong).tap(0).session.startDawn()
        val dawning = s.phase as Phase.Dawning
        assertEquals(listOf(0L, 600L, 1200L, 1800L), dawning.timeline.map { it.atMs })
        assertEquals(4 * WAVE_INTERVAL_MS, s.dawnDurationMs())
        assertEquals(Rejection.NOT_PLACING, s.tap(1).rejection)
        s = s.finishDawn()
        val result = s.phase as Phase.Result
        assertTrue(result.sim.allSinging)
        assertEquals(3, result.stars, "chorus + first try + longest song")
        assertEquals(1, s.attempts)
        assertEquals(emptyList<Int>(), s.sleepingBirds())
    }

    @Test
    fun failedDawnKeepsWhistlesForTheNextTry() {
        val level = TUTORIAL[1] // zz birds need two singers
        var s = GroveSession(level.grove, level.maxSong).tap(0).session.startDawn().finishDawn()
        val result = s.phase as Phase.Result
        assertEquals(0, result.stars)
        assertTrue(s.sleepingBirds().isNotEmpty())
        s = s.tryAgain()
        assertEquals(Phase.Placing, s.phase)
        assertEquals(setOf(0), s.whistled)
        // {0, 3} wakes everyone in 3 waves (checked against the TypeScript engine; {0, 4} does not).
        s = s.tap(3).session.startDawn().finishDawn()
        assertEquals(2, s.attempts)
        val second = s.phase as Phase.Result
        assertTrue(second.sim.allSinging)
        assertEquals(listOf(listOf(0, 3), listOf(2, 4), listOf(1)), second.sim.waves)
        assertEquals(3, second.stars, "chorus + within 3 tries + longest song (3)")
    }

    @Test
    fun dailyGroveExampleSolutionEarnsAtLeastTwoStarsOnTheFirstTry() {
        val daily = generateDaily("2026-12-01", Guest.ROOSTER)
        val info = solve(daily.grove)
        var s = GroveSession(daily.grove, daily.maxSong)
        for (id in info.example) s = s.tap(id).session
        s = s.startDawn().finishDawn()
        val stars = (s.phase as Phase.Result).stars
        assertTrue(stars >= 2, "chorus and clear head; long song only if the example is a longest song")
    }
}
