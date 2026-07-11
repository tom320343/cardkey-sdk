#ifndef SIGNATURE_H
#define SIGNATURE_H

#include <jni.h>

int verify_signature(JNIEnv *env, jobject activity, const char *expected_sig);

#endif
