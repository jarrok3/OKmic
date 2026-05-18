#include "DSPmodule.h"
#include <cmath>
#include <stdexcept>
#include <complex>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846f
#endif

DSPmodule::DSPmodule() : currentDB(0.0f), maxDB(-100.0f), minDB(100.0f), ringBuffer(std::make_unique<LockFreeQueue<float>>(1024)), fwindowSize(1024), bufferSize(1024)
{
    isRunning = true;
    processingThread = std::thread(&DSPmodule::_processingLoop, this);
}

DSPmodule::~DSPmodule() {
    isRunning = false;
    cv.notify_all();
    if (processingThread.joinable()) {
        processingThread.join();
    }
}

void DSPmodule::setFWindowSize(int size){
    if (size <= 0 || (size & (size - 1)) != 0)
        throw std::invalid_argument("DSP: Window size must be a positive power of 2");
    fwindowSize = size;
}

void DSPmodule::setBufferSize(int size) {
    if (size <= 0 || (size & (size - 1)) != 0)
        throw std::invalid_argument("DSP: Buffer size must be a positive power of 2");
    bufferSize = size;
    _resetBuffer();
}

bool DSPmodule::_resetBuffer() {
    ringBuffer = std::make_unique<LockFreeQueue<float>>(bufferSize);
    return true;
}

bool DSPmodule::reset() {
    maxDB = -100.0f;
    minDB = 100.0f;
    currentDB = 0.0f;
    this->_resetBuffer();
    return true;
}

void DSPmodule::process(const float* data, int numFrames){
    for (int i = 0; i < numFrames; ++i) {
        ringBuffer->push(data[i]);
    }

    if(this->ringBuffer->size() == bufferSize)
    {
        cv.notify_one(); // wake random thread for internal processing
    }
}

void DSPmodule::_processingLoop(){
    std::vector<float> workingBuffer(bufferSize);

    while (isRunning) {
        std::unique_lock<std::mutex> lock(threadMutex);
        cv.wait(lock, [this] { return !isRunning || ringBuffer->size() >= bufferSize; });

        if (!isRunning) break;

        for (int i = 0; i < bufferSize; ++i) {
            ringBuffer->pop(workingBuffer[i]);
        }
        lock.unlock();

        // Process
        float rms = _calcRMS(workingBuffer);
        currentDB = 20.0f * std::log10(std::max(rms, 1e-9f));

        if (currentDB > maxDB) maxDB = currentDB;
        if (currentDB < minDB) minDB = currentDB;

        latestFourierResults = _fourierTransform(workingBuffer);

        // Notify the engine, invoke event
        AudioResults results = {
                currentDB,
                maxDB,
                minDB,
                latestFourierResults
        };
        if (listener != nullptr) {
            listener->onAudioDataReady(results);
        }
    }
}

float DSPmodule::_calcRMS(std::vector<float>& workingBuffer) {
    float sum = 0.0f;
    for(int i = 0; i < workingBuffer.size(); ++i)
    {
        sum += workingBuffer[i] * workingBuffer[i];
    }
    return std::sqrt(sum / bufferSize);
}

std::vector<float> DSPmodule::_fourierTransform(std::vector<float>& workingBuffer) {
    int n = fwindowSize;
    int dataSize = static_cast<int>(workingBuffer.size());

    // 1. Hann Windowing
    for (int i = 0; i < std::min(n, dataSize); i++) {
        float window = 0.5f * (1.0f - cosf(2.0f * M_PI * i / (n - 1.0f)));
        workingBuffer[i] *= window;
    }

    // 2. Prepare complex buffer for FFT
    std::vector<std::complex<float>> buffer(n);
    for (int i = 0; i < n; i++) {
        float sample = (i < dataSize) ? workingBuffer[i] : 0.0f;
        buffer[i] = std::complex<float>(sample, 0.0f);
    }

    // 3. FFT with Cooley-Tukey Radix-2 algorithm
    // Bit-reversal permutation
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(buffer[i], buffer[j]);
    }

    // Butterfly transformations
    for (int len = 2; len <= n; len <<= 1) {
        float ang = 2.0f * M_PI / len;
        std::complex<float> wlen(cosf(ang), -sinf(ang));
        for (int i = 0; i < n; i += len) {
            std::complex<float> w(1);
            for (int j = 0; j < len / 2; j++) {
                std::complex<float> u = buffer[i + j];
                std::complex<float> v = buffer[i + j + len / 2] * w;
                buffer[i + j] = u + v;
                buffer[i + j + len / 2] = u - v;
                w *= wlen;
            }
        }
    }

    // 4. Calculate Magnitude Spectrum and convert to Relative sound level [dB]
    std::vector<float> magnitudeSpectrum(n / 2);
    for (int i = 0; i < n / 2; i++) {
        float mag = std::abs(buffer[i]) / n; // normalization
        magnitudeSpectrum[i] = 20.0f * log10f(std::fmax(mag, 1e-6f));
    }

    return magnitudeSpectrum;
}

