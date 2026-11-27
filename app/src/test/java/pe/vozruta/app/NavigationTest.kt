package pe.vozruta.app

import org.junit.Assert.*
import org.junit.Test
import pe.vozruta.app.maps.parseCoordinates
import pe.vozruta.app.maps.projectOnRoute
import pe.vozruta.app.model.Geo
import pe.vozruta.app.model.Pilot

class NavigationTest {
    @Test fun projectsPositionAndDetectsASideStreet() {
        val route = listOf(Geo(0.0, 0.0), Geo(0.0, .001), Geo(.001, .001))
        val position = projectOnRoute(Geo(.0004, .0011), route)!!
        assertEquals(156.0, position.along, 1.0)
        assertEquals(11.12, position.distance, .2)
        assertEquals(222.39, position.length, .2)
    }

    @Test fun handlesDateLineAndDegeneratePaths() {
        val route = listOf(Geo(0.0, 179.999), Geo(0.0, -179.999))
        val position = projectOnRoute(Geo(0.0, 180.0), route)!!
        assertEquals(111.2, position.along, .2)
        assertEquals(0.0, position.distance, .01)
        assertNull(projectOnRoute(route.first(), listOf(route.first(), route.first())))
    }

    @Test fun validatesCoordinatesAndKeepsBoardingWithinSelectedRoute() {
        assertEquals(Geo(-9.93, -76.24), parseCoordinates("-9.93, -76.24"))
        assertNull(parseCoordinates("NaN,-76.24"))
        assertNull(parseCoordinates("-9.93,181"))
        val city = Pilot.network(1)
        val trip = Pilot.trip(city.last(), Geo(-10.1, -76.2), Pilot.stops.last(), 1)
        assertEquals(city.first(), trip.board)
        assertEquals(city, trip.stops)
        assertEquals(18, trip.minutes)
        assertEquals("calicanto", Pilot.stop("Ir a Puente Calicanto")?.id)
    }
}
