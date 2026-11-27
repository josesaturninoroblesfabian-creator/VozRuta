package pe.vozruta.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import pe.vozruta.app.maps.Destination
import pe.vozruta.app.maps.VOZRUTA_AGENT
import pe.vozruta.app.model.Geo
import java.io.File

@Composable
internal fun StreetMap(
    path: List<Geo>,
    location: Geo?,
    places: List<Destination>,
    bus: Geo? = null,
    follow: Boolean = false,
    recenter: Int = 0,
    pick: (Geo) -> Unit = {},
    moved: () -> Unit = {}
) {
    val choose by rememberUpdatedState(pick)
    val movement by rememberUpdatedState(moved)
    Box(Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(13.dp))) {
        AndroidView(
            factory = { context ->
                Configuration.getInstance().apply {
                    userAgentValue = VOZRUTA_AGENT
                    osmdroidBasePath = File(context.cacheDir, "street-map").apply { mkdirs() }
                    osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
                    tileFileSystemCacheMaxBytes = 80L * 1024 * 1024
                    tileFileSystemCacheTrimBytes = 60L * 1024 * 1024
                }
                StreetMapView(context, { choose(it) }, { movement() }).apply { onResume() }
            },
            modifier = Modifier.matchParentSize(),
            onReset = null,
            onRelease = { it.onPause(); it.onDetach() },
            update = { it.present(path, location, places, bus, follow, recenter) }
        )
        Text("© OpenStreetMap contributors", color = Snow, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopEnd).background(Background).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

private class StreetMapView(context: Context, choose: (Geo) -> Unit, private val moved: () -> Unit) : MapView(context) {
    private val line = Polyline(this).apply {
        outlinePaint.color = Color.rgb(35, 92, 215)
        outlinePaint.strokeWidth = 10f
    }
    private val user = marker("Tu ubicación", 0xFF146CFF.toInt())
    private val unit = marker("Autobús simulado", 0xFF588013.toInt())
    private var lastPath: List<Geo>? = null
    private var lastPlaces: List<Destination>? = null
    private val pins = mutableListOf<Marker>()
    private var lastFollow = false
    private var lastCenter: Int? = null
    private var lastLocation: Geo? = null

    init {
        contentDescription = "Mapa de calles. Los destinos y las indicaciones también están en texto."
        setTileSource(XYTileSource("OpenStreetMap", 0, 19, 256, ".png",
            arrayOf("https://tile.openstreetmap.org/"), "© OpenStreetMap contributors"))
        setMultiTouchControls(true)
        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        setVerticalMapRepetitionEnabled(false)
        controller.setZoom(14.5)
        controller.setCenter(GeoPoint(-9.93149, -76.24007))
        overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint) = false
            override fun longPressHelper(p: GeoPoint): Boolean {
                choose(Geo(p.latitude, p.longitude))
                return true
            }
        }))
        overlays.add(line)
        overlays.add(user)
        overlays.add(unit)
        user.setEnabled(false)
        unit.setEnabled(false)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
            moved()
        }
        if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun marker(label: String, color: Int) = Marker(this).apply {
        title = label
        icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(3, Color.WHITE)
            setSize(31, 31)
        }
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    }

    fun present(path: List<Geo>, location: Geo?, places: List<Destination>, bus: Geo?, follow: Boolean, recenter: Int) {
        val pathChanged = lastPath != path
        val placesChanged = lastPlaces != places
        val centered = recenter != lastCenter
        if (pathChanged) {
            line.setPoints(path.map(Geo::osm))
            lastPath = path
        }
        if (placesChanged) {
            overlays.removeAll(pins.toSet())
            pins.clear()
            places.forEach { place ->
                val pin = Marker(this).apply { position = place.point.osm(); title = place.name }
                pins.add(pin)
            }
            overlays.addAll(2, pins)
            lastPlaces = places
        }
        user.setEnabled(location != null)
        location?.let { user.position = it.osm() }
        unit.setEnabled(bus != null)
        bus?.let { unit.position = it.osm() }

        if (follow && location != null) {
            if (!lastFollow || centered) controller.setZoom(17.5)
            if (location != lastLocation || !lastFollow || centered) controller.setCenter(location.osm())
        } else if (pathChanged || placesChanged || centered) {
            val visible = (path + places.map { it.point } + listOfNotNull(location)).map(Geo::osm).distinct()
            post {
                if (!isAttachedToWindow) return@post
                if (visible.size == 1) {
                    controller.setZoom(15.5)
                    controller.setCenter(visible.first())
                } else if (visible.size > 1) {
                    zoomToBoundingBox(BoundingBox.fromGeoPoints(visible), false, 50, 17.5, null)
                }
            }
        }
        lastFollow = follow
        lastCenter = recenter
        lastLocation = location
        invalidate()
    }
}

private fun Geo.osm() = GeoPoint(lat, lng)
