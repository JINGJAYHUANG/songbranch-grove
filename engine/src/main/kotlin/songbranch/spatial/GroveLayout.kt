// Places a grove's birds and branches in the room for the PICO stage.
// Pure Kotlin so it can be unit tested on the JVM; the app converts these values to SDK types.
// Units are metres. +Y is up, -Z is forward (away from the user), the floor is Y = 0.
package songbranch.spatial

import songbranch.engine.COLS
import songbranch.engine.Grove
import songbranch.engine.ROWS
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    fun length() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 { val l = length(); return if (l == 0.0) this else Vec3(x / l, y / l, z / l) }
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
}

/** Rotation as a unit quaternion (x, y, z, w). */
data class Quat(val x: Double, val y: Double, val z: Double, val w: Double) {
    companion object {
        val IDENTITY = Quat(0.0, 0.0, 0.0, 1.0)

        /** Shortest rotation that turns unit vector [from] into unit vector [to]. */
        fun between(from: Vec3, to: Vec3): Quat {
            val f = from.normalized()
            val t = to.normalized()
            val d = f.dot(t).coerceIn(-1.0, 1.0)
            if (d > 1.0 - 1e-9) return IDENTITY
            if (d < -1.0 + 1e-9) {
                // Opposite directions: rotate 180 degrees around any axis perpendicular to f.
                var axis = Vec3(1.0, 0.0, 0.0).cross(f)
                if (axis.length() < 1e-6) axis = Vec3(0.0, 0.0, 1.0).cross(f)
                val a = axis.normalized()
                return Quat(a.x, a.y, a.z, 0.0)
            }
            val axis = f.cross(t).normalized()
            val angle = acos(d)
            val s = sin(angle / 2)
            return Quat(axis.x * s, axis.y * s, axis.z * s, cos(angle / 2))
        }
    }

    /** Rotates vector [v] by this quaternion. */
    fun rotate(v: Vec3): Vec3 {
        val u = Vec3(x, y, z)
        val uv = u.cross(v)
        val uuv = u.cross(uv)
        return v + (uv * (2.0 * w)) + (uuv * 2.0)
    }
}

/** A branch drawn as a cylinder whose local +Y axis runs from bird [a] to bird [b]. */
data class BranchPose(val a: Int, val b: Int, val center: Vec3, val length: Double, val rotation: Quat)

data class GroveScene(val birds: List<Vec3>, val branches: List<BranchPose>, val panel: Vec3, val center: Vec3)

object GroveLayout {
    /** Distance from the user's standing point to the grove arc. */
    const val RADIUS = 2.0
    /** Horizontal spread of the grove: from -HALF_ANGLE to +HALF_ANGLE (radians). */
    val HALF_ANGLE = 24.0 * PI / 180.0
    /** The grove is centred a little to the left so the control panel fits on the right. */
    val CENTER_ANGLE = -10.0 * PI / 180.0
    /** Height of the lowest and highest rows. */
    const val LOW_Y = 1.0
    const val HIGH_Y = 2.25
    /** Small deterministic depth stagger so branches do not all lie on one surface. */
    const val DEPTH_STAGGER = 0.12

    fun birdPosition(x: Double, y: Double, id: Int): Vec3 {
        val u = (x / COLS).coerceIn(0.0, 1.0)         // 0 = left column edge, 1 = right
        val v = (y / ROWS).coerceIn(0.0, 1.0)         // 0 = top row edge, 1 = bottom
        val theta = CENTER_ANGLE + (u - 0.5) * 2.0 * HALF_ANGLE
        val r = RADIUS + DEPTH_STAGGER * sin(id * 2.3999632)   // golden-angle spread per bird id
        val height = HIGH_Y - v * (HIGH_Y - LOW_Y)
        return Vec3(r * sin(theta), height, -r * cos(theta))
    }

    fun layout(grove: Grove): GroveScene {
        val birds = grove.birds.map { birdPosition(it.x, it.y, it.id) }
        val branches = grove.links.map { (a, b) ->
            val pa = birds[a]
            val pb = birds[b]
            val d = pb - pa
            BranchPose(a, b, pa + d * 0.5, d.length(), Quat.between(Vec3(0.0, 1.0, 0.0), d))
        }
        val center = Vec3(RADIUS * sin(CENTER_ANGLE), (LOW_Y + HIGH_Y) / 2, -RADIUS * cos(CENTER_ANGLE))
        // The control panel sits to the user's front right, a little closer than the grove.
        val panelTheta = 20.0 * PI / 180.0
        val panel = Vec3(1.3 * sin(panelTheta), 1.45, -1.3 * cos(panelTheta))
        return GroveScene(birds, branches, panel, center)
    }
}
