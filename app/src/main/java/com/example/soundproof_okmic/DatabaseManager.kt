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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun NoiseMeasurementDto.toReadableDate(): String {
    return try {
        // Tworzymy obiekt Date na podstawie milisekund
        val date = Date(this.timestamp_ms)

        // Definiujemy porządny format: Dzień.Miesiąc.Rok Godzina:Minuta:Sekunda
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        formatter.format(date)
    } catch (e: Exception) {
        Log.e("DATE_DEBUG", "Failed to format timestamp: ${this.timestamp_ms}", e)
        "Nieznana data" // W razie nieprzewidzianego błędu zwracamy bezpieczny tekst alternatywny
    }
}

fun NoiseMeasurementDto.toLatLng(): LatLng? {
    return try {
        val loc = location.trim()

        // Przypadek 1: Jeśli format to WKT tekstowy np. "POINT(21.01 52.22)"
        if (loc.startsWith("POINT", ignoreCase = true)) {
            val cleaned = loc.removePrefix("POINT(").removePrefix("point(").removeSuffix(")").trim()
            val parts = cleaned.split(" ")
            val lon = parts[0].toDoubleOrNull()
            val lat = parts[1].toDoubleOrNull()
            if (lon != null && lat != null) return LatLng(lat, lon)
        }
        // Przypadek 2: Jeśli baza zwraca Hex EWKB (np. "0101000020E6...")
        else if (loc.matches(Regex("^[0-9a-fA-F]+$"))) {
            val bytes = hexToBytes(loc)
            val buffer = ByteBuffer.wrap(bytes)

            // Określenie kolejności bajtów (Little vs Big Endian)
            buffer.order(
                if (buffer.get().toInt() == 0) ByteOrder.BIG_ENDIAN
                else ByteOrder.LITTLE_ENDIAN
            )

            val geomType = buffer.int
            val hasSrid = (geomType and 0x20000000) != 0 // Flaga EWKB informująca o obecności SRID
            val actualType = geomType and 0xFFFFFF

            // Jeśli jest SRID, musimy pominąć 4 bajty, aby nie popsuć odczytu koordynatów
            if (hasSrid) {
                buffer.int
            }

            if (actualType == 1) { // Typ 1 = Point
                val lon = buffer.double
                val lat = buffer.double
                return LatLng(lat, lon)
            }
        }

        Log.e("GEO_DEBUG", "Unrecognized location format: $location")
        null
    } catch (e: Exception) {
        Log.e("GEO_DEBUG", "Failed parsing location: $location", e)
        null
    }
}

fun hexToBytes(hex: String): ByteArray {
    val clean = hex.removePrefix("SRID=").substringAfter(",")
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

        // Odczytanie byte-order (Big vs Little Endian)
        buffer.order(
            if (buffer.get().toInt() == 0) ByteOrder.BIG_ENDIAN
            else ByteOrder.LITTLE_ENDIAN
        )

        // Pobranie surowego typu geometrii (zawiera flagi PostGIS, np. obecność SRID)
        val geomTypeRaw = buffer.int

        // Sprawdzenie, czy ustawiony jest bit odpowiadający za obecność SRID (0x20000000)
        val hasSrid = (geomTypeRaw and 0x20000000) != 0

        // Wyczyszczenie flag, aby uzyskać czysty typ geometrii (1 = Point, 2 = LineString, 3 = Polygon)
        val geomType = geomTypeRaw and 0xFFFFFF

        // Kluczowa poprawka: Jeśli geometria zawiera SRID, pomijamy te 4 bajty,
        // aby wskaźnik bufora trafił dokładnie na początek danych koordynatów
        if (hasSrid) {
            buffer.int
        }

        val feature = when (geomType) {
            // 1 -> POINT
            1 -> {
                val x = buffer.double
                val y = buffer.double
                Feature.fromGeometry(Point.fromLngLat(x, y))
            }

            // 2 -> LINESTRING
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

            // 3 -> POLYGON
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
    val osm_id: Long,
    val building: String?,
    val geom: String
)

@Serializable
data class StreetDto(
    val osm_id: Long,
    val name: String?,
    val highway: String?,
    val geom: String
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