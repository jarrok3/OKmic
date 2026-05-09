cmake_minimum_required(VERSION 3.22.1)

project("native_audio_engine")

# Find Oboe via Prefab
find_package(oboe REQUIRED CONFIG)

# Create your library
add_library(native-audio-lib SHARED native-lib.cpp)

# Link your library to Oboe and the Android Logging library
target_link_libraries(native-audio-lib
        oboe::oboe
        log)