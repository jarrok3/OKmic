#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic>
#include <cmath>

#define TAG "AudioEngine"

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    AudioEngine() = default;
    ~AudioEngine() {
        stop();
    }

    // return true if correctly started streaming
    bool start() {
        std::lock_guard<std::mutex> lock(mLock);
        if (mStream) return true; // Already running

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Exclusive)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Mono)
               ->setDataCallback(this);

        oboe::Result result = builder.openStream(mStream);
        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening stream: %s", oboe::convertToText(result));
            return false;
        }

        result = mStream->requestStart();
        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Error starting stream: %s", oboe::convertToText(result));
            mStream->close();
            mStream.reset();
            return false;
        }

        __android_log_print(ANDROID_LOG_INFO, TAG, "Audio stream started successfully");
        return true;
    }

    void stop() {
        std::lock_guard<std::mutex> lock(mLock);
        if (mStream) {
            mStream->stop();
            mStream->close();
            mStream.reset();
            __android_log_print(ANDROID_LOG_INFO, TAG, "Audio stream stopped");
        }
    }

    // Get mic data
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        if (audioStream->getFormat() == oboe::AudioFormat::Float) {
            auto *floatData = static_cast<float *>(audioData);
            float maxAmplitude = 0.0f;
            for (int i = 0; i < numFrames; ++i) {
                float absValue = std::abs(floatData[i]);
                if (absValue > maxAmplitude) maxAmplitude = absValue;
            }
            // Atomically update the last peak amplitude
            mLastPeak.store(maxAmplitude);
        }
        return oboe::DataCallbackResult::Continue;
    }

    // Peak amplitude calc
    float getLastPeakAmplitude() const {
        return mLastPeak.load();
    }

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::atomic<float> mLastPeak{0.0f};
    std::mutex mLock;
};

// Singleton engine instance
static AudioEngine gEngine;

extern "C" {
    JNIEXPORT jboolean JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_startAudio(JNIEnv *env, jobject thiz) {
        return static_cast<jboolean>(gEngine.start());
    }

    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_stopAudio(JNIEnv *env, jobject thiz) {
        gEngine.stop();
    }

    JNIEXPORT jfloat JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_getAmplitude(JNIEnv *env, jobject thiz) {
        return gEngine.getLastPeakAmplitude();
    }
}
