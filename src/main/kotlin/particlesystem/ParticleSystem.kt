package particlesystem

import kotlinx.coroutines.yield
import lab.clustering.Hgrid
import lab.clustering.Particle
import lib.*
import org.intellij.lang.annotations.Language
import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.fx.edges.Contour
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.shapes.hobbycurve.hobbyCurve
import org.openrndr.math.*
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Segment2D
import org.openrndr.shape.bounds
import kotlin.math.*
import kotlin.random.Random

fun Program.createParticleSystem(bounds: Rectangle, maxParticleRadius: Double = 20.0): ParticleSystem {
    return ParticleSystem(this, bounds, maxParticleRadius)
}

class ParticleSystem(val program: Program, val bounds: Rectangle, val maxParticleRadius: Double) {

    var particles = listOf<Particle>()


    var groupedByType = particles.groupBy { it.type }
    var clusterMeans = groupedByType.mapValues { it.value.map { it.pos }.sum() / it.value.size.toDouble() }
    var clusterRadii = groupedByType.mapValues { (key, value) ->
        val cm = clusterMeans[key]!!
        value.maxBy { it.pos.distanceTo(cm) }.pos.distanceTo(cm)
    }

    private var busy = false

    suspend fun split(
        items: List<Any>,
        itemWeights: List<Double> = items.map { 1.0 },
        colors: List<ColorRGBa> = items.map { ColorRGBa.WHITE },
        targets: List<Vector2?> = items.map { null }, ) {

        if (!busy) {
            busy = true

            val weights = clusters.map { it.value.weight } + itemWeights

            val clusteredParticles = particles

            val weightSum = weights.sum()
            val weightedPartitions = List(weights.size) { ceil((weights[it] / weightSum) * clusteredParticles.size).toInt() }

            val cmeans = clusteredParticles.map { Vector2(it.x, it.y) }.sum() / clusteredParticles.size.toDouble()
            val sorted = clusteredParticles.indices.sortedBy { atan2(clusteredParticles[it].y - cmeans.y, clusteredParticles[it].x - cmeans.x).asDegrees + 180.0 }
            val intervals = mutableListOf<List<Pair<Int, Particle>>>()

            var acc = 0
            for ((i, current) in weightedPartitions.withIndex()) {
                val l = sorted.drop(acc).take(current).map { idx -> idx to clusteredParticles[idx] }
                val localCenter = l.map { Vector2(it.second.x, it.second.y) }.sum() / l.size.toDouble()
                intervals.add(l.sortedBy { Vector2(it.second.x, it.second.y).distanceTo(localCenter) })

                acc += current
            }

            for ((i, l) in intervals.withIndex()) {

                addCluster(i, colors[i], targets[i], weights[i])

                for ((j, iparticle) in l.withIndex()) {
                    val (idx, _) = iparticle

                    particles[idx].type = i
                    if (i > 0) {
                        if (j % 8 == 0) {
                            yield()
                        }
                    }

                }

                if (i > 0) {
                    for (x in 0..10) {
                        yield()
                    }
                }

            }


            busy = false
        }

    }

    val isSplit: Boolean
        get() = groupedByType.keys.size > 1

    suspend fun join(idx: Int = 0) {
        if (isSplit && !busy) {
            busy = true

            val toRemove = mutableSetOf<Int>()
            for (i in particles.indices) {
                val t = particles[i].type
                if (t != -1) {
                    particles[i].type = idx

                    if (t != idx) toRemove.add(t)

                    if (i % 10 == 0)
                        yield()
                }
            }


            toRemove.forEach {
                removeCluster(it)
            }

            busy = false
        }
    }


    var boxes = mutableListOf<Rectangle>()


    private val hgrid = Hgrid(maxParticleRadius, bounds.width * 1.0)

    fun update(iterations: Int = 10) {

        groupedByType = particles.groupBy { it.type }
        clusterMeans = groupedByType.mapValues { it.value.map { it.pos }.sum() / it.value.size.toDouble() }
        clusterRadii = groupedByType.mapValues { (key, value) ->
            val cm = clusterMeans[key]!!
            value.maxBy { it.pos.distanceTo(cm) }.pos.distanceTo(cm)
        }

        val radiiFromCenter = groupedByType.mapValues { (key, value) ->
            val cm = bounds.center
            value.maxBy { it.pos.distanceTo(cm) }.pos.distanceTo(cm)
        }


        for (iter in 0 until iterations) {
            val nextLookup = mutableMapOf<Int, MutableList<Particle>>()

            particles = particles.mapIndexed { i, p ->

                val np = p.copy()

                val closeBy = hgrid.getLookUpRadius(np.x, np.y, 1)
                closeBy.forEach { otherP ->

                    if (p !== otherP) {
                        if (p.type != otherP.type) {
                            np.repel(
                                otherP.x,
                                otherP.y,
                                (otherP.radius) + maxParticleRadius * 0.05,
                                0.01
                            )
                        } else {
                            np.repel(
                                otherP.x,
                                otherP.y,
                                (otherP.radius) + maxParticleRadius * 0.02,
                                -1.5
                            )
                        }

                        np.collide(otherP.x, otherP.y, otherP.radius, 1.0)
                    }

                }

                if (clusters[np.type]?.active == true) {
                    val mean = clusterMeans[np.type]!!

                    val md = mean.distanceTo(np.pos)
                        .map(300.0, 0.0, 1.0, 0.0, true)
                        .smoothstep(2.0, 3.0)

                    val cd = bounds.center
                        .distanceTo(np.pos)
                        .map(radiiFromCenter[np.type]!!, 0.0, 1.0, 0.0, true)
                        .smoothstep(0.2, 1.0)


                    np.attract(mean.x, mean.y, -2.0 * md)
                    np.attract(bounds.center.x, bounds.center.y, -0.4 * cd)
                    val target = clusters[np.type]?.target
                    target?.let {

                        val td = bounds.center
                            .distanceTo(np.pos)
                            .map(100.0, 0.0, 0.5, 0.0, true)
                            .smoothstep(0.2, 1.0)

                        np.attract(it.x, it.y, -1.0 * td)
                    }

                } else {
                    np.attract(bounds.center.x, bounds.center.y,  sin((Double.uniform(0.0, 1.0, Random(i)) * 2 * PI) + program.seconds * 0.05 * 2 * PI) * 0.75)
                }

                for (b in boxes) {
                    np.collideBox(b, 3.0)
                }

                np.constrain(0.0, 0.0, bounds.width, bounds.height)
                np.update(0.1, 0.1)

                hgrid.addToLookUp(np, nextLookup)

                np
            }

            hgrid.lookup = nextLookup
        }

        boxes.clear()

    }


    ////  DRAW

    inner class Cluster {
        lateinit var color: ColorRGBa
        lateinit var contour: ColorBuffer
        lateinit var density: ColorBuffer

        var target: Vector2? = null
        var weight: Double = 1.0

        var active = false
    }

    val clusters = mutableMapOf<Int, Cluster>()

    fun addCluster(type: Int, color: ColorRGBa = ColorRGBa.WHITE, target: Vector2? = null, weight: Double = 1.0) {
        val c = clusters.getOrPut(type) {
            Cluster()
        }

        c.color = color
        c.contour = colorBuffer(bounds.width.toInt(), bounds.height.toInt())
        c.density = colorBuffer(bounds.width.toInt(), bounds.height.toInt(), type = ColorType.FLOAT32)
        c.weight = weight
        c.target = target
        c.active = true
    }

    fun removeCluster(type: Int) {
        clusters[type]?.let {
            it.active = false
            it.contour.destroy()
        }
        clusters.remove(type)
    }


    private val contour = Contour().apply {
        bias = -0.225
        backgroundOpacity = 0.0
        contourWidth = 1.0
    }
    private val rt = renderTarget(bounds.width.toInt(), bounds.height.toInt()) {
        colorBuffer(type = ColorType.FLOAT32)
        depthBuffer()
    }

    private val blob = program.drawImage(256, 256, type = ColorType.FLOAT32) {
        program.run {
            drawer.shadeStyle = shadeStyle { fragmentTransform = blobSS }
            drawer.stroke = null
            drawer.rectangle(0.0, 0.0, 256.0, 256.0)
        }
    }

    val densityGradient = DensityGradient()

    fun draw() {

        program.run {

            val positions = groupedByType.mapValues { it.value.map { it.dpos } }

            groupedByType.forEach { (type, gparticles) ->
                val cluster = clusters[type]

                if (cluster != null && clusterMeans[type] != null) {
                    drawer.drawStyle.blendMode = BlendMode.BLEND

                    drawer.stroke = null

                    val ss = shadeStyle {
                        fragmentPreamble = densitySSpreamble
                        fragmentTransform = densitySS
                    }


                    ss.parameter("iTime", seconds)
                    ss.parameter("type", type)
                    ss.parameter("densities", clusters.map { it.value.density }.toTypedArray())

                    drawer.shadeStyle = ss
                    drawer.rectangles {
                        for (b in gparticles) {
                            fill = cluster.color.mix(ColorRGBa.BLACK, smoothstep(0.0, 0.8,  map(0.0, 200.0, 1.0, 0.0, clusterMeans[type]!!.distanceTo(b.pos))))
                            rectangle(Rectangle.fromCenter(Vector2(b.delayedx, b.delayedy), 3.0 * b.radius * sqrt(2.0)))
                        }
                    }

                    drawer.shadeStyle = null


                }
            }


            for ((i, cluster) in clusters) {

                contour.contourColor = cluster.color.toLinear()

                drawer.isolatedWithTarget(rt) {
                    drawer.clear(ColorRGBa.WHITE.shade(0.01))

                    drawer.drawStyle.blendMode = BlendMode.ADD
                    for (b in positions[i] ?: emptyList()) {
                        drawer.image(blob, b.x - 128.0, b.y - 128.0)
                    }
                }

                densityGradient.apply(rt.colorBuffer(0), cluster.density)
                contour.levels = 0.15
                contour.apply(rt.colorBuffer(0), cluster.contour)

                if (cluster.active) {
                    drawer.image(cluster.contour)
                }
            }

        }

    }
}

fun String.take(t: Double): String {
    return take((length * t).toInt().coerceIn(0, length))
}

private val blobSS =  """
    float d = length(va_texCoord0.xy - vec2(0.5));
    float sd = exp(-d*10.0) * 0.2;
    //float sd = max(0.0, 0.5-d)*2.0;
    x_fill = vec4(vec3(sd), 1.0);
"""

private val densitySSpreamble = """
    float line_segment(in vec2 p, in vec2 a, in vec2 b) {
	vec2 ba = b - a;
	vec2 pa = p - a;
	float h = clamp(dot(pa, ba) / dot(ba, ba), 0., 1.);
	return length(pa - h * ba);
}
                                    """.trimIndent()

@Language("GLSL")
private val densitySS = """
    vec3 densities[p_densities.length];
    vec2 ts = vec2(textureSize(p_density0, 0));
    vec2 uv = c_screenPosition/ts;
    uv.y = 1.0 - uv.y;
    
    for (int i = 0; i < p_densities.length; ++i) {
        densities[i] = texture(p_densities[i], uv).rgb;
    }

    //float ed = length(va_texCoord0 - vec2(0.5));
    
    float contrast = 0.0;
    vec2 contrastDir = vec2(0.0);
    for (int i = 0; i < 7; ++i) {
        if (i != p_type) {
            contrast += densities[i].r;
            contrastDir += densities[i].gb;
        }
    }

    float l = cos(p_iTime*3.14 + float(c_instance) * 0.2) * 0.5 + 0.5;
    float l2 = cos(p_iTime*3.14 + float(c_instance) * 0.312) * 0.5 + 0.5;

    float ed = line_segment( (va_texCoord0 - vec2(0.5)) * 2.0, vec2(0.0) - contrastDir*1.0 * l, contrastDir*1.0 * l2); 
    
    
    
    float blur = 0.01;// pow(densities[p_type].r, 2.0)*0.25;
    //float sharpness = 1.0 / (1.0 + densities[p_type]);
    float size = cos(float(p_type)*2.412) * 0.15 + 0.25;
    float sd = smoothstep( min(1.0, size+blur), size, ed);// * smoothstep(-0.1, 0.0, ed-(cos(float(p_type) + p_iTime + float(c_instance)*0.01 )*0.1+0.1));
    //x_fill.rgb = mix(x_fill.rgb, vec3(1.0), clamp(1.0 - ed*8.0, 0.0, 1.0));
    x_fill.rgb *=  pow(dot(normalize(densities[p_type].rg), 2.0 * (va_texCoord0 - vec2(0.5))),1.0) * 0.8 + 0.8;
    
//                                        x_fill.rg = contrastDir;
    //x_fill.rgb *= exp(-max(0.0, d)*sharpness);
    //x_fill.rgb = vec3(densities[0]*1.0);
    x_fill.rgb *= 1.0/0.9;
     x_fill.a *= sd * 0.9;
""".trimIndent()

