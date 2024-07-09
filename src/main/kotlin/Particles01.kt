import lab.clustering.particle
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.noise.scatter
import org.openrndr.extra.noise.uniform
import org.openrndr.launch
import particlesystem.createParticleSystem

fun main() = application {
    configure {
        width = 1920
        height = 1080
    }

    program {

        val ps = createParticleSystem(drawer.bounds)

        ps.particles = drawer.bounds.scatter(12.0, distanceToEdge = 100.0).map { particle(it, ps.maxParticleRadius, Int.uniform(-1, 2)) }


        ps.addCluster(0)
        ps.addCluster(1, ColorRGBa.BLUE)

        mouse.buttonUp.listen {
            launch {
                ps.join(0)
            }
        }

        extend {

            ps.update()
            ps.draw()
        }
    }
}
