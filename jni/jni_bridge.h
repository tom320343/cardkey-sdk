#ifndef JNI_BRIDGE_H
#define JNI_BRIDGE_H

#include <jni.h>

JNIEXPORT jstring JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeDetectEnvironment(
    JNIEnv *env, jclass cls, jobject activity);

JNIEXPORT jstring JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeGenerateDeviceId(
    JNIEnv *env, jclass cls);

JNIEXPORT jboolean JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeFetchBeijingTime(
    JNIEnv *env, jclass cls);

JNIEXPORT jboolean JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeCheckExpiry(
    JNIEnv *env, jclass cls, jstring expiryStr, jlong beijingTime);

JNIEXPORT jstring JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeFetchValidation(
    JNIEnv *env, jclass cls, jstring deviceId);

#endif
