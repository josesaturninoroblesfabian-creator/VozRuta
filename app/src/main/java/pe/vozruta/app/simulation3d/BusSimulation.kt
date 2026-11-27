package pe.vozruta.app.simulation3d

import pe.vozruta.app.model.BusStage
import kotlin.math.roundToInt
import kotlin.math.sin

data class BusSimulation(
    val stage: BusStage = BusStage.ROUTE,
    val elapsed: Float = 0f,
    val running: Boolean = false,
    val speed: Int = 1,
    val announcements: Boolean = true
) {
    val fraction get() = (elapsed / stage.seconds).coerceIn(0f, 1f)
    val routeProgress get() = when (stage) {
        BusStage.TRAVEL -> fraction * fraction * (3 - 2 * fraction)
        BusStage.ARRIVED -> 1f
        else -> 0f
    }
    val speedKmh get() = when (stage) {
        BusStage.ROUTE -> 36
        BusStage.APPROACH -> (46.8 * (1 - fraction)).roundToInt()
        BusStage.TRAVEL -> (32.4 * sin(fraction * Math.PI)).roundToInt()
        else -> 0
    }

    fun tick(seconds: Float): BusSimulation {
        if (!running) return this
        val time = elapsed + seconds * speed
        return if (time < stage.seconds) copy(elapsed = time) else if (stage == BusStage.ARRIVED) {
            copy(elapsed = stage.seconds.toFloat(), running = false)
        } else {
            val next = BusStage.entries[stage.ordinal + 1]
            copy(stage = next, elapsed = time - stage.seconds)
        }
    }
}
