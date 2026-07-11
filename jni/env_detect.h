#ifndef ENV_DETECT_H
#define ENV_DETECT_H

#include <jni.h>

int detect_xposed(void);
int detect_vm(void);
int detect_vpn(JNIEnv *env, jobject activity);

#endif
