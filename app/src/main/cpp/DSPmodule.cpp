#include "DSPmodule.h"
#include <cmath>
#include <stdexcept>
#include <complex>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846f
#endif

DSPmodule::DSPmodule() : currentDB(0.0f), maxDB(-100.0f), minDB(100.0f), ringBuffer(std::make_unique<LockFreeQueue<float, 1024>>()),  fwindowSize(1024), bufferSize(1024)
                          {}

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
    // Implement correct code here
    // LockFreeQueue is static, do we want to parametrize buffer size or just the Fwindowsize?
    return true;
}

bool DSPmodule::reset() {
    maxDB = -100.0f;
    minDB = 100.0f;
    return true;
}

void DSPmodule::process(const float* data, int numFrames){

}

