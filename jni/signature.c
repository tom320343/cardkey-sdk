#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <android/log.h>
#include <jni.h>
#include "signature.h"

#define TAG "mthugo_sig"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

static void bytes_to_hex(const unsigned char *src, int len, char *dst) {
    for (int i = 0; i < len; i++) {
        sprintf(dst + i * 2, "%02x", src[i]);
    }
    dst[len * 2] = '\0';
}

static void strip_hex(const char *src, char *dst) {
    while (*src) {
        if ((*src >= '0' && *src <= '9') ||
            (*src >= 'a' && *src <= 'f') ||
            (*src >= 'A' && *src <= 'F')) {
            *dst++ = tolower(*src);
        }
        src++;
    }
    *dst = '\0';
}

int verify_signature(JNIEnv *env, jobject activity, const char *expected_sig) {
    if (!expected_sig || expected_sig[0] == '\0') return 0;

    char clean_expected[128];
    strip_hex(expected_sig, clean_expected);

    if (strlen(clean_expected) < 64) return 0;

    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "getPackageName",
        "()Ljava/lang/String;");
    if (!mid) return 0;

    jstring pkgName = (jstring)(*env)->CallObjectMethod(env, activity, mid);
    jmethodID pmMid = (*env)->GetMethodID(env, cls, "getPackageManager",
        "()Landroid/content/pm/PackageManager;");
    jobject pm = (*env)->CallObjectMethod(env, activity, pmMid);
    if (!pm) return 0;

    jclass pmCls = (*env)->GetObjectClass(env, pm);

    jmethodID piMid = (*env)->GetMethodID(env, pmCls, "getPackageInfo",
        "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    if (!piMid) return 0;

    jobject pkgInfo = (*env)->CallObjectMethod(env, pm, piMid, pkgName, 0x08000000);
    if (!pkgInfo) {
        pkgInfo = (*env)->CallObjectMethod(env, pm, piMid, pkgName, 64);
    }
    if (!pkgInfo) return 0;

    jclass piCls = (*env)->GetObjectClass(env, pkgInfo);

    jfieldID sigFid = (*env)->GetFieldID(env, piCls, "signatures",
        "[Landroid/content/pm/Signature;");
    if (!sigFid) return 0;

    jobjectArray sigs = (jobjectArray)(*env)->GetObjectField(env, pkgInfo, sigFid);
    if (!sigs) return 0;

    jsize len = (*env)->GetArrayLength(env, sigs);
    if (len == 0) return 0;

    jobject sig = (*env)->GetObjectArrayElement(env, sigs, 0);
    jclass sigCls = (*env)->GetObjectClass(env, sig);
    jmethodID baMid = (*env)->GetMethodID(env, sigCls, "toByteArray", "()[B");
    jbyteArray sigBytes = (jbyteArray)(*env)->CallObjectMethod(env, sig, baMid);

    jsize baLen = (*env)->GetArrayLength(env, sigBytes);
    jbyte *bytes = (*env)->GetByteArrayElements(env, sigBytes, NULL);

    jclass mdCls = (*env)->FindClass(env, "java/security/MessageDigest");
    jmethodID getInstMid = (*env)->GetStaticMethodID(env, mdCls, "getInstance",
        "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    jstring algo = (*env)->NewStringUTF(env, "SHA-256");
    jobject md = (*env)->CallStaticObjectMethod(env, mdCls, getInstMid, algo);
    (*env)->DeleteLocalRef(env, algo);

    jmethodID updateMid = (*env)->GetMethodID(env, mdCls, "update", "([B)V");
    (*env)->CallVoidMethod(env, md, updateMid, sigBytes);

    jmethodID digestMid = (*env)->GetMethodID(env, mdCls, "digest", "()[B");
    jbyteArray digest = (jbyteArray)(*env)->CallObjectMethod(env, md, digestMid);

    jsize digestLen = (*env)->GetArrayLength(env, digest);
    jbyte *digestBytes = (*env)->GetByteArrayElements(env, digest, NULL);

    char hex[128];
    bytes_to_hex((unsigned char *)digestBytes, digestLen, hex);

    (*env)->ReleaseByteArrayElements(env, digest, digestBytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, sigBytes, bytes, JNI_ABORT);

    if (strcasecmp(hex, clean_expected) == 0) return 0;

    LOGD("Signature mismatch - expected: %s, actual: %s", clean_expected, hex);
    return 1;
}
