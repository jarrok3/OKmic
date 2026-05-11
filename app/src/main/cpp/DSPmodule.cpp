#include "DSPmodule.h"
#include <cmath>
#include <stdexcept>
#include <complex>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846f
#endif

DSPmodule::DSPmodule() : currentDB(0.0f), maxDB(-100.0f), minDB(100.0f), window_size(1024), fftResults(512, -100.0f), curr_totalof_samples(0) {}

float DSPmodule::getCurrentDB() const{
    return currentDB;
}

float DSPmodule::getMaxDB() const{
    return maxDB;
}

float DSPmodule::getMinDB() const{
    return minDB;
}

int DSPmodule::getWindowSize() const {
    return window_size;
}

std::vector<float> DSPmodule::getFFTResults() const {
    return fftResults;
}

void DSPmodule::setWindowSize(int size){
    if (size <= 0 || (size & (size - 1)) != 0)
        throw std::invalid_argument("Window size must be a positive power of 2");
    window_size = size;
    fftResults.assign(window_size / 2, -100.0f);
}

void DSPmodule::reset() {
    maxDB = -100.0f;
    minDB = 100.0f;
    fftResults.assign(window_size / 2, -100.0f);
    curr_totalof_samples = 0;
}

void DSPmodule::process(const float* data, int numFrames){
    float rms = _calcRMS(data, numFrames);
    currentDB = 20.0f * log10f( std::fmax(rms, 1e-9f));

    if (currentDB > maxDB)
        maxDB = currentDB;
    if (currentDB < minDB)
        minDB = currentDB;

//    curr_totalof_samples += numFrames;
//    if(curr_totalof_samples >= getWindowSize()){
//        fftResults = _fourierTransform(data, curr_totalof_samples);
//        curr_totalof_samples = 0;
//    }
}

float DSPmodule::_calcRMS(const float* data, int numFrames) const{
    float quadratic_sum = 0.0f;
    for(int i = 0; i<numFrames; i++){
        quadratic_sum += data[i] * data[i];
    }
    return sqrt(quadratic_sum/numFrames);
}

std::vector<float> DSPmodule::_fourierTransform(const float* data, int numFrames) const {
    int n = window_size;

    // Hann Windowing (antialiasing)
    std::vector<std::complex<float>> buffer(n);
    for (int i = 0; i < n; i++) {
        float window = 0.5f * (1.0f - cosf(2.0f * M_PI * i / (n - 1)));
        float sample = (i < numFrames) ? data[i] : 0.0f;
        buffer[i] = std::complex<float>(sample * window, 0.0f);
    }

    // 2. FFT with Cooley-Tukey Radix-2 algorithm
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

    // 3. Relative sound level [dB]
    std::vector<float> magnitudeSpectrum(n / 2);
    for (int i = 0; i < n / 2; i++) {
        float mag = std::abs(buffer[i]) / n; // normalization
        magnitudeSpectrum[i] = 20.0f * log10f(std::fmax(mag, 1e-6f));
    }

    return magnitudeSpectrum;
}
