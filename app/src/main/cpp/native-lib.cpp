#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic>
#include <cmath>
#include <mutex>
#include "DSPmodule.h"

#define TAG "AudioEngine"

class AudioEngine : public oboe::AudioStreamDataCallback {
private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::mutex mLock;
    DSPmodule dspProcessor;

public:
    AudioEngine() = default;
    ~AudioEngine() {
        stop();
    }

    float getLoudestDb() const {
        return dspProcessor.getMaxDB();
    }

    float getCurrentDb() const {
        return dspProcessor.getCurrentDB();
    }

    float getLowestDb() const {
        return dspProcessor.getMinDB();
    }

    // return true if correctly started streaming
    bool start() {
        std::lock_guard<std::mutex> lock(mLock);
        if (mStream) return true;

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
            dspProcessor.reset();
            __android_log_print(ANDROID_LOG_INFO, TAG, "Audio stream stopped, DSP state reset");
        }
    }

    // Get & process mic data
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        if (audioStream->getFormat() == oboe::AudioFormat::Float) {
            auto *micData = static_cast<float *>(audioData);
            dspProcessor.process(micData, numFrames);

            // Now it's possible to retrieve all the data from DSP Getters

        }
        return oboe::DataCallbackResult::Continue;
    }
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
    Java_com_example_soundproof_1okmic_MainActivity_getLoudestDb(JNIEnv *env, jobject thiz) {
        return static_cast<jfloat>(gEngine.getLoudestDb());
    }

    JNIEXPORT jfloat JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_getLowestDb(JNIEnv *env, jobject thiz) {
        return static_cast<jfloat>(gEngine.getLowestDb());
    }

    JNIEXPORT jfloat JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_getCurrentDb(JNIEnv *env, jobject thiz) {
        return static_cast<jfloat>(gEngine.getCurrentDb());
    }
}
