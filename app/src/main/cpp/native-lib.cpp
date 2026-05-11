#include <jni.h>
#include "AudioEngine.h"

// Single engine instance, disallowed copying
static std::unique_ptr<AudioEngine> gEngine;

// JNI Integration
extern "C" {
    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_openAudio(JNIEnv *env, jobject thiz) {
        if (!gEngine) {
            gEngine = std::make_unique<AudioEngine>();
        }
        gEngine->openStream();
    }

    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_startAudio(JNIEnv *env, jobject thiz) {
        if (gEngine){
            gEngine->startStream();
        }
    }

    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_stopAudio(JNIEnv *env, jobject thiz) {
        if (gEngine) {
            gEngine->stopStream();
        }
    }

    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_setBufferSize(JNIEnv *env, jobject thiz, jint buffer_size) {
        gEngine->setBufferSize(buffer_size);
    }
}
