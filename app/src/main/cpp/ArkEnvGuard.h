#ifndef ARK_ENV_GUARD_H
#define ARK_ENV_GUARD_H

#include <jni.h>

typedef bool (*ArkEnvGuardFunc)(JNIEnv *env, jobject context);

ArkEnvGuardFunc ArkEnvGuard_GetEntry();

#endif