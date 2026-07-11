#ifndef TIMECHECK_H
#define TIMECHECK_H

#include <jni.h>

jlong fetch_beijing_time(void);
int check_expiry(const char *expiry_str, jlong beijing_time);
char* fetch_validation_url(const char *url, const char *device_id);

#endif
