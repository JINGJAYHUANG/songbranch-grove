// Kotlin port of track-e-reddit/engine/src/rules.ts.
//
// Wave 0: whistled birds and every lark sing.
// Wave t+1: a sleeping bird joins when the singing neighbors at the end of
// wave t add up to at least its `need`. A singing rooster counts 2, any other
// singing bird counts 1. Waves stop when nobody new joins.
package songbranch.engine

fun voiceWeight(kind: Kind): Int = if (kind == Kind.ROOSTER) 2 else 1

/** Owls ignore whistles; larks are already singing. */
fun canWhistle(bird: Bird): Boolean = bird.kind == Kind.PLAIN || bird.kind == Kind.ROOSTER

fun adjacency(grove: Grove): List<List<Int>> {
    val adj = List(grove.birds.size) { ArrayList<Int>() }
    for ((a, b) in grove.links) {
        adj[a].add(b)
        adj[b].add(a)
    }
    for (list in adj) list.sort()
    return adj
}

/** Sum of neighbor voice weights: the most a bird can ever hear. */
fun weightedDegree(grove: Grove, adj: List<List<Int>> = adjacency(grove)): List<Int> =
    grove.birds.indices.map { i -> adj[i].sumOf { j -> voiceWeight(grove.birds[j].kind) } }

fun isConnected(count: Int, links: List<Pair<Int, Int>>): Boolean {
    if (count == 0) return false
    val adj = List(count) { ArrayList<Int>() }
    for ((a, b) in links) {
        adj[a].add(b)
        adj[b].add(a)
    }
    val seen = BooleanArray(count)
    val stack = ArrayDeque<Int>()
    stack.addLast(0)
    seen[0] = true
    var visited = 1
    while (stack.isNotEmpty()) {
        val i = stack.removeLast()
        for (j in adj[i]) {
            if (!seen[j]) {
                seen[j] = true
                visited++
                stack.addLast(j)
            }
        }
    }
    return visited == count
}

sealed interface WhistleCheck {
    data class Ok(val whistles: List<Int>) : WhistleCheck
    data class Rejected(val reason: String) : WhistleCheck
}

/** Validate a player's whistles. Returns them sorted and de-duplicated on success. */
fun checkWhistles(grove: Grove, input: List<Int>): WhistleCheck {
    if (input.size > grove.whistles) return WhistleCheck.Rejected("at most ${grove.whistles} whistles")
    val seen = LinkedHashSet<Int>()
    for (id in input) {
        if (id < 0 || id >= grove.birds.size) return WhistleCheck.Rejected("no bird $id")
        if (id in seen) return WhistleCheck.Rejected("bird $id listed twice")
        if (!canWhistle(grove.birds[id])) return WhistleCheck.Rejected("bird $id cannot take a whistle")
        seen.add(id)
    }
    return WhistleCheck.Ok(seen.sorted())
}

/** Run the dawn. `whistled` must already be valid (see checkWhistles). */
fun simulate(grove: Grove, whistled: List<Int>): SimResult {
    val n = grove.birds.size
    val adj = adjacency(grove)
    val singing = BooleanArray(n)
    val first = ArrayList<Int>()
    for (bird in grove.birds) {
        if (bird.kind == Kind.LARK) {
            singing[bird.id] = true
            first.add(bird.id)
        }
    }
    for (id in whistled) {
        if (!singing[id]) {
            singing[id] = true
            first.add(id)
        }
    }
    if (first.isEmpty()) return SimResult(emptyList(), singing.toList(), n == 0, 0)
    first.sort()
    val waves = ArrayList<List<Int>>()
    waves.add(first)
    while (true) {
        val joining = ArrayList<Int>()
        for (i in 0 until n) {
            if (singing[i]) continue
            var heard = 0
            for (j in adj[i]) if (singing[j]) heard += voiceWeight(grove.birds[j].kind)
            if (heard >= grove.birds[i].need) joining.add(i)
        }
        if (joining.isEmpty()) break
        for (i in joining) singing[i] = true
        waves.add(joining)
    }
    return SimResult(waves, singing.toList(), singing.all { it }, waves.size)
}
