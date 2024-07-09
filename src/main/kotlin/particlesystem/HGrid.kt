package lab.clustering

import org.openrndr.math.Vector2
import kotlin.math.ceil

class Hgrid(maxRadius: Double, width: Double) {

    val lookUpGridSize = ceil(maxRadius * 2.0).toInt()
    val invLookupGridSize = 1.0 / lookUpGridSize
    val lookUpWidth = ceil(width.toDouble() / lookUpGridSize).toInt()
    var lookup = mutableMapOf<Int, MutableList<Particle>>()

    fun lookupAtIndex(i: Int): List<Particle> {
        return lookup[i] ?: emptyList()
    }

    fun getLookUpIndex(x: Double, y: Double): Int {
        return (y * invLookupGridSize).toInt() * lookUpWidth + (x * invLookupGridSize).toInt()
    }

    fun getLookUpRadius(x: Double, y: Double, r: Int = 1): List<Particle> {
        val result = mutableListOf<Particle>()
        val lookupIndex = getLookUpIndex(x, y)
        for (j in -r..r) {
            for (i in -r..r) {
                val partialResult = lookupAtIndex(lookupIndex + j * lookUpWidth + i)
                result.addAll(partialResult)
            }
        }
        return result
    }

    fun addToLookUp(ball: Particle, lookupRef: MutableMap<Int, MutableList<Particle>>) {
        val lookupIndex = getLookUpIndex(ball.x, ball.y)
        val list = lookupRef.getOrPut(lookupIndex) { mutableListOf() }
        list.add(ball)
    }
}
