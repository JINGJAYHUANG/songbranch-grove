package songbranch.tools

import org.junit.jupiter.api.Test
import songbranch.engine.defaultGuest
import songbranch.engine.generateDaily
import songbranch.engine.solve
import java.time.LocalDate
import java.time.ZoneOffset

/** Prints today's grove and one solver solution (developer aid for emulator testing; asserts nothing). */
class PrintToday {
    @Test
    fun printToday() {
        val date = System.getProperty("date") ?: LocalDate.now(ZoneOffset.UTC).toString()
        val d = generateDaily(date, defaultGuest(date))
        val info = solve(d.grove)
        println("TODAY date=$date #${d.number} ${d.name} guest=${d.guest} birds=${d.grove.birds.size} whistles=${d.grove.whistles} par=${info.par} maxSong=${info.maxSong} example=${info.example}")
        d.grove.birds.forEach { println("BIRD ${it.id} need=${it.need} kind=${it.kind} species=${it.species}") }
    }
}
