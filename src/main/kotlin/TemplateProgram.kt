import lab.clustering.particle
import org.openrndr.application
import org.openrndr.extra.noise.scatter
import particlesystem.createParticleSystem

fun main() = application {
    configure {
        width = 1920
        height = 1080
    }

    program {

        val ps = createParticleSystem(drawer.bounds)

        ps.particles = drawer.bounds.scatter(12.0, distanceToEdge = 100.0).map { particle(it, ps.maxParticleRadius) }

        ps.addCluster(0)

        extend {

            ps.update()
            ps.draw()
        }
    }
}
