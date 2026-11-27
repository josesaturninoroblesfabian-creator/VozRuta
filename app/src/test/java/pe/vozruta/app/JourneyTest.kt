package pe.vozruta.app

import org.junit.Assert.*
import org.junit.Test
import pe.vozruta.app.maps.decodePolyline
import pe.vozruta.app.model.BusStage
import pe.vozruta.app.model.Geo
import pe.vozruta.app.model.Pilot
import pe.vozruta.app.model.pointAlong
import pe.vozruta.app.simulation3d.BusSimulation
import java.time.LocalTime

class JourneyTest {
    @Test fun resolvesAccentsAndAlternateNames() {
        assertEquals("hospital", Pilot.stop("Ir al Hospital Hermílio Valdizán")?.id)
        assertEquals("nuevo", Pilot.stop("Llévame al Mercado Modelo")?.id)
        assertEquals("yanag", Pilot.stop("Ir a El Cuartel")?.id)
        assertNull(Pilot.stop("Rambouillet, Francia"))
    }

    @Test fun manualBoardingSupportsBothDirections() {
        val first = Pilot.stops.first()
        val last = Pilot.stops.last()
        val forward = Pilot.trip(last, null, first)
        val backward = Pilot.trip(first, null, last)
        assertEquals(forward.stops.asReversed(), backward.stops)
        assertEquals(forward.minutes, backward.minutes)
        assertEquals("06:00", forward.departure(LocalTime.of(5, 59)))
        assertEquals("06:30", forward.departure(LocalTime.of(6, 0, 1)))
        assertEquals("06:00 mañana", forward.departure(LocalTime.of(23, 59)))
        assertNull(Pilot.nearest(Geo(48.85, 2.35)))
    }

    @Test fun interpolatesByDistanceAndDecodesSignedOffsets() {
        val route = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(Geo(38.5, -120.2), Geo(40.7, -120.95), Geo(43.252, -126.453)), route)
        val line = listOf(Geo(0.0, 0.0), Geo(0.0, 1.0), Geo(0.0, 4.0))
        assertEquals(2.0, pointAlong(line, .5f)!!.lng, 0.00001)
    }

    @Test fun reachesArrivalAndStopsAtDoubleSpeed() {
        var simulation = BusSimulation(running = true, speed = 2)
        val observed = mutableSetOf(simulation.stage)
        repeat(300) {
            simulation = simulation.tick(.1f)
            observed += simulation.stage
        }
        assertEquals(BusStage.entries.toSet(), observed)
        assertEquals(BusStage.ARRIVED, simulation.stage)
        assertFalse(simulation.running)
        assertEquals(1f, simulation.routeProgress, 0.00001f)
    }
}
