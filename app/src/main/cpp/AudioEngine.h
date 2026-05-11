#ifndef SOUNDPROOF_OKMIC_AUDIOENGINE_H
#define SOUNDPROOF_OKMIC_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic>
#include <memory>
#include <mutex>
#include "DSPmodule.h"

class AudioEngine : public oboe::AudioStreamDataCallback {
private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::mutex mLock;
    DSPmodule dspProcessor;
    std::atomic<int> buffer_size = 1024;

public:
    AudioEngine() = default;
    ~AudioEngine() override;

    bool openStream();
    bool startStream();
    void stopStream();
    void setBufferSize(int bs);

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;

};

#endif //SOUNDPROOF_OKMIC_AUDIOENGINE_H
