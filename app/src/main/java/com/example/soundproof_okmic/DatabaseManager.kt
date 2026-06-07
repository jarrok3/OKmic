package com.example.soundproof_okmic

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import com.mapbox.geojson.*
import com.mapbox.mapboxsdk.geometry.LatLng
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun NoiseMeasurementDto.toLatLng(): LatLng? {
    return try {
        val cleaned = location
            .trim()
            .removePrefix("POINT(")
            .removeSuffix(")")
            .trim()

        val parts = cleaned.split(" ")

        if (parts.size != 2) {
            Log.e("GEO_DEBUG", "Invalid POINT format: $location")
            return null
        }

        val lon = parts[0].toDoubleOrNull()
        val lat = parts[1].toDoubleOrNull()

        if (lon == null || lat == null) {
            Log.e("GEO_DEBUG", "NaN coords in: $location")
            return null
        }

        LatLng(lat, lon)
    } catch (e: Exception) {
        Log.e("GEO_DEBUG", "Failed parsing POINT: $location", e)
        null
    }
}

fun hexToBytes(hex: String): ByteArray {
    // Handle PostGIS EWKB format which might include SRID (e.g., "SRID=4326;0101...")
    val clean = if (hex.contains(";")) {
        hex.substringAfter(";")
    } else {
        hex.removePrefix("SRID=").substringAfter(",")
    }
    val len = clean.length
    val data = ByteArray(len / 2)

    var i = 0
    while (i < len) {
        data[i / 2] = ((clean[i].digitToInt(16) shl 4) +
                clean[i + 1].digitToInt(16)).toByte()
        i += 2
    }
    return data
}

// JSON helper (OK)
fun Any.toJsonElement(): JsonElement =
    when (this) {
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(this.toString())
    }

// WKB PARSER
fun wkbToFeature(
    hex: String,
    properties: Map<String, Any> = emptyMap()
): Feature? {
    return try {

        val bytes = hexToBytes(hex)
        val buffer = ByteBuffer.wrap(bytes)

        buffer.order(
            if (buffer.get().toInt() == 0) ByteOrder.BIG_ENDIAN
            else ByteOrder.LITTLE_ENDIAN
        )

        val geomType = buffer.int and 0xFFFFFF

        val feature = when (geomType) {

            // POINT
            1 -> {
                val x = buffer.double
                val y = buffer.double
                Feature.fromGeometry(Point.fromLngLat(x, y))
            }

            // LINESTRING
            2 -> {
                val numPoints = buffer.int
                val points = mutableListOf<Point>()

                repeat(numPoints) {
                    val x = buffer.double
                    val y = buffer.double
                    points.add(Point.fromLngLat(x, y))
                }

                Feature.fromGeometry(LineString.fromLngLats(points))
            }

            // POLYGON
            3 -> {
                val numRings = buffer.int
                val rings = mutableListOf<List<Point>>()

                repeat(numRings) {
                    val numPoints = buffer.int
                    val ring = mutableListOf<Point>()

                    repeat(numPoints) {
                        val x = buffer.double
                        val y = buffer.double
                        ring.add(Point.fromLngLat(x, y))
                    }

                    rings.add(ring)
                }

                Feature.fromGeometry(Polygon.fromLngLats(rings))
            }

            else -> null
        }

        feature?.apply {
            properties.forEach { (k, v) ->
                addProperty(k, v.toJsonElement())
            }
        }

    } catch (e: Exception) {
        Log.e("GEO_DEBUG", "WKB parse error: $hex", e)
        null
    }
}

// =======================
// DTOs
// =======================
@Serializable
data class BuildingDto(
    val id: Long,
    val geometry: String
)

@Serializable
data class StreetDto(
    val id: Long,
    val geometry: String
)

@Serializable
data class NoiseMeasurementDto(
    val timestamp_ms: Long,
    val location: String, // POINT(lon lat)
    val avg_db: Float,
    val spectrogram: List<Float>
)

// =======================
// DATABASE MANAGER
// =======================
class DatabaseManager(
    private val supabaseClient: SupabaseClient,
    private val audioManager: AudioManager
) {
    private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        observeNoiseResults()
    }

    private fun observeNoiseResults() {
        databaseScope.launch {

            audioManager.noiseTestResults
                .filter { it.timestamp != 0L }
                .filter { it.spectrogram.isNotEmpty() }
                .distinctUntilChanged()
                .collect { measurement ->

                    try {
                        val dto = NoiseMeasurementDto(
                            timestamp_ms = measurement.timestamp,
                            location = "POINT(${measurement.longitude} ${measurement.latitude})",
                            avg_db = measurement.avgDb,
                            spectrogram = measurement.spectrogram
                        )

                        supabaseClient.postgrest["noise_measurements"].insert(dto)

                        Log.d("DatabaseManager", "Inserted measurement OK")

                    } catch (e: Exception) {
                        Log.e("DatabaseManager", "Insert error", e)
                    }
                }
        }
    }

    suspend fun fetchAllMeasurements(): List<NoiseMeasurementDto> {
        return try {

            val response = supabaseClient.postgrest["noise_measurements"]
                .select {
                    order("timestamp_ms", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<NoiseMeasurementDto>()

            Log.d("DatabaseManager", "Retrieved ${response.size} measurements")
            response

        } catch (e: Exception) {
            Log.e("DatabaseManager", "fetchAllMeasurements error", e)
            emptyList()
        }
    }

    suspend fun fetchBuildings(): List<BuildingDto> {
        return try {
            val res = supabaseClient.postgrest["warsaw_buildings"]
                .select()
                .decodeList<BuildingDto>()

            Log.d("DB_DEBUG", "buildings loaded = ${res.size}")
            res
        } catch (e: Exception) {
            Log.e("DB_DEBUG", "buildings fetch error", e)
            emptyList()
        }
    }

    suspend fun fetchStreets(): List<StreetDto> {
        return try {
            val res = supabaseClient.postgrest["warsaw_roads"]
                .select()
                .decodeList<StreetDto>()

            Log.d("DB_DEBUG", "streets loaded = ${res.size}")
            res
        } catch (e: Exception) {
            Log.e("DB_DEBUG", "streets fetch error", e)
            emptyList()
        }
    }

    suspend fun deleteMeasurement(timestampMs: Long) {
        try {
            Log.d("DatabaseManager", "Deleting measurement with timestamp: $timestampMs")
            supabaseClient.postgrest["noise_measurements"].delete {
                filter {
                    eq("timestamp_ms", timestampMs)
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseManager", "Error deleting measurement from Supabase", e)
        }
    }

    fun cleanup() {
        databaseScope.cancel()
    }
}