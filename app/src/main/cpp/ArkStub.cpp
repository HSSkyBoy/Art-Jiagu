#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include "ArkEnvGuard.h"

#define LOG_TAG "ArkStub"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static jobject gStringContext = nullptr;
static std::once_flag gStringPoolOnce;
static std::vector<std::vector<unsigned char>> gEncryptedStrings;

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

    if (gStringContext == nullptr) {
        gStringContext = env->NewGlobalRef(context);
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

static uint32_t readBe32(const unsigned char *data) {
    return (static_cast<uint32_t>(data[0]) << 24) | (static_cast<uint32_t>(data[1]) << 16) |
           (static_cast<uint32_t>(data[2]) << 8) | static_cast<uint32_t>(data[3]);
}

static void loadStringPool(JNIEnv *env) {
    if (gStringContext == nullptr) return;
    jclass contextClass = env->GetObjectClass(gStringContext);
    jmethodID getAssets = env->GetMethodID(contextClass, "getAssets", "()Landroid/content/res/AssetManager;");
    jobject assets = env->CallObjectMethod(gStringContext, getAssets);
    jclass assetsClass = env->GetObjectClass(assets);
    jmethodID open = env->GetMethodID(assetsClass, "open", "(Ljava/lang/String;)Ljava/io/InputStream;");
    jstring name = env->NewStringUTF("top_strings.bin");
    jobject input = env->CallObjectMethod(assets, open, name);
    if (env->ExceptionCheck() || input == nullptr) { env->ExceptionClear(); return; }
    jclass inputClass = env->GetObjectClass(input);
    jmethodID read = env->GetMethodID(inputClass, "read", "([B)I");
    jbyteArray buffer = env->NewByteArray(4096);
    std::vector<unsigned char> data;
    while (true) {
        jint count = env->CallIntMethod(input, read, buffer);
        if (count <= 0 || env->ExceptionCheck()) break;
        size_t offset = data.size(); data.resize(offset + count);
        env->GetByteArrayRegion(buffer, 0, count, reinterpret_cast<jbyte *>(data.data() + offset));
    }
    if (env->ExceptionCheck() || data.size() < 12 || readBe32(data.data()) != 0x41535452 || readBe32(data.data() + 4) != 1) { env->ExceptionClear(); return; }
    const uint32_t count = readBe32(data.data() + 8); size_t cursor = 12;
    for (uint32_t i = 0; i < count; ++i) {
        if (cursor + 4 > data.size()) { gEncryptedStrings.clear(); return; }
        const uint32_t length = readBe32(data.data() + cursor); cursor += 4;
        if (cursor + length > data.size()) { gEncryptedStrings.clear(); return; }
        gEncryptedStrings.emplace_back(data.begin() + cursor, data.begin() + cursor + length); cursor += length;
    }
}

static jstring native_decodeString(JNIEnv *env, jclass clazz, jint index) {
    std::call_once(gStringPoolOnce, [env]() { loadStringPool(env); });
    if (index < 0 || static_cast<size_t>(index) >= gEncryptedStrings.size()) return env->NewStringUTF("");
    const auto &cipher = gEncryptedStrings[index]; std::vector<char> plain(cipher.size() + 1, '\0');
    for (size_t i = 0; i < cipher.size(); ++i) plain[i] = static_cast<char>(cipher[i] ^ ((0xa7 + i * 31) & 0xff));
    return env->NewStringUTF(plain.data());
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

    jstring key = env->NewStringUTF("top");

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

static JNINativeMethod gStringMethods[] = {
        {
                "decodeString",
                "(I)Ljava/lang/String;",
                (void *) native_decodeString
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

    if (env->RegisterNatives(
            clazz,
            gStringMethods,
            sizeof(gStringMethods) / sizeof(gStringMethods[0])
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
