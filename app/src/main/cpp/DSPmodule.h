#ifndef SOUNDPROOF_OKMIC_DSPMODULE_H
#define SOUNDPROOF_OKMIC_DSPMODULE_H

#include <vector>
#include <complex>
#include <atomic>
#include "LockFreeQueue.h"
#include <thread>
#include <mutex>
#include <condition_variable>
#include "AudioDataListener.h"

enum class AlgoType {
    Hann,
    Hamming,
    Blackman
};

class DSPmodule{
private:
    AudioDataListener* listener = nullptr;
    float currentDB;
    float maxDB;
    float minDB;
    std::vector<float> latestFourierResults;

    std::unique_ptr<LockFreeQueue<float>> ringBuffer;
    std::atomic<int> bufferSize;
    std::atomic<int> fwindowSize;
    std::atomic<AlgoType> algoType;
    std::atomic<float> noiseThreshold;

    // DSP processing methods
    float _calcRMS(std::vector<float>& workingBuffer);
    std::vector<float> _fourierTransform(std::vector<float>& workingBuffer);

    /*
     * Consumer thread for RMS and Fourier calculations
     * Invoked whenever the ringBuffer is filled, moving the calculations outside of the Audio Callback
     */
    std::thread processingThread;
    std::mutex threadMutex;
    std::condition_variable cv;
    std::atomic<bool> isRunning{false};
    void _processingLoop(); // invoked by internal processing thread
    bool _resetBuffer();

public:
    DSPmodule();
    ~DSPmodule();
    void process(const float* data, int numFrames); // invoked from the manager class audio callback
    bool reset();
    void setListener(AudioDataListener* newDataListener) { listener = newDataListener; }

    // Getters
    float getCurrentDB() const {return currentDB;};
    float getMaxDB() const {return maxDB;};
    float getMinDB() const {return minDB;};
    std::vector<float> getLatestFourierResults() const {return latestFourierResults;};
    int getFWindowSize() const {return this->fwindowSize.load();}
    int getBufferSize() const {return this->bufferSize.load();}

    // Setters
    void setFWindowSize(int fwindowSize);
    void setBufferSize(int bufferSize);
    void setAlgoType(std::string algo);
    void setNoiseThreshold(float nt);
};

#endif //SOUNDPROOF_OKMIC_DSPMODULE_H
