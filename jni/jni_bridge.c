#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "jni_bridge.h"
#include "env_detect.h"
#include "signature.h"
#include "timecheck.h"
#include "utils.h"

static char g_validation_url[512] = {0};
static char g_contact_url[512] = {0};
static char g_expected_sig[128] = {0};
static int g_urls_loaded = 0;

static void ensure_urls_loaded(JNIEnv *env) {
    if (g_urls_loaded) return;
    jclass cls = (*env)->FindClass(env, "com/mthugo/secretkey5/mthugosctc");
    if (!cls) return;

    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "getValidationUrl", "()Ljava/lang/String;");
    if (mid) {
        jstring js = (jstring)(*env)->CallStaticObjectMethod(env, cls, mid);
        if (js) {
            const char *str = (*env)->GetStringUTFChars(env, js, NULL);
            strncpy(g_validation_url, str, sizeof(g_validation_url) - 1);
            (*env)->ReleaseStringUTFChars(env, js, str);
        }
    }

    mid = (*env)->GetStaticMethodID(env, cls, "getContactUrl", "()Ljava/lang/String;");
    if (mid) {
        jstring js = (jstring)(*env)->CallStaticObjectMethod(env, cls, mid);
        if (js) {
            const char *str = (*env)->GetStringUTFChars(env, js, NULL);
            strncpy(g_contact_url, str, sizeof(g_contact_url) - 1);
            (*env)->ReleaseStringUTFChars(env, js, str);
        }
    }

    mid = (*env)->GetStaticMethodID(env, cls, "getExpectedSignature", "()Ljava/lang/String;");
    if (mid) {
        jstring js = (jstring)(*env)->CallStaticObjectMethod(env, cls, mid);
        if (js) {
            const char *str = (*env)->GetStringUTFChars(env, js, NULL);
            strncpy(g_expected_sig, str, sizeof(g_expected_sig) - 1);
            (*env)->ReleaseStringUTFChars(env, js, str);
        }
    }

    g_urls_loaded = 1;
}

JNIEXPORT jstring JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeDetectEnvironment(
    JNIEnv *env, jclass cls, jobject activity) {
    
    ensure_urls_loaded(env);

    int xposed = detect_xposed();
    int vm = detect_vm();
    int vpn = detect_vpn(env, activity);
    int sig = verify_signature(env, activity, g_expected_sig);

    if (sig == 1) {
        return (*env)->NewStringUTF(env, "签名被更改，应用退出");
    }
    if (xposed && vm) {
        return (*env)->NewStringUTF(env, "检测到Xposed模块及虚拟机环境");
    }
    if (xposed) {
        return (*env)->NewStringUTF(env, "检测到Xposed模块");
    }
    if (vm) {
        return (*env)->NewStringUTF(env, "检测到虚拟机环境");
    }
    if (vpn) {
        return (*env)->NewStringUTF(env, "检测到VPN环境，即将退出");
    }
    return NULL;
}

JNIEXPORT jstring JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeGenerateDeviceId(
    JNIEnv *env, jclass cls) {
    char buf[16];
    generate_random_id(buf, sizeof(buf));
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jboolean JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeFetchBeijingTime(
    JNIEnv *env, jclass cls) {
    jlong bt = fetch_beijing_time();
    if (bt > 0) {
        jclass jcls = (*env)->FindClass(env, "com/mthugo/secretkey5/mthugosctc");
        if (jcls) {
            jfieldID fid = (*env)->GetStaticFieldID(env, jcls, "cachedBeijingTime", "J");
            if (fid) {
                (*env)->SetStaticLongField(env, jcls, fid, bt);
            }
        }
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeCheckExpiry(
    JNIEnv *env, jclass cls, jstring expiryStr, jlong beijingTime) {
    const char *exp = (*env)->GetStringUTFChars(env, expiryStr, NULL);
    int result = check_expiry(exp, beijingTime);
    (*env)->ReleaseStringUTFChars(env, expiryStr, exp);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_mthugo_secretkey5_mthugosctc_nativeFetchValidation(
    JNIEnv *env, jclass cls, jstring deviceId) {
    const char *did = (*env)->GetStringUTFChars(env, deviceId, NULL);
    char *result = fetch_validation_url(g_validation_url, did);
    (*env)->ReleaseStringUTFChars(env, deviceId, did);
    if (result) {
        jstring js = (*env)->NewStringUTF(env, result);
        free(result);
        return js;
    }
    return NULL;
}
