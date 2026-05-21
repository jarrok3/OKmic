package com.example.soundproof_okmic

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/* VIEWMODEL FOR STORING CONFIGURATION SETTINGS
*  onaudiostart() should read from these to create the AudioStream with specified parameters
*  alternatively you should also be able to change parameters of the stream during runtime
*/

// Single Flow State (struct) for updates
data class ConfigurationData(
    val bufferSize: Int = 1024,
    val fWindowSize: Int = 1024,
    val noiseGateEnabled: Boolean = false,
    val noiseGateThreshold: Float = -80f,
    val algo: String = "Hann"
)

class ConfigurationSettings : ViewModel() {
    // States/memory:
    private val _configData = MutableStateFlow(ConfigurationData())
    val configData: StateFlow<ConfigurationData> = _configData.asStateFlow()

    // Methods:
    fun setBufferSize(size: Int) {
        _configData.update { it.copy(bufferSize = size) }
    }

    fun setFWindowSize(size: Int) {
        _configData.update { it.copy(fWindowSize = size) }
    }

    fun setNoiseGateEnabled(enabled: Boolean) {
        _configData.update { it.copy(noiseGateEnabled = enabled) }
    }

    fun setNoiseGateThreshold(threshold: Float) {
        if(_configData.value.noiseGateEnabled)
        {
            _configData.update { it.copy(noiseGateThreshold = threshold) }
        }
    }

    fun setAlgo(algo: String){
        _configData.update { it.copy(algo = algo) }
    }
}