package com.example.soundproof_okmic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.cancel

@Serializable
data class NoiseMeasurementDto(
    val timestamp_ms: Long,
    val location: String,
    val avg_db: Float,
    val spectrogram: List<Float>
)

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
            Log.d("DatabaseManager", "INIT DB done, watching over: ${System.identityHashCode(audioManager)}")
            audioManager.noiseTestResults
                .filter { it.timestamp != 0L }
                .filter { it.spectrogram.isNotEmpty() }
                .distinctUntilChanged()
                .collect { measurementData ->
                    try {
                        Log.d("DatabaseManager", "New measurement noticed, sending to supabase... Timestamp: ${measurementData.timestamp}")

                        val dto = NoiseMeasurementDto(
                            timestamp_ms = measurementData.timestamp,
                            location = "POINT(${measurementData.longitude} ${measurementData.latitude})",
                            avg_db = measurementData.avgDb,
                            spectrogram = measurementData.spectrogram
                        )

                        // Send to database
                        supabaseClient.postgrest["noise_measurements"].insert(dto)

                        Log.d("DatabaseManager", "Measurement was successfully added to Supabase")
                    } catch (e: Exception) {
                        Log.e("DatabaseManager", "Error when saving measurement to Supabase", e)
                    }
                }
        }
    }

    suspend fun fetchAllMeasurements(): List<NoiseMeasurementDto> {
        return try {
            // Get measurements by timestamp (from newest)
            val response = supabaseClient.postgrest["noise_measurements"]
                .select {
                    order(column = "timestamp_ms", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<NoiseMeasurementDto>()

            Log.d("DatabaseManager", "Retrieved ${response.size} measurements.")
            response
        } catch (e: Exception) {
            Log.e("DatabaseManager", "Error retrieving data from Supabase", e)
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