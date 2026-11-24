package dev.songbranch.grove.content

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.LookAtComponent
import com.pico.spatial.core.ecs.LookAtForwardDirection
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import songbranch.engine.Bird
import songbranch.engine.DailyGrove
import songbranch.engine.Grove
import songbranch.engine.Guest
import songbranch.engine.Kind
import songbranch.engine.defaultGuest
import songbranch.engine.generateDaily
import songbranch.game.GroveSession
import songbranch.game.Phase
import songbranch.game.Rejection
import songbranch.game.TUTORIAL
import songbranch.game.TutorialLevel
import songbranch.game.WAVE_INTERVAL_MS
import songbranch.game.birdLabel
import songbranch.spatial.GroveLayout
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.atan2

enum class UiPhase { Placing, Dawning, Won, Failed }

/** Everything the 2D panel shows. Plain Compose state, written only from the main thread. */
class GroveUi {
    var levelLabel by mutableStateOf("")
    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var hint by mutableStateOf("")
    var whistlesLeft by mutableIntStateOf(0)
    var whistlesTotal by mutableIntStateOf(0)
    var phase by mutableStateOf(UiPhase.Placing)
    var message by mutableStateOf<String?>(null)
    var resultTitle by mutableStateOf("")
    var resultDetail by mutableStateOf("")
    var stars by mutableIntStateOf(0)
    var sparkline by mutableStateOf("")
    var waveSizes by mutableStateOf<List<Int>>(emptyList())
    var showRules by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var showList by mutableStateOf(false)
    var birdButtons by mutableStateOf<List<BirdButton>>(emptyList())
}

/** One entry of the accessible bird list on the panel (same action as selecting the bird in 3D). */
data class BirdButton(val id: Int, val label: String, val description: String, val whistled: Boolean)

private sealed interface Level {
    val grove: Grove
    val maxSong: Int

    data class Practice(val number: Int, val level: TutorialLevel) : Level {
        override val grove get() = level.grove
        override val maxSong get() = level.maxSong
    }

    data class Daily(val daily: DailyGrove) : Level {
        override val grove get() = daily.grove
        override val maxSong get() = daily.maxSong
    }
}

/**
 * Owns the 3D grove: birds on an arc around the player, branches between them, whistle rings and the dawn.
 * The rules, solver and daily generator are the tested engine module; this class only renders its state.
 */
class GroveGame(context: Context) {
    val ui = GroveUi()
    val root: Entity = Entity().apply { setName("grove-root") }
    val audio = GroveAudio(context)

    private val today: String = LocalDate.now(ZoneOffset.UTC).toString()
    private val levels: List<Level> =
        TUTORIAL.mapIndexed { i, t -> Level.Practice(i + 1, t) } + Level.Daily(generateDaily(today, defaultGuest(today)))
    private var levelIndex = 0
    private var session = GroveSession(levels[0].grove, levels[0].maxSong)

    // Shared meshes and materials are global resources: destroying a level's entities must not release them
    // (the SDK otherwise closes resources together with the last entity that used them). Closed in dispose().
    private val globals = ArrayList<Resource>()
    private fun <T : Resource> T.global(): T = apply { toGlobal(); globals.add(this) }

    private val labels = Labels { it.global() }
    private val bodyMesh = MeshResource.createSphere(0.048f).global()
    private val headMesh = MeshResource.createSphere(0.031f).global()
    private val beakMesh = MeshResource.createCone(0.034f, 0.012f).global()
    private val combMesh = MeshResource.createSphere(0.015f).global()
    private val ringMesh = MeshResource.createTorus(0.075f, 0.007f).global()
    private val noteMesh = MeshResource.createSphere(0.015f).global()
    private val branchMesh = MeshResource.createCylinder(1f, 0.006f).global()
    private val beakMat = unlit(Palette.beak).global()
    private val combMat = unlit(Palette.comb).global()
    private val ringMat = unlit(Palette.ring).global()
    private val noteMat = unlit(Palette.note).global()

    private class BirdView(
        val bird: Bird,
        val root: Entity,
        val body: Entity,
        val bodyMat: UnlitMaterial,
        val ring: Entity,
        val note: Entity,
        val position: Vector3,
    )

    private class BranchView(val a: Int, val b: Int, val mat: UnlitMaterial)

    private val birds = ArrayList<BirdView>()
    private val branches = ArrayList<BranchView>()
    private val birdByEntity = HashMap<Long, Int>()
    private var dawnJob: Job? = null
    private var panelPosition = Vector3(1.2f, 1.35f, -1.3f)
    private var panelYaw = -52f

    /** Called once from SpatialView.initial (main thread). */
    suspend fun start() {
        buildLevel()
        audio.prepare(root)
    }

    fun panelPose(): Pair<Vector3, EulerAngles> = panelPosition to EulerAngles(0f, panelYaw, 0f)

    // ---- Level building -------------------------------------------------------------------

    private fun buildLevel() {
        dawnJob?.cancel()
        dawnJob = null
        birds.forEach { it.root.destroy(true) }
        birds.clear()
        branches.forEach { _ -> }
        root.getChildren().filter { it.getName() == BRANCH }.forEach { it.destroy(true) }
        branches.clear()
        birdByEntity.clear()

        val level = levels[levelIndex]
        session = GroveSession(level.grove, level.maxSong)
        val scene = GroveLayout.layout(level.grove)
        scene.panel.let {
            panelPosition = Vector3(it.x.toFloat(), it.y.toFloat(), it.z.toFloat())
            panelYaw = -Math.toDegrees(atan2(it.x, -it.z)).toFloat()
        }

        for (br in scene.branches) {
            val mat = unlit(Palette.branch)
            val e = ModelEntity(branchMesh, mat).apply {
                setName(BRANCH)
                components[TransformComponent::class.java]?.apply {
                    position = br.center.toVec()
                    quaternion = Quat(br.rotation.x.toFloat(), br.rotation.y.toFloat(), br.rotation.z.toFloat(), br.rotation.w.toFloat())
                    scaleVector = Vector3(1f, br.length.toFloat(), 1f)
                }
            }
            root.addChild(e)
            branches.add(BranchView(br.a, br.b, mat))
        }

        level.grove.birds.forEachIndexed { i, bird ->
            val p = scene.birds[i].toVec()
            birds.add(makeBird(bird, p))
        }
        refreshBirds()
        updateUi()
    }

    private fun makeBird(bird: Bird, p: Vector3): BirdView {
        val yaw = -Math.toDegrees(atan2(p.x.toDouble(), -p.z.toDouble())).toFloat()
        val birdRoot = Entity().apply {
            setName("bird-${bird.id}")
            components[TransformComponent::class.java]?.apply {
                position = p
                eulerAngles = EulerAngles(0f, yaw, 0f)
            }
            // A collider larger than the bird makes it easy to select with a ray or a mouse.
            components.set(
                CollisionComponent(
                    collisionShape = listOf(ShapeResource.createSphere(0.09f)),
                    physicsMaterial = PhysicsMaterialResource(),
                )
            )
            components.set(InteractableComponent())
            components.set(HoverEffectComponent())
        }
        val bodyMat = unlit(Palette.asleepFor(bird.kind))
        val body = ModelEntity(bodyMesh, bodyMat)
        birdRoot.addChild(body)
        body.addChild(ModelEntity(headMesh, bodyMat).at(Vector3(0f, 0.052f, 0.018f)))
        body.addChild(
            ModelEntity(beakMesh, beakMat).at(Vector3(0f, 0.05f, 0.055f)).also {
                it.components[TransformComponent::class.java]?.eulerAngles = EulerAngles(90f, 0f, 0f)
            }
        )
        if (bird.kind == Kind.ROOSTER) body.addChild(ModelEntity(combMesh, combMat).at(Vector3(0f, 0.088f, 0.015f)))

        val ring = ModelEntity(ringMesh, ringMat).at(Vector3(0f, -0.004f, 0f)).apply { enabled = false }
        birdRoot.addChild(ring)
        val note = ModelEntity(noteMesh, noteMat).at(Vector3(0.045f, 0.12f, 0.015f)).apply { enabled = false }
        birdRoot.addChild(note)

        val labelText = when (bird.kind) {
            Kind.LARK -> "sings"
            Kind.OWL -> Labels.needText(bird.need) + " owl"
            Kind.ROOSTER -> Labels.needText(bird.need) + " x2"
            Kind.PLAIN -> Labels.needText(bird.need)
        }
        val label = ModelEntity(labels.plane, labels.materialFor(labelText)).at(Vector3(0f, 0.15f, 0f)).apply {
            components.set(LookAtComponent().apply {
                setViewerAsTarget()
                lookAtForwardDirection = LookAtForwardDirection.POSITIVE_Z
            })
        }
        birdRoot.addChild(label)

        root.addChild(birdRoot)
        birdByEntity[birdRoot.id] = bird.id
        return BirdView(bird, birdRoot, body, bodyMat, ring, note, p)
    }

    // ---- Player actions -------------------------------------------------------------------

    fun onTap(target: Entity) {
        var e: Entity? = target
        var id: Int? = null
        while (e != null && id == null) {
            id = birdByEntity[e.id]
            e = e.getParent()
        }
        val birdId = id ?: return
        if (session.phase is Phase.Dawning) return
        if (session.phase is Phase.Result) session = session.tryAgain()
        val result = session.tap(birdId)
        session = result.session
        ui.message = when (result.rejection) {
            Rejection.OWL_IGNORES_WHISTLES -> "Owls ignore whistles. Wake it with its neighbours."
            Rejection.LARK_ALREADY_SINGS -> "Larks sing at dawn on their own."
            Rejection.NO_WHISTLES_LEFT -> "No whistles left. Select a whistled bird to take its whistle back."
            else -> null
        }
        refreshBirds()
        updateUi()
    }

    fun clearWhistles() {
        if (session.phase is Phase.Result) session = session.tryAgain()
        session = session.clearWhistles()
        ui.message = null
        refreshBirds()
        updateUi()
    }

    fun tryAgain() {
        session = session.tryAgain()
        ui.message = null
        refreshBirds()
        updateUi()
    }

    fun nextLevel() {
        levelIndex = (levelIndex + 1) % levels.size
        ui.message = null
        buildLevel()
    }

    fun toggleRules() { ui.showRules = !ui.showRules }

    fun toggleList() { ui.showList = !ui.showList }

    /** Same as selecting the bird in the scene; used by the accessible list on the panel. */
    fun tapBird(birdId: Int) {
        birds.getOrNull(birdId)?.let { onTap(it.root) }
    }

    fun toggleSound() {
        audio.muted = !audio.muted
        ui.muted = audio.muted
    }

    fun dawn(scope: CoroutineScope) {
        if (session.phase != Phase.Placing) return
        session = session.startDawn()
        val dawning = session.phase as? Phase.Dawning ?: return
        ui.message = null
        updateUi()
        refreshBirds()
        dawnJob = scope.launch {
            val singing = BooleanArray(birds.size)
            for (cue in dawning.timeline) {
                for (b in cue.birds) singing[b] = true
                cue.birds.forEach { wake(it, scope) }
                lightBranches(cue.birds, singing, cue.index)
                audio.playWave(cue.index, centroid(cue.birds))
                delay(WAVE_INTERVAL_MS)
            }
            session = session.finishDawn()
            val result = session.phase as? Phase.Result
            if (result != null && result.sim.allSinging) {
                audio.playChord(centroid(birds.indices.toList()))
            }
            refreshBirds()
            updateUi()
        }
    }

    fun dispose() {
        dawnJob?.cancel()
        audio.release()
        root.destroy(true)
        globals.forEach { r -> try { r.close() } catch (_: Throwable) { } }
        globals.clear()
    }

    // ---- Rendering ------------------------------------------------------------------------

    private fun wake(birdId: Int, scope: CoroutineScope) {
        val view = birds.getOrNull(birdId) ?: return
        view.bodyMat.setBaseColor(Palette.singing)
        view.ring.enabled = false
        view.note.enabled = true
        scope.launch { pop(view.body) }
    }

    /** A short scale bounce when a bird starts singing (main-thread coroutine, about 16 ms per step). */
    private suspend fun pop(e: Entity) {
        val t = e.components[TransformComponent::class.java] ?: return
        val steps = listOf(1.12f, 1.24f, 1.32f, 1.26f, 1.16f, 1.08f, 1.02f, 1f)
        for (s in steps) {
            t.scaleVector = Vector3(s)
            delay(30)
        }
    }

    private fun lightBranches(newBirds: List<Int>, singing: BooleanArray, wave: Int) {
        if (wave == 0) return
        val fresh = newBirds.toSet()
        for (br in branches) {
            val aNew = br.a in fresh
            val bNew = br.b in fresh
            if ((aNew && singing[br.b] && !bNew) || (bNew && singing[br.a] && !aNew)) br.mat.setBaseColor(Palette.branchLit)
        }
    }

    /** Applies the current session to every bird and branch (placing state or result state). */
    private fun refreshBirds() {
        val phase = session.phase
        val sleeping = session.sleepingBirds().toSet()
        for (view in birds) {
            val id = view.bird.id
            val whistled = id in session.whistled
            when (phase) {
                Phase.Placing -> {
                    view.bodyMat.setBaseColor(if (whistled) Palette.whistled else Palette.asleepFor(view.bird.kind))
                    view.ring.enabled = whistled
                    view.note.enabled = view.bird.kind == Kind.LARK
                }
                is Phase.Dawning -> {
                    view.ring.enabled = whistled
                    if (view.bird.kind != Kind.LARK) {
                        view.bodyMat.setBaseColor(if (whistled) Palette.whistled else Palette.asleepFor(view.bird.kind))
                        view.note.enabled = false
                    }
                }
                is Phase.Result -> {
                    val asleep = id in sleeping
                    view.bodyMat.setBaseColor(if (asleep) Palette.stillAsleep else Palette.singing)
                    view.note.enabled = !asleep
                    view.ring.enabled = false
                }
            }
        }
        if (phase == Phase.Placing) branches.forEach { it.mat.setBaseColor(Palette.branch) }
    }

    private fun updateUi() {
        val level = levels[levelIndex]
        ui.whistlesTotal = level.grove.whistles
        ui.whistlesLeft = session.whistlesLeft
        ui.birdButtons = level.grove.birds.map { b ->
            val whistled = b.id in session.whistled
            val mark = when (b.kind) {
                Kind.LARK -> "lark"
                Kind.OWL -> "owl " + Labels.needText(b.need)
                Kind.ROOSTER -> Labels.needText(b.need) + " x2"
                Kind.PLAIN -> Labels.needText(b.need)
            }
            BirdButton(
                id = b.id,
                label = "${b.id + 1} $mark" + if (whistled) " ●" else "",
                description = birdLabel(level.grove, b, whistled, false),
                whistled = whistled,
            )
        }
        when (level) {
            is Level.Practice -> {
                ui.levelLabel = "Practice ${level.number} of ${TUTORIAL.size}"
                ui.title = level.level.title
                ui.subtitle = "A tiny grove to learn the rules."
            }
            is Level.Daily -> {
                val d = level.daily
                ui.levelLabel = "Today's grove"
                ui.title = "Songbranch #${d.number} · ${d.name}"
                ui.subtitle = "Guest: " + when (d.guest) {
                    Guest.ROOSTER -> "the rooster counts as two singers"
                    Guest.OWL -> "the owl ignores whistles"
                    Guest.LARK -> "the lark sings from the start"
                } + " · ${d.date}"
            }
        }
        when (val p = session.phase) {
            Phase.Placing -> {
                ui.phase = UiPhase.Placing
                ui.hint = when (level) {
                    is Level.Practice -> level.level.hint
                    is Level.Daily -> "Select birds to place ${level.grove.whistles} whistles, then press Dawn."
                }
            }
            is Phase.Dawning -> {
                ui.phase = UiPhase.Dawning
                ui.hint = "Listen: each wave wakes the next."
            }
            is Phase.Result -> {
                ui.hint = if (p.sim.allSinging) "Press Next grove for another puzzle." else "Select birds to move your whistles, or press Try again."
                val asleep = p.sim.singing.count { !it }
                ui.stars = p.stars
                ui.sparkline = p.sparkline
                ui.waveSizes = p.sim.waves.map { it.size }
                if (p.sim.allSinging) {
                    ui.phase = UiPhase.Won
                    ui.resultTitle = "The whole grove is singing!"
                    ui.resultDetail = (if (level is Level.Practice) level.level.done + " " else "") +
                        "Song: ${p.sim.song} waves (longest possible: ${level.maxSong}). Tries: ${session.attempts}."
                } else {
                    ui.phase = UiPhase.Failed
                    ui.resultTitle = if (asleep == 1) "1 bird is still asleep." else "$asleep birds are still asleep."
                    ui.resultDetail = if (level is Level.Practice) level.level.retry
                    else "Your whistles stay where they were: move one and try again."
                }
            }
        }
    }

    private fun centroid(ids: List<Int>): Vector3 {
        if (ids.isEmpty()) return Vector3(0f, 1.5f, -2f)
        var x = 0f; var y = 0f; var z = 0f
        for (i in ids) { val p = birds[i].position; x += p.x; y += p.y; z += p.z }
        return Vector3(x / ids.size, y / ids.size, z / ids.size)
    }

    private fun Entity.at(p: Vector3): Entity = apply { components[TransformComponent::class.java]?.position = p }

    private fun songbranch.spatial.Vec3.toVec() = Vector3(x.toFloat(), y.toFloat(), z.toFloat())

    companion object {
        private const val BRANCH = "branch"
        fun unlit(c: Color4): UnlitMaterial = UnlitMaterial.create().apply { setBaseColor(c) }
    }
}
