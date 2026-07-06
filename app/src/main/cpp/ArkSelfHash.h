#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

jboolean ark_get_self_cert_sha256(
        JNIEnv *env,
        jbyteArray outSha256);

#ifdef __cplusplus
}
#endif