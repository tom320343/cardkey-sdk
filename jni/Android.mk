LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := mthugo
LOCAL_SRC_FILES := jni_bridge.c env_detect.c signature.c timecheck.c utils.c
LOCAL_LDLIBS := -llog -landroid
include $(BUILD_SHARED_LIBRARY)
