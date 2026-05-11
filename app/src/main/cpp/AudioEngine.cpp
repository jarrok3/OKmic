#include "AudioEngine.h"
#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic>
#include <cmath>
#include <mutex>
#include "DSPmodule.h"

#define TAG "AudioEngine"

AudioEngine::~AudioEngine() {
    stopStream();
}

bool AudioEngine::openStream() {
    if(buffer_size <= 0 || (buffer_size & (buffer_size - 1)) != 0)
    {
        // Don't even bother if the buffer_size is incorrect...
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Provided buffer_size was NOT a complete power of 2");
        return false;
    }

    std::lock_guard<std::mutex> lock(mLock); // Lock for start-stream (Avoid interruptions from UI or other threads)
    if (mStream) return true;

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
        return false;
    }

    int32_t sampleRate = mStream->getSampleRate();
    __android_log_print(ANDROID_LOG_INFO, TAG, "Audio stream OPENED with sample rate: %d", sampleRate);

    return true;
}

bool AudioEngine::startStream() {
    std::lock_guard<std::mutex> lock(mLock);
    if (!mStream) return false;
    if (mStream->getState() == oboe::StreamState::Started) return true;

    oboe::Result result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error starting stream: %s", oboe::convertToText(result));
        mStream->close();
        mStream.reset();
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Audio stream STARTED");
    return true;
}

void AudioEngine::stopStream() {
    if (mStream) {
        std::lock_guard<std::mutex> lock(mLock); // Lock for stop-stream

        mStream->stop();
        mStream->close();
        mStream.reset();
        dspProcessor.reset();
        __android_log_print(ANDROID_LOG_INFO, TAG, "Audio stream stopped, DSP state reset");
    }
}

void AudioEngine::setBufferSize(int bs) {
    this->buffer_size = bs;
}

// Get & process mic data
oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    if (audioStream->getFormat() == oboe::AudioFormat::Float) {
        auto *micData = static_cast<float *>(audioData);

        // Now it's possible to retrieve all the data from DSP Getters

    }
    return oboe::DataCallbackResult::Continue;
}