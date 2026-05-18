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
    Java_com_example_soundproof_1okmic_MainActivity_setFWindowSize(JNIEnv *env, jobject thiz, jint fwindow_size) {
        gEngine->setFWindowSize(fwindow_size);
    }

    JNIEXPORT void JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_setBufferSize(JNIEnv *env, jobject thiz, jint buffer_size) {
        gEngine->setBufferSize(buffer_size);
    }

    JNIEXPORT jfloatArray JNICALL
    Java_com_example_soundproof_1okmic_MainActivity_getAudioResults(JNIEnv *env, jobject thiz) {
        if (!gEngine) return nullptr;

        AudioResults results = gEngine->getLatestResults();

        size_t fourierSize = results.fourierResults.size();
        size_t totalSize = 3 + fourierSize; // currentDB, maxDB, minDB + fourierResults

        jfloatArray resultArray = env->NewFloatArray((jsize)totalSize);
        if (resultArray == nullptr) return nullptr;

        auto* temp = new jfloat[totalSize];
        temp[0] = (jfloat)results.currentDB;
        temp[1] = (jfloat)results.maxDB;
        temp[2] = (jfloat)results.minDB;
        for (size_t i = 0; i < fourierSize; ++i) {
            temp[3 + i] = (jfloat)results.fourierResults[i];
        }

        env->SetFloatArrayRegion(resultArray, 0, (jsize)totalSize, temp);
        delete[] temp;

        return resultArray;
    }
}
