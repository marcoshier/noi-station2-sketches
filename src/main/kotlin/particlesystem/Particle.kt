package lab.clustering

import lib.sdf3
import org.openrndr.extra.noise.uniform
import org.openrndr.math.Vector2
import org.openrndr.math.mix
import org.openrndr.shape.Rectangle

fun particle(
    pos: Vector2,
    radius: Double = 10.0,
    type: Int = 0,
    damping: Double = 0.999,
    friction: Double = 0.1) = Particle(pos.x, pos.y, radius, type).apply {
    this.damping = damping
    this.friction = friction
}

data class Particle(
    var x: Double, var y: Double,
    var radius: Double = 10.0,
    var type: Int = Int.uniform(0, 4),
    var delayedx: Double = x, var delayedy: Double = y
) {

    val dpos: Vector2
        get() = Vector2(delayedx, delayedy)

    val pos: Vector2
        get() = Vector2(x, y)

    var oldx: Double = x
    var oldy: Double = y
    var nextx: Double = x
    var nexty: Double = y

    var taken = false

    fun addForce(x: Double, y: Double) {
        nextx += x
        nexty += y
    }

    fun attract(otherX: Double, otherY: Double, strength: Double = 1.0) {
        val diff = pos - Vector2(otherX, otherY)
        if (diff.squaredLength > 0.01) {
            val force = diff.normalized * strength
            addForce(force.x, force.y)
        }
    }

    fun repel(otherX: Double, otherY: Double, radius: Double = 1.0, strength: Double = 1.0) {
        val diff = pos - Vector2(otherX, otherY)
        if (diff.length > 0 && diff.length < radius + this.radius) {
            val force = diff.normalized * strength
            addForce(force.x, force.y)
        }
    }

    fun collideBox(r1: Rectangle, strength: Double) {
        val sdf = r1.offsetEdges(radius).sdf3(pos)
        if (sdf.z < 0.0) {
            addForce(sdf.x * strength, sdf.y * strength)
        }
    }

    fun collide(otherX: Double, otherY: Double, otherRadius: Double, strength: Double) {
        val diff = Vector2(otherX, otherY) - pos
        val combinedRadius = otherRadius + radius
        if (diff.length < combinedRadius) {
            val force = diff * ((diff.length - combinedRadius) / diff.length * 0.5 * strength)
            addForce(force.x, force.y)
        }
    }


    var damping: Double = 0.999
    var friction: Double = 0.1

    fun constrain(left: Double, top: Double, right: Double, bottom: Double) {
        val vx = (x - oldx) * friction
        val vy = (y - oldy) * friction

        val lr = left + radius
        val tr = top + radius
        val rr = right + radius
        val br = bottom + radius

        if (x > rr) {
            x = rr
            oldx = x + vx
        } else if (x < lr) {
            x = lr
            oldx = x + vx
        }

        if (y > br) {
            y = br
            oldy = y + vy
        } else if (y < tr) {
            y = tr
            oldy = y + vy
        }
    }

    fun update(dt: Double = 1.0, acc: Double = 1.0) {
        val vx = (x - oldx) * acc
        val vy = (y - oldy) * acc
        oldx = x - vx * damping * (1 - dt)
        oldy = y - vy * damping * (1 - dt)
        x = nextx + vx * damping * dt
        y = nexty + vy * damping * dt
        delayedx = mix(delayedx, x, 0.05)
        delayedy = mix(delayedy, y, 0.05)
        nextx = x
        nexty = y
    }
}