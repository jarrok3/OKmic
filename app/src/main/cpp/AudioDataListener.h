#ifndef SOUNDPROOF_OKMIC_AUDIODATALISTENER_H
#define SOUNDPROOF_OKMIC_AUDIODATALISTENER_H

#include <cstdint>
#include <vector>

struct AudioResults {
    float currentDB;
    float maxDB;
    float minDB;
    std::vector<float> fourierResults;
};

class AudioDataListener {
public:
    virtual ~AudioDataListener() = default;
    virtual void onAudioDataReady(const AudioResults& results) = 0;
};

#endif //SOUNDPROOF_OKMIC_AUDIODATALISTENER_H
