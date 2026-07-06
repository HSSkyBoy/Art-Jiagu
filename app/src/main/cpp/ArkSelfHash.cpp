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


extern "C"
jboolean ark_get_self_cert_sha256(JNIEnv *env, jbyteArray outSha256) {

    //这里是回写签名hash的方法，这个不开源

    return JNI_TRUE;
}