#ifndef SOUNDPROOF_OKMIC_AUDIOENGINE_H
#define SOUNDPROOF_OKMIC_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic>
#include <memory>
#include <mutex>
#include "DSPmodule.h"
#include "AudioDataListener.h"

class AudioEngine : public oboe::AudioStreamDataCallback, public AudioDataListener {
private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::mutex mLock;
    std::unique_ptr<DSPmodule> dspProcessor;

    AudioResults mLatestResults;
    std::mutex mResultsLock;

public:
    AudioEngine();
    ~AudioEngine() override;

    bool openStream();
    bool startStream();
    void stopStream();
    void setFWindowSize(int bs);
    void setBufferSize(int bs);

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;

    void onAudioDataReady(const AudioResults& results) override;
    AudioResults getLatestResults();
};

#endif //SOUNDPROOF_OKMIC_AUDIOENGINE_H
