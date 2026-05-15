#ifndef SOUNDPROOF_OKMIC_DSPMODULE_H
#define SOUNDPROOF_OKMIC_DSPMODULE_H

#include <vector>
#include <complex>
#include <atomic>
#include "LockFreeQueue.h"

class DSPmodule{
private:
    float currentDB;
    float maxDB;
    float minDB;

    std::unique_ptr<LockFreeQueue<float, 1024>> ringBuffer;
    std::atomic<int> bufferSize;
    std::atomic<int> fwindowSize;

    bool _resetBuffer();
public:
    DSPmodule();
    void process(const float* data, int numFrames);
    bool reset();

    // Getters
    float getCurrentDB() const {return this->currentDB;}
    float getMaxDB() const {return this->maxDB;}
    float getMinDB() const {return this->minDB;}
    int getFWindowSize() {return this->fwindowSize.load();}
    int getBufferSize() {return this->bufferSize.load();}

    // Setters
    void setFWindowSize(int fwindowSize);
    void setBufferSize(int bufferSize);
};

#endif //SOUNDPROOF_OKMIC_DSPMODULE_H
