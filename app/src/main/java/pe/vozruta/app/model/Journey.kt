package pe.vozruta.app.model

import java.text.Normalizer
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

data class Geo(val lat: Double, val lng: Double) {
    val coordinate get() = "$lat,$lng"

    fun metersTo(other: Geo): Double {
        val a = sin(Math.toRadians(other.lat - lat) / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) *
            sin(Math.toRadians(other.lng - lng) / 2).pow(2)
        return 12_742_000 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun directionTo(other: Geo): String {
        val delta = Math.toRadians(other.lng - lng)
        val first = Math.toRadians(lat)
        val second = Math.toRadians(other.lat)
        val bearing = Math.toDegrees(atan2(sin(delta) * cos(second),
            cos(first) * sin(second) - sin(first) * cos(second) * cos(delta)))
        val directions = listOf("norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste")
        return directions[((bearing + 360 + 22.5) / 45).toInt() % directions.size]
    }
}

data class Stop(val id: String, val name: String, val area: String, val geo: Geo, val minute: Int, val aliases: List<String>)

enum class Profile(val title: String, val detail: String, val request: String) {
    GENERAL("General", "Indicaciones de viaje y avisos de parada.", "apoyo de abordaje"),
    VISUAL("Guía auditiva", "Guía verbal, asiento preferente y aviso sonoro de parada.", "guía verbal y hasta el asiento"),
    WHEELCHAIR("Silla de ruedas", "Rampa, espacio de maniobra y sujeción de la silla.", "rampa y anclaje de silla")
}

enum class BusStage(val title: String, val seconds: Int, val message: String) {
    ROUTE("Bus en ruta", 7, "La unidad está en camino al paradero de subida."),
    APPROACH("Bus acercándose", 5, "La unidad se acerca. Espera en una zona segura del paradero."),
    RAMP("Rampa desplegada", 6, "La unidad está detenida. Puertas abiertas y rampa desplegada."),
    ASSIST("Asistencia a bordo", 7, "El asistente te acompaña y comprueba que estés en una posición segura."),
    TRAVEL("En traslado", 18, "Continuamos el viaje. Avisaremos cuando nos acerquemos a tu destino."),
    ARRIVED("Llegada a destino", 1, "Llegamos a tu destino. Espera el apoyo del asistente para bajar.");

    fun messageFor(profile: Profile): String = if (this != ASSIST) message else when (profile) {
        Profile.WHEELCHAIR -> "El asistente acompaña el abordaje y verifica el anclaje de la silla antes de salir."
        Profile.VISUAL -> "El asistente ofrece su brazo y guía al pasajero hasta un asiento seguro."
        Profile.GENERAL -> message
    }
}

data class PilotTrip(val board: Stop, val exit: Stop, val stops: List<Stop>, val fare: String, val minutes: Int) {
    fun departure(now: LocalTime = LocalTime.now(ZoneId.of("America/Lima"))): String {
        val offset = if (board.minute <= exit.minute) board.minute else Pilot.stops.last().minute - board.minute
        val first = 6 * 60 + offset
        val current = now.hour * 60 + now.minute + if (now.second > 0 || now.nano > 0) 1 else 0
        val departure = ((current - first).coerceAtLeast(0) + 29) / 30
        val nextDay = departure >= 29
        val minutes = first + if (nextDay) 0 else departure * 30
        val hour = LocalTime.ofSecondOfDay(minutes * 60L)
        return hour.format(DateTimeFormatter.ofPattern("HH:mm")) + if (nextDay) " mañana" else ""
    }
}

object Pilot {
    val stops = listOf(
        Stop("viejo", "Mercado Viejo", "Huánuco", Geo(-9.93149, -76.24007), 0, listOf("mercado viejo", "mercado central")),
        Stop("hospital", "Hospital Regional", "Huánuco", Geo(-9.92796, -76.23618), 5, listOf("hospital", "hermilio valdizan")),
        Stop("nuevo", "Mercado Nuevo", "Huánuco · Mercado Modelo", Geo(-9.92947, -76.24367), 10, listOf("mercado nuevo", "mercado modelo")),
        Stop("amarilis", "Amarilis", "Paucarbamba", Geo(-9.94167, -76.24028), 17, listOf("amarilis", "paucarbamba")),
        Stop("unheval", "UNHEVAL", "Pillco Marca", Geo(-9.94826, -76.24996), 24, listOf("unheval", "universidad", "un eval")),
        Stop("rosavero", "Rosavero", "Pillco Marca", Geo(-9.99753, -76.24758), 35, listOf("rosavero", "rosa vero")),
        Stop("yanag", "Yanag / El Cuartel", "Pillco Marca", Geo(-9.98861, -76.24407), 40, listOf("yanag", "el cuartel", "yanac")),
        Stop("conchamarca", "Entrada a Conchamarca", "Conchamarca", Geo(-10.03576, -76.21657), 52, listOf("conchamarca")),
        Stop("ambo", "Mercado de Ambo", "Ambo", Geo(-10.12685, -76.20496), 68, listOf("ambo", "mercado de ambo"))
    )

    fun stop(text: String): Stop? {
        val words = " ${normalize(text)} "
        return stops.firstOrNull { stop -> stop.aliases.any { " $it " in words } }
    }

    fun nearest(location: Geo, excluded: Stop? = null): Stop? =
        stops.asSequence().filter { it != excluded }.minByOrNull { it.geo.metersTo(location) }
            ?.takeIf { it.geo.metersTo(location) <= 3_000 }

    fun trip(exit: Stop, location: Geo?, board: Stop? = null): PilotTrip {
        val origin = board?.takeUnless { it == exit } ?: location?.let { nearest(it, exit) } ?: stops.first { it != exit }
        val from = stops.indexOf(origin)
        val to = stops.indexOf(exit)
        val path = if (from < to) stops.subList(from, to + 1) else stops.subList(to, from + 1).asReversed()
        val minutes = abs(origin.minute - exit.minute)
        val fare = if (minutes <= 24) "S/ 2,00" else if (minutes <= 43) "S/ 3,50" else "S/ 5,00"
        return PilotTrip(origin, exit, path, fare, minutes)
    }
}

fun normalize(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

fun Geo.distanceLabel(to: Geo): String {
    val meters = metersTo(to)
    return if (meters < 1_000) "${meters.roundToInt()} m" else String.format(Locale.forLanguageTag("es-PE"), "%.1f km", meters / 1000)
}

fun pointAlong(points: List<Geo>, progress: Float): Geo? = when {
    points.isEmpty() -> null
    points.size == 1 -> points.first()
    else -> {
        val distances = points.zipWithNext { a, b -> a.metersTo(b) }
        var remaining = distances.sum() * progress.coerceIn(0f, 1f)
        var segment = 0
        while (segment < distances.lastIndex && remaining > distances[segment]) {
            remaining -= distances[segment++]
        }
        val fraction = (remaining / distances[segment].coerceAtLeast(0.01)).coerceIn(0.0, 1.0)
        val a = points[segment]
        val b = points[segment + 1]
        Geo(a.lat + (b.lat - a.lat) * fraction, a.lng + (b.lng - a.lng) * fraction)
    }
}
