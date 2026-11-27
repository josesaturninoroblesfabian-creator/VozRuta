package pe.vozruta.app.maps

import org.json.JSONArray
import org.json.JSONObject
import pe.vozruta.app.BuildConfig
import pe.vozruta.app.model.BusStage
import pe.vozruta.app.model.Geo
import pe.vozruta.app.model.PilotTrip
import pe.vozruta.app.model.Profile
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Step(val title: String, val detail: String)
data class TransitPlan(val minutes: Int, val meters: Int, val fare: String?, val points: List<Geo>, val steps: List<Step>)
data class Boarding(val id: String, val board: String, val exit: String, val time: String, val profile: String, val stage: BusStage)

class TransitService {
    val isConfigured = BuildConfig.SERVICE_URL.isNotBlank() && BuildConfig.RIDER_TOKEN.isNotBlank()
    val operatorConfigured = BuildConfig.SERVICE_URL.isNotBlank() && BuildConfig.OPERATOR_TOKEN.isNotBlank()

    fun find(origin: String, destination: String, mode: String = "TRANSIT"): List<TransitPlan> {
        val body = JSONObject().put("origin", origin).put("destination", destination).put("mode", mode)
        val routes = call("/routes", body).optJSONArray("routes") ?: JSONArray()
        return (0 until routes.length()).map { index ->
            val route = routes.getJSONObject(index)
            val legs = route.optJSONArray("legs") ?: JSONArray()
            val steps = (0 until legs.length()).flatMap { leg ->
                val list = legs.getJSONObject(leg).optJSONArray("steps") ?: JSONArray()
                (0 until list.length()).map { i -> parseStep(list.getJSONObject(i)) }
            }
            val fare = route.optJSONObject("travelAdvisory")?.optJSONObject("transitFare")
            val amount = fare?.run {
                val sum = optString("units").toDoubleOrNull().orZero() + optInt("nanos") / 1_000_000_000.0
                "${getString("currencyCode")} ${String.format(java.util.Locale.US, "%.2f", sum)}"
            }
            val duration = route.optString("duration").removeSuffix("s").toDoubleOrNull().orZero()
            val line = route.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
            TransitPlan(kotlin.math.ceil(duration / 60).toInt(), route.optInt("distanceMeters"), amount, decodePolyline(line), steps)
        }
    }

    private fun parseStep(step: JSONObject): Step {
        val transit = step.optJSONObject("transitDetails")
        val instruction = step.optJSONObject("navigationInstruction")?.optString("instructions").orEmpty()
        if (transit == null) {
            val distance = step.optInt("distanceMeters")
            return Step(instruction.ifBlank { "Tramo a pie" }, "$distance m")
        }
        val line = transit.optJSONObject("transitLine")
        val vehicle = line?.optJSONObject("vehicle")?.optJSONObject("name")?.optString("text").orEmpty()
        val name = line?.optString("nameShort").orEmpty().ifBlank { line?.optString("name").orEmpty() }
        val stops = transit.optJSONObject("stopDetails")
        val departure = stops?.optJSONObject("departureStop")?.optString("name").orEmpty()
        val arrival = stops?.optJSONObject("arrivalStop")?.optString("name").orEmpty()
        val whenText = transit.optJSONObject("localizedValues")?.optJSONObject("departureTime")?.optString("time").orEmpty()
        return Step(listOf(vehicle.ifBlank { "Transporte público" }, name).filter { it.isNotBlank() }.joinToString(" "),
            listOf("Subida: $departure", "Bajada: $arrival", whenText).filter { it.isNotBlank() }.joinToString(" · "))
    }

    fun request(id: String, trip: PilotTrip, profile: Profile, time: String): Boarding {
        val body = JSONObject().put("id", id).put("board", trip.board.name).put("exit", trip.exit.name)
            .put("time", time).put("profile", profile.request)
        return boarding(call("/boarding", body))
    }

    fun status(id: String) = boarding(call("/boarding/$id"))

    fun queue(): List<Boarding> {
        val rows = call("/operator", operator = true).getJSONArray("requests")
        return (0 until rows.length()).map { boarding(rows.getJSONObject(it)) }
    }

    fun advance(item: Boarding, stage: BusStage) =
        boarding(call("/operator/${item.id}", JSONObject().put("stage", stage.name), operator = true))

    private fun boarding(data: JSONObject) = Boarding(data.getString("id"), data.getString("board"), data.getString("exit"),
        data.getString("time"), data.getString("profile"), BusStage.valueOf(data.getString("stage")))

    private fun call(path: String, body: JSONObject? = null, operator: Boolean = false): JSONObject {
        val connection = URL(BuildConfig.SERVICE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 18_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer " + if (operator) BuildConfig.OPERATOR_TOKEN else BuildConfig.RIDER_TOKEN)
            if (body != null) {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val data = stream?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            if (code !in 200..299) throw IOException(data.optString("error", "El servicio no está disponible."))
            return data
        } finally {
            connection.disconnect()
        }
    }
}

private fun Double?.orZero() = this ?: 0.0

internal fun decodePolyline(encoded: String): List<Geo> {
    var cursor = 0
    var lat = 0
    var lng = 0
    fun read(): Int {
        var result = 0
        var shift = 0
        while (cursor < encoded.length && shift <= 30) {
            val value = encoded[cursor++].code - 63
            require(value in 0..63)
            result = result or ((value and 31) shl shift)
            if (value < 32) return if (result and 1 == 1) (result shr 1).inv() else result shr 1
            shift += 5
        }
        throw IllegalArgumentException("Polilínea incompleta")
    }
    return buildList {
        while (cursor < encoded.length) {
            lat += read()
            lng += read()
            add(Geo(lat / 1e5, lng / 1e5))
        }
    }
}
