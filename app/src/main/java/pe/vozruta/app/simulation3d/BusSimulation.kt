package pe.vozruta.app.simulation3d

import pe.vozruta.app.model.BusStage

data class BusSimulation(
    val stage: BusStage = BusStage.ROUTE,
    val elapsed: Float = 0f,
    val running: Boolean = false,
    val speed: Int = 1,
    val announcements: Boolean = true
) {
    val fraction get() = (elapsed / stage.seconds).coerceIn(0f, 1f)
    val routeProgress get() = when (stage) {
        BusStage.TRAVEL -> fraction
        BusStage.ARRIVED -> 1f
        else -> 0f
    }

    fun tick(seconds: Float): BusSimulation {
        if (!running || stage == BusStage.ARRIVED) return this
        val time = elapsed + seconds * speed
        return if (time < stage.seconds) copy(elapsed = time) else {
            val next = BusStage.entries[stage.ordinal + 1]
            copy(stage = next, elapsed = time - stage.seconds, running = next != BusStage.ARRIVED)
        }
    }
}
