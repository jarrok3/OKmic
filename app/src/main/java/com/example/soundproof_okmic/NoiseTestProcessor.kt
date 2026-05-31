package com.example.soundproof_okmic

data class NoiseTestResults(
    val avgDb: Float = 0.0f,
    val spectrogram: FloatArray = floatArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NoiseTestResults

        if (avgDb != other.avgDb) return false
        if (!spectrogram.contentEquals(other.spectrogram)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = avgDb.hashCode()
        result = 31 * result + spectrogram.contentHashCode()
        return result
    }
}

data class NoiseFrame(
    val db: Float,
    val fourier: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NoiseFrame

        if (db != other.db) return false
        if (!fourier.contentEquals(other.fourier)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = db.hashCode()
        result = 31 * result + fourier.contentHashCode()
        return result
    }
}

class NoiseTestProcessor(
    private val expectedFrames: Int = 100
) {
    private val capturedFrames = mutableListOf<NoiseFrame>()

    fun pushNoiseFrame(frame: NoiseFrame) {
        capturedFrames.add(frame)
    }

    fun calcAverageDb(): Float
    {
        if (capturedFrames.isEmpty()) return 0f
        var sum = 0f
        for (noiseframe in capturedFrames)
        {
            sum += noiseframe.db
        }
        return sum/capturedFrames.size
    }

    fun getSpectrogram() : FloatArray
    {
        if (capturedFrames.isEmpty()) return floatArrayOf()
        val frameSize = capturedFrames[0].fourier.size
        val result = FloatArray(capturedFrames.size * frameSize)
        for (i in capturedFrames.indices) {
            System.arraycopy(
                capturedFrames[i].fourier, 0, result, i * frameSize, frameSize
            )
        }
        return result
    }

    fun getResults(): NoiseTestResults {
        return NoiseTestResults(
            avgDb = calcAverageDb(),
            spectrogram = getSpectrogram()
        )
    }
}