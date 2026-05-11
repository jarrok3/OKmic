#ifndef SOUNDPROOF_OKMIC_DSPMODULE_H
#define SOUNDPROOF_OKMIC_DSPMODULE_H

#include <vector>
#include <complex>

class DSPmodule{
private:
    float currentDB;
    float maxDB;
    float minDB;
    int window_size;
    int curr_totalof_samples;
    std::vector<float> fftResults;

    std::vector<float> _fourierTransform(const float* data, int numFrames) const;
    float _calcRMS(const float* data, int numFrames) const;
public:
    DSPmodule();

    void process(const float* data, int numFrames);
    void reset();

    // Setters
    void setWindowSize(int size);

    // Getters
    float getCurrentDB() const;
    float getMaxDB() const;
    float getMinDB() const;
    int getWindowSize() const;
    std::vector<float> getFFTResults() const;
};

#endif //SOUNDPROOF_OKMIC_DSPMODULE_H
