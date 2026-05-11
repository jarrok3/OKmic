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
    std::atomic<int> buffer_size = 1024;
public:
    AudioEngine() = default;
    ~AudioEngine() override {
        stop();
    }

    void start() {
        if(buffer_size <= 0 || (buffer_size & (buffer_size - 1)) != 0)
            {
                // Don't even bother if the buffer_size is incorrect...
                __android_log_print(ANDROID_LOG_ERROR, TAG, "Provided buffer_size was NOT a complete power of 2");
                return;
        }

        std::lock_guard<std::mutex> lock(mLock); // Lock for start-stream (Avoid interruptions from UI or other threads)

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Exclusive)
               ->setFormat(oboe::AudioFormat::Float)
               ->setUsage(oboe::Usage::Media)
               ->setChannelCount(oboe::ChannelCount::Mono)
               ->setDataCallback(this);

        oboe::Result result = builder.openStream(mStream);
        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, TAG,"Failed to create stream. Error: %s", oboe::convertToText(result));
            mStream->close();
            mStream.reset();
            return;
        }

        result = mStream->requestStart();
        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Error starting stream: %s", oboe::convertToText(result));
            mStream->close();
            mStream.reset();
        }

        int32_t sampleRate = mStream->getSampleRate();
        __android_log_print(ANDROID_LOG_INFO, TAG, "Audio stream started with sample rate: %d", sampleRate);
    }

    void stop() {
        if (mStream) {
            std::lock_guard<std::mutex> lock(mLock); // Lock for stop-stream

            mStream->stop();
            mStream->close();
            mStream.reset();
            dspProcessor.reset();
            __android_log_print(ANDROID_LOG_INFO, TAG, "Audio stream stopped, DSP state reset");
        }
    }

    void setBufferSize(int bs) {
        this->buffer_size = bs;
    }

    // Get & process mic data
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        if (audioStream->getFormat() == oboe::AudioFormat::Float) {
            auto *micData = static_cast<float *>(audioData);

            // Now it's possible to retrieve all the data from DSP Getters

        }
        return oboe::DataCallbackResult::Continue;
    }
};

// Singleton engine instance
static AudioEngine gEngine;

// JNI Integration
extern "C" {
    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_startAudio(JNIEnv *env, jobject thiz) {
        gEngine.start();
    }

    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_stopAudio(JNIEnv *env, jobject thiz) {
        gEngine.stop();
    }

    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_setBufferSize(JNIEnv *env, jobject thiz, jint buffer_size) {
        gEngine.setBufferSize(buffer_size);
    }
}
