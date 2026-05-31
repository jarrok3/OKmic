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

@Serializable
data class NoiseMeasurementDto(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val avgDb: Float,
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
                .distinctUntilChanged()
                .collect { measurementData ->
                    try {
                        Log.d("DatabaseManager", "New measurement noticed, sending to supabase... Timestamp: ${measurementData.timestamp}")

                        val dto = NoiseMeasurementDto(
                            timestamp = measurementData.timestamp,
                            latitude = measurementData.latitude,
                            longitude = measurementData.longitude,
                            avgDb = measurementData.avgDb,
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
}