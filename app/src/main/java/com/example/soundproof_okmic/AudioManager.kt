package com.example.soundproof_okmic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
*  VIEWMODEL FOR STORING CONFIGURATION SETTINGS AND MANAGING THE AUDIO STREAM
*/

data class ConfigurationData(
    val bufferSize: Int = 1024,
    val fWindowSize: Int = 1024,
    val noiseGateEnabled: Boolean = false,
    val noiseGateThreshold: Float = -80f,
    val algo: String = "Hann"
)

data class AudioStream(
    val isRecording: Boolean = false,
    val currentDb: Float = 0.0f,
    val maxDb: Float = -100.0f,
    val minDb:Float = 100.0f,
    val fourierResults: FloatArray = floatArrayOf(),
    // History
    val dbHistory: List<Float> = emptyList(),
    val totalSamples: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioStream

        if (isRecording != other.isRecording) return false
        if (currentDb != other.currentDb) return false
        if (maxDb != other.maxDb) return false
        if (minDb != other.minDb) return false
        if (!fourierResults.contentEquals(other.fourierResults)) return false
        if (dbHistory != other.dbHistory) return false
        if (totalSamples != other.totalSamples) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isRecording.hashCode()
        result = 31 * result + currentDb.hashCode()
        result = 31 * result + maxDb.hashCode()
        result = 31 * result + minDb.hashCode()
        result = 31 * result + fourierResults.contentHashCode()
        result = 31 * result + dbHistory.hashCode()
        result = 31 * result + totalSamples.hashCode()
        return result
    }
}

class AudioManager : ViewModel() {
    // States/memory:
    private val _configData = MutableStateFlow(ConfigurationData())
    val configData: StateFlow<ConfigurationData> = _configData.asStateFlow()

    private val _audioStream = MutableStateFlow(AudioStream())
    val audioStream: StateFlow<AudioStream> = _audioStream.asStateFlow()

    private var recordingJob: Job? = null
    private val maxHistorySize = 100

    // ConfigurationSettings Changes:
    fun changeBufferSize(size: Int) {
        _configData.update { it.copy(bufferSize = size) }
        if(_audioStream.value.isRecording)
        {
            setBufferSize(size)
        }
    }

    fun changeFWindowSize(size: Int) {
        _configData.update { it.copy(fWindowSize = size) }
        if (_audioStream.value.isRecording)
        {
            setFWindowSize(size)
        }
    }

    fun setNoiseGateEnabled(enabled: Boolean) {
        _configData.update { it.copy(noiseGateEnabled = enabled) }
    }

    fun setNoiseGateThreshold(threshold: Float) {
        _configData.update { it.copy(noiseGateThreshold = threshold) }
    }

    fun setAlgo(algo: String){
        _configData.update { it.copy(algo = algo) }
    }

    // AudioStream Changes:
    fun changeRecordingState(active: Boolean){
        _audioStream.update { it.copy(isRecording = active) }
    }

    fun startRecording() {
        if (recordingJob?.isActive == true) return

        openAudio()
        setBufferSize(_configData.value.bufferSize)
        setFWindowSize(_configData.value.fWindowSize)
        startAudio()
        _audioStream.update {
            it.copy(
                isRecording = true,
                dbHistory = emptyList(),
                totalSamples = 0L
            )
        }

        // Start Coroutine Job
        recordingJob = viewModelScope.launch {
            while (_audioStream.value.isRecording) {
                updateAudioResults()
                delay(100) // Update UI every 100ms
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        stopAudio()
        _audioStream.update { it.copy(isRecording = false) }
        reset()
    }

    fun reset(){
        _audioStream.update { it.copy(
            isRecording = false,
            currentDb = 0.0f,
            maxDb = 0.0f,
            minDb = 0.0f,
            fourierResults = floatArrayOf(),
            dbHistory = emptyList(),
            totalSamples = 0L
        ) }
    }

    fun updateAudioResults() {
        val rawResults = getAudioResults()
        if (rawResults != null && rawResults.size >= 3) {
            val currentDbVal = rawResults[0]
            val maxDbVal = rawResults[1]
            val minDbVal = rawResults[2]
            val fourierVal = if (rawResults.size > 3) {
                rawResults.copyOfRange(3, rawResults.size)
            } else {
                floatArrayOf()
            }

            _audioStream.update { currentState ->
                val updatedHistory = currentState.dbHistory.toMutableList().apply {
                    add(currentDbVal)
                    if (size > maxHistorySize) {
                        removeAt(0)
                    }
                }

                currentState.copy(
                    currentDb = currentDbVal,
                    maxDb = maxDbVal,
                    minDb = minDbVal,
                    fourierResults = fourierVal,
                    dbHistory = updatedHistory,
                    totalSamples = currentState.totalSamples + 1
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
    }

    // External OBOE lib inclusion
    companion object {
        init {
            System.loadLibrary("native-audio-lib")
        }
    }
    private external fun openAudio()
    private external fun startAudio()
    private external fun stopAudio()
    private external fun setBufferSize(bufferSize: Int)
    private external fun setFWindowSize(fwindowSize: Int)
    private external fun getAudioResults(): FloatArray?
}
