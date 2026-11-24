// The three hand-made practice groves, ported from track-e-reddit/app/src/shared/tutorial.ts.
// Coordinates are lattice units (x across, y down), as in the Reddit version; the spatial
// scene maps them onto the arc around the player.
package songbranch.game

import songbranch.engine.Bird
import songbranch.engine.Grove
import songbranch.engine.Kind

data class TutorialLevel(
    val title: String,
    /** Shown before the first dawn. */
    val hint: String,
    /** Shown after a dawn that leaves birds asleep. */
    val retry: String,
    /** Shown when the whole grove sings. */
    val done: String,
    val grove: Grove,
    val maxSong: Int,
)

private data class B(val x: Double, val y: Double, val need: Int, val kind: Kind, val species: String)

private fun grove(birds: List<B>, links: List<Pair<Int, Int>>, whistles: Int): Grove =
    Grove(
        birds = birds.mapIndexed { id, b -> Bird(id, id, b.x, b.y, b.need, b.kind, b.species) },
        links = links,
        whistles = whistles,
    )

val TUTORIAL: List<TutorialLevel> = listOf(
    TutorialLevel(
        title = "One song starts the next",
        hint = "Select any bird to place your whistle, then press Dawn.",
        retry = "Every bird here wakes to one singing neighbor. Try a whistle at the end of the branch for a longer song.",
        done = "Each bird woke the next. That chain is the whole game.",
        grove = grove(
            listOf(
                B(0.5, 1.0, 1, Kind.PLAIN, "robin"),
                B(1.5, 1.15, 1, Kind.PLAIN, "finch"),
                B(2.5, 1.0, 1, Kind.PLAIN, "tit"),
                B(3.5, 1.15, 1, Kind.PLAIN, "warbler"),
            ),
            listOf(0 to 1, 1 to 2, 2 to 3),
            whistles = 1,
        ),
        maxSong = 4,
    ),
    TutorialLevel(
        title = "zz needs two",
        hint = "Birds that show zz need two singing neighbors. You have 2 whistles.",
        retry = "A zz bird with only one singing neighbor stays asleep. Give each zz bird two singers.",
        done = "Both zz birds heard two songs at once.",
        grove = grove(
            listOf(
                B(0.4, 1.1, 1, Kind.PLAIN, "sparrow"),
                B(1.2, 0.85, 2, Kind.PLAIN, "bunting"),
                B(2.0, 1.1, 1, Kind.PLAIN, "thrush"),
                B(2.8, 0.85, 2, Kind.PLAIN, "robin"),
                B(3.6, 1.1, 1, Kind.PLAIN, "wren"),
            ),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
            whistles = 2,
        ),
        maxSong = 3,
    ),
    TutorialLevel(
        title = "Guests",
        hint = "A singing rooster counts as two. Owls ignore whistles, so wake this owl through its neighbors.",
        retry = "The owl needs two singers, and only the rooster counts double.",
        done = "The rooster's double song woke the owl. You are ready for today's grove.",
        grove = grove(
            listOf(
                B(0.7, 1.05, 1, Kind.ROOSTER, "rooster"),
                B(2.0, 0.9, 2, Kind.OWL, "owl"),
                B(3.3, 1.05, 1, Kind.PLAIN, "finch"),
            ),
            listOf(0 to 1, 1 to 2),
            whistles = 1,
        ),
        maxSong = 3,
    ),
)
