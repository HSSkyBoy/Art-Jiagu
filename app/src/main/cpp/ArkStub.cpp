#include <jni.h>
#include <android/log.h>
#include <string>
#include "ArkEnvGuard.h"

#define LOG_TAG "ArkStub"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void native_DtcLoader(JNIEnv *env, jclass clazz, jobject context) {
    //LOGI("进入 native_DtcLoader");

    if (context == nullptr) {
        //LOGE("context 为空");
        return;
    }

    //LoaderDEX(env, context);
    ArkEnvGuardFunc guardFunc = ArkEnvGuard_GetEntry();
    if (guardFunc != nullptr) {
        guardFunc(env, context);
    }
}

static void native_attachBaseContext(JNIEnv *env, jobject thiz, jobject context) {
    if (context == nullptr) {
        return;
    }

    jclass contextWrapperClass = env->FindClass("android/content/ContextWrapper");
    if (contextWrapperClass == nullptr) {
        env->ExceptionClear();
        return;
    }

    jmethodID midAttachBaseContext = env->GetMethodID(
            contextWrapperClass,
            "attachBaseContext",
            "(Landroid/content/Context;)V"
    );

    if (midAttachBaseContext == nullptr) {
        env->ExceptionClear();
        return;
    }

    env->CallNonvirtualVoidMethod(
            thiz,
            contextWrapperClass,
            midAttachBaseContext,
            context
    );

    if (env->ExceptionCheck()) {
        return;
    }

    //LoaderDEX(env, context);
    ArkEnvGuardFunc guardFunc = ArkEnvGuard_GetEntry();
    if (guardFunc != nullptr) {
        guardFunc(env, context);
    }
}

static std::string jstringToString(JNIEnv *env, jstring str) {
    if (str == nullptr) {
        return "";
    }

    const char *chars = env->GetStringUTFChars(str, nullptr);
    if (chars == nullptr) {
        return "";
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(str, chars);
    return result;
}

static void dotToSlash(std::string &name) {
    for (size_t i = 0; i < name.length(); i++) {
        if (name[i] == '.') {
            name[i] = '/';
        }
    }
}

static std::string getStubClassNameFromProperty(JNIEnv *env) {
    jclass clsSystem = env->FindClass("java/lang/System");
    if (clsSystem == nullptr) {
        env->ExceptionClear();
        return "";
    }

    jmethodID midGetProperty = env->GetStaticMethodID(
            clsSystem,
            "getProperty",
            "(Ljava/lang/String;)Ljava/lang/String;"
    );

    if (midGetProperty == nullptr) {
        env->ExceptionClear();
        return "";
    }

    jstring key = env->NewStringUTF("ark");

    jstring value = (jstring) env->CallStaticObjectMethod(
            clsSystem,
            midGetProperty,
            key
    );

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return "";
    }

    std::string className = jstringToString(env, value);
    dotToSlash(className);

    return className;
}

static JNINativeMethod gDtcLoaderMethods[] = {
        {
                "DtcLoader",
                "(Landroid/content/Context;)V",
                (void *) native_DtcLoader
        }
};

static JNINativeMethod gAttachMethods[] = {
        {
                "attachBaseContext",
                "(Landroid/content/Context;)V",
                (void *) native_attachBaseContext
        }
};

static int registerNativeMethods(JNIEnv *env) {
    std::string className = getStubClassNameFromProperty(env);

    if (className.empty()) {
        className = "com/ark/safe/StubApp";
    }

    jclass clazz = env->FindClass(className.c_str());
    if (clazz == nullptr) {
        env->ExceptionClear();
        return JNI_FALSE;
    }

    bool hasRegistered = false;

    if (env->RegisterNatives(
            clazz,
            gAttachMethods,
            sizeof(gAttachMethods) / sizeof(gAttachMethods[0])
    ) == JNI_OK) {
        hasRegistered = true;
    } else {
        env->ExceptionClear();
    }

    if (env->RegisterNatives(
            clazz,
            gDtcLoaderMethods,
            sizeof(gDtcLoaderMethods) / sizeof(gDtcLoaderMethods[0])
    ) == JNI_OK) {
        hasRegistered = true;
    } else {
        env->ExceptionClear();
    }

    if (!hasRegistered) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        //LOGE("GetEnv 失败");
        return JNI_ERR;
    }

    if (!registerNativeMethods(env)) {
        return JNI_ERR;
    }

    //LOGI("JNI_OnLoad 完成");
    return JNI_VERSION_1_6;
}