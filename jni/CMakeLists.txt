cmake_minimum_required(VERSION 3.22.1)
project("mthugo")

add_library(mthugo SHARED
    jni_bridge.c
    env_detect.c
    signature.c
    timecheck.c
    utils.c
)

target_link_libraries(mthugo
    android
    log
)
