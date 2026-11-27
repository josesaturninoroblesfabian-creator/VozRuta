package pe.vozruta.app.maps

import org.json.JSONObject
import pe.vozruta.app.model.Geo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

enum class TravelMode(val label: String, val profile: String) {
    WALK("A pie", "foot"), DRIVE("Conduciendo", "car"), PASSENGER("En un vehículo", "car")
}

data class Destination(val name: String, val point: Geo)
data class Maneuver(val text: String, val atMeters: Double)
data class StreetRoute(val points: List<Geo>, val meters: Double, val seconds: Double, val maneuvers: List<Maneuver>)
data class Projection(val along: Double, val distance: Double, val length: Double)

private fun longitudeDelta(a: Double, b: Double) = ((a - b + 540) % 360) - 180

fun projectOnRoute(location: Geo, points: List<Geo>): Projection? {
    var distance = Double.POSITIVE_INFINITY
    var along = 0.0
    var total = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val xScale = 111_195 * cos(Math.toRadians(a.lat)).coerceAtLeast(0.0001)
        val dx = longitudeDelta(b.lng, a.lng) * xScale
        val dy = (b.lat - a.lat) * 111_195
        val px = longitudeDelta(location.lng, a.lng) * xScale
        val py = (location.lat - a.lat) * 111_195
        val length = hypot(dx, dy)
        if (length < .01) continue
        val ratio = ((px * dx + py * dy) / (length * length)).coerceIn(0.0, 1.0)
        val offset = hypot(px - ratio * dx, py - ratio * dy)
        if (offset < distance) {
            distance = offset
            along = total + length * ratio
        }
        total += length
    }
    return if (total > 0.0) Projection(along, distance, total) else null
}

class StreetRouting {
    fun route(start: Geo, end: Geo, mode: TravelMode): StreetRoute {
        waitForRequestSlot()
        val coordinates = "${start.lng},${start.lat};${end.lng},${end.lat}"
        val url = URL("https://routing.openstreetmap.de/routed-${mode.profile}/route/v1/driving/$coordinates?overview=full&geometries=polyline&steps=true")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", VOZRUTA_AGENT)
            val status = connection.responseCode
            if (status == 429) throw IOException("El servicio de rutas está ocupado. Reintenta en unos segundos.")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { JSONObject(it.readText()) }
            if (response?.optString("code") in listOf("NoRoute", "NoSegment")) throw IOException("No se encontró un camino para este modo.")
            val route = response?.optJSONArray("routes")?.optJSONObject(0)
                ?: throw IOException("El servicio de rutas no respondió. Comprueba tu conexión.")
            val points = decodePolyline(route.getString("geometry"))
            if (points.size < 2) throw IOException("El destino ya está cerca de tu ubicación.")
            val maneuvers = buildList {
                var at = 0.0
                val legs = route.getJSONArray("legs")
                for (leg in 0 until legs.length()) {
                    val steps = legs.getJSONObject(leg).getJSONArray("steps")
                    for (i in 0 until steps.length()) {
                        val step = steps.getJSONObject(i)
                        add(Maneuver(instruction(step), at))
                        at += step.optDouble("distance", 0.0)
                    }
                }
            }
            return StreetRoute(points, route.getDouble("distance"), route.getDouble("duration"), maneuvers)
        } finally {
            connection.disconnect()
        }
    }

    // The public demonstration router allows at most one request per second.
    @Synchronized private fun waitForRequestSlot() {
        val elapsed = (System.nanoTime() - lastRequest) / 1_000_000
        if (elapsed in 0..1_099) Thread.sleep(1_100 - elapsed)
        lastRequest = System.nanoTime()
    }

    private var lastRequest = 0L

    private fun instruction(step: JSONObject): String {
        val maneuver = step.getJSONObject("maneuver")
        val type = maneuver.optString("type")
        val turn = when (maneuver.optString("modifier")) {
            "left", "sharp left" -> "Gira a la izquierda"
            "right", "sharp right" -> "Gira a la derecha"
            "slight left" -> "Mantente a la izquierda"
            "slight right" -> "Mantente a la derecha"
            "uturn" -> "Da la vuelta si está permitido"
            else -> "Sigue recto"
        }
        val name = step.optString("name").filterNot { it.isISOControl() }.take(90)
        val road = name.takeIf(String::isNotBlank)?.let { " por $it" }.orEmpty()
        return when (type) {
            "arrive" -> "Destino al final del tramo"
            "depart" -> "Inicia el recorrido$road"
            "roundabout", "rotary" -> maneuver.optInt("exit").takeIf { it > 0 }
                ?.let { "En la rotonda, toma la salida $it$road" } ?: "Entra en la rotonda"
            "exit roundabout", "exit rotary" -> "Sal de la rotonda$road"
            "merge" -> "Incorpórate$road"
            "fork", "on ramp", "off ramp", "turn", "end of road", "continue" -> "$turn$road"
            else -> "Continúa$road"
        }
    }
}

internal const val VOZRUTA_AGENT = "VozRuta/0.2 (Android; https://github.com/josesaturninoroblesfabian-creator/VozRuta)"

fun parseCoordinates(text: String): Geo? {
    val parts = text.trim().split(',')
    if (parts.size != 2) return null
    val lat = parts[0].trim().toDoubleOrNull() ?: return null
    val lng = parts[1].trim().toDoubleOrNull() ?: return null
    return Geo(lat, lng).takeIf { lat in -85.0..85.0 && lng in -180.0..180.0 }
}
