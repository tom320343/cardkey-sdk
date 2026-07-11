#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/stat.h>
#include <ctype.h>
#include <android/log.h>
#include <jni.h>
#include "env_detect.h"

#define TAG "mthugo_env"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

static int file_exists(const char *path) {
    return access(path, F_OK) == 0;
}

static int file_contains(const char *path, const char *keyword) {
    FILE *f = fopen(path, "r");
    if (!f) return 0;
    char buf[4096];
    size_t n = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    if (n == 0) return 0;
    buf[n] = '\0';
    char *lower = strdup(buf);
    for (size_t i = 0; i < n; i++) lower[i] = tolower(lower[i]);
    int found = strstr(lower, keyword) != NULL;
    free(lower);
    return found;
}

int detect_xposed(void) {
    const char *xposed_paths[] = {
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so",
        "/system/framework/XposedBridge.jar",
        "/data/data/de.robv.android.xposed.installer",
        "/data/local/tmp/xposed",
        NULL
    };
    for (int i = 0; xposed_paths[i]; i++) {
        if (file_exists(xposed_paths[i])) return 1;
    }

    const char *stack_path = "/proc/self/maps";
    if (file_exists(stack_path)) {
        if (file_contains(stack_path, "xposed")) return 1;
        if (file_contains(stack_path, "substrate")) return 1;
    }

    return 0;
}

int detect_vm(void) {
    char buf[256];

    FILE *f = fopen("/system/build.prop", "r");
    if (!f) f = fopen("/default.prop", "r");
    if (!f) f = fopen("/vendor/build.prop", "r");
    if (f) {
        char line[1024];
        while (fgets(line, sizeof(line), f)) {
            for (int i = 0; line[i]; i++) line[i] = tolower(line[i]);

            if (strstr(line, "ro.product.manufacturer") &&
                (strstr(line, "genymotion") || strstr(line, "qemu"))) {
                fclose(f); return 1;
            }
            if (strstr(line, "ro.product.model") &&
                (strstr(line, "sdk") || strstr(line, "emulator"))) {
                fclose(f); return 1;
            }
            if (strstr(line, "ro.hardware") &&
                (strstr(line, "goldfish") || strstr(line, "ranchu") ||
                 strstr(line, "vbox") || strstr(line, "qemu"))) {
                fclose(f); return 1;
            }
            if (strstr(line, "ro.product.brand") &&
                strstr(line, "generic")) {
                fclose(f); return 1;
            }
            if (strstr(line, "ro.build.fingerprint") &&
                (strstr(line, "generic") || strstr(line, "vbox") ||
                 strstr(line, "emulator"))) {
                fclose(f); return 1;
            }
            if (strstr(line, "ro.kernel.qemu") && strstr(line, "=1")) {
                fclose(f); return 1;
            }
        }
        fclose(f);
    }

    if (file_exists("/dev/qemu_pipe")) return 1;
    if (file_exists("/dev/socket/qemud")) return 1;
    if (file_exists("/dev/socket/genyd")) return 1;
    if (file_exists("/dev/socket/baseband_genyd")) return 1;

    if (file_exists("/proc/tty/drivers")) {
        if (file_contains("/proc/tty/drivers", "goldfish")) return 1;
    }

    return 0;
}

int detect_vpn(JNIEnv *env, jobject activity) {
    if (file_exists("/sys/class/net/tun0")) return 1;
    if (file_exists("/sys/class/net/ppp0")) return 1;
    if (file_exists("/sys/class/net/tap0")) return 1;

    if (file_exists("/proc/net/if_inet6")) {
        if (file_contains("/proc/net/if_inet6", "tun0")) return 1;
        if (file_contains("/proc/net/if_inet6", "ppp0")) return 1;
    }

    if (env && activity) {
        jclass cls = (*env)->GetObjectClass(env, activity);
        jmethodID mid = (*env)->GetMethodID(env, cls, "getSystemService",
            "(Ljava/lang/String;)Ljava/lang/Object;");
        if (mid) {
            jstring cn = (*env)->NewStringUTF(env, "connectivity");
            jobject cm = (*env)->CallObjectMethod(env, activity, mid, cn);
            (*env)->DeleteLocalRef(env, cn);
            if (cm) {
                jclass cm_cls = (*env)->GetObjectClass(env, cm);
                jmethodID gmid = (*env)->GetMethodID(env, cm_cls, "getAllNetworks",
                    "()[Landroid/net/Network;");
                if (gmid) {
                    jobjectArray networks = (jobjectArray)(*env)->CallObjectMethod(env, cm, gmid);
                    if (networks) {
                        int len = (*env)->GetArrayLength(env, networks);
                        for (int i = 0; i < len; i++) {
                            jobject net = (*env)->GetObjectArrayElement(env, networks, i);
                            jmethodID nc_mid = (*env)->GetMethodID(env, cm_cls,
                                "getNetworkCapabilities",
                                "(Landroid/net/Network;)Landroid/net/NetworkCapabilities;");
                            if (nc_mid) {
                                jobject caps = (*env)->CallObjectMethod(env, cm, nc_mid, net);
                                if (caps) {
                                    jclass nc_cls = (*env)->GetObjectClass(env, caps);
                                    jmethodID ht_mid = (*env)->GetMethodID(env, nc_cls,
                                        "hasTransport", "(I)Z");
                                    if (ht_mid) {
                                        jboolean has = (*env)->CallBooleanMethod(env, caps, ht_mid, 4);
                                        if (has) return 1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    return 0;
}
