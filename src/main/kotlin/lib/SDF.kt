package lib

import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.shape.Rectangle
import kotlin.math.abs
import kotlin.math.sign


fun Rectangle.sdf3(other: Vector2): Vector3 {
    val dcx = center.x - other.x
    val dcy = center.y - other.y
    val adcx = abs(dcx)
    val adcy = abs(dcy)

    if (adcx < width / 2 && adcy < height / 2) {
        val sdx = adcx - width/2.0
        val sdy = adcy - height/2.0

        val sdx0 = sdx.coerceAtMost(0.0)
        val sdy0 = sdy.coerceAtMost(0.0)

        if (sdx0 >= sdy0) {
            return Vector3(sdx0 * sign(dcx), 0.0, sdx0, )
        } else {
            return Vector3(0.0, sdy0 * sign(dcy), sdy0)
        }
    } else if (adcx < width / 2) {
        return Vector2(0.0, (adcy - height / 2) * sign(-dcy)).let {
            Vector3(it.x, it.y, abs(it.y))
        }
    } else if (adcy < height / 2) {
        return Vector2((adcx - width / 2) * sign(-dcx), 0.0).let {
            Vector3(it.x, it.y, abs(it.x))
        }
    } else {
        return corners.map { t -> other - t }.minBy { it.length }.let {
            Vector3(it.x, it.y, it.length)
        }
    }

}

val Rectangle.corners get() = listOf(position(0.0, 0.0), position(1.0, 0.0), position(1.0, 1.0), position(0.0, 1.0))
/**
 * Return signed distance between [this] and [other]. Return values encodes direction in [Vector3.x] and [Vector3.y],
 * signed distance is encoded in [Vector3.z]
 */
fun Rectangle.sdf3(other: Rectangle): Vector3 {
    val dcx = center.x - other.center.x
    val dcy = center.y - other.center.y
    val adcx = abs(center.x - other.center.x)
    val adcy = abs(center.y - other.center.y)

    if (adcx < width / 2 + other.width / 2 && adcy < height / 2 + other.height / 2) {
        val sdx = adcx - width/2.0 - other.width / 2.0
        val sdy = adcy - height/2.0 - other.height / 2.0

        val sdx0 = sdx.coerceAtMost(0.0)
        val sdy0 = sdy.coerceAtMost(0.0)

        if (sdx0 >= sdy0) {
            return Vector3(sdx0 * sign(dcx), 0.0, sdx0, )
        } else {
            return Vector3(0.0, sdy0 * sign(dcy), sdy0)
        }
    } else if (adcx < width / 2 + other.width / 2) {
        return Vector2(0.0, (adcy - height / 2 - other.height / 2) * sign(-dcy)).let {
            Vector3(it.x, it.y, abs(it.y))
        }
    } else if (adcy < height / 2 + other.height / 2) {
        return Vector2((adcx - width / 2 - other.width / 2) * sign(-dcx), 0.0).let {
            Vector3(it.x, it.y, abs(it.x))
        }
    } else {
        return corners.flatMap { t -> other.corners.map { o -> o-t } }.minBy { it.length }.let {
            Vector3(it.x, it.y, it.length)
        }
    }
}