#ifndef ARK_DEX_LOADER_H
#define ARK_DEX_LOADER_H

#include <jni.h>

typedef bool (*ArkDexLoaderFunc)(JNIEnv *env, jobject context);

bool LoaderDEX(JNIEnv *env, jobject context);

ArkDexLoaderFunc ArkDexLoader_GetEntry();

#endif