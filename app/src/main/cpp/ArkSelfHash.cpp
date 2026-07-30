// ArkSelfHash.cpp
#include <jni.h>
#include <stdint.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <stdio.h>
#include <android/log.h>
#include <sys/syscall.h>
#include <errno.h>
#include "ArkSelfHash.h"
#include "ApkSignatureVerifier.h"

extern "C"
jboolean ark_get_self_cert_sha256(JNIEnv *env, jbyteArray outSha256) {
    if (env == nullptr || outSha256 == nullptr) {
        return JNI_FALSE;
    }

    uint8_t sha32[32];
    memset(sha32, 0, sizeof(sha32));

    // Try getting application context via ActivityThread
    jobject context = nullptr;
    jclass clsActivityThread = env->FindClass("android/app/ActivityThread");
    if (clsActivityThread != nullptr) {
        jmethodID midCurrentApp = env->GetStaticMethodID(
                clsActivityThread,
                "currentApplication",
                "()Landroid/app/Application;"
        );
        if (midCurrentApp != nullptr) {
            context = env->CallStaticObjectMethod(clsActivityThread, midCurrentApp);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                context = nullptr;
            }
        }
    }

    bool success = ReadApkSigningBlockSha256(env, context, sha32);

    if (context != nullptr) {
        env->DeleteLocalRef(context);
    }

    if (!success) {
        return JNI_FALSE;
    }

    env->SetByteArrayRegion(
            outSha256,
            0,
            32,
            reinterpret_cast<const jbyte *>(sha32)
    );

    return JNI_TRUE;
}