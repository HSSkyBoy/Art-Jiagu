#include "ArkEnvGuard.h"
#include "ArkDexLoader.h"

#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <string>
#include <vector>
#include <dirent.h>
#include <limits.h>
#include <sys/syscall.h>
#include <cstdio>

#define LOG_TAG "ArkEnvGuard"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define XOR_KEY 0x5A
#define DEC(arr) decStr(arr, sizeof(arr))
#define DEBUG_PRINT_MAPS 1

static std::string decStr(const unsigned char *data, size_t len) {
    std::string out;
    out.resize(len);

    for (size_t i = 0; i < len; i++) {
        out[i] = (char) (data[i] ^ XOR_KEY);
    }

    return out;
}

static int safeOpenReadOnly(const char *path) {
    return (int) syscall(__NR_openat, AT_FDCWD, path, O_RDONLY | O_CLOEXEC, 0);
}

static bool containsIgnoreCase(const std::string &text, const char *key) {
    if (key == nullptr) {
        return false;
    }

    std::string a = text;
    std::string b = key;

    for (size_t i = 0; i < a.size(); i++) {
        if (a[i] >= 'A' && a[i] <= 'Z') {
            a[i] = a[i] - 'A' + 'a';
        }
    }

    for (size_t i = 0; i < b.size(); i++) {
        if (b[i] >= 'A' && b[i] <= 'Z') {
            b[i] = b[i] - 'A' + 'a';
        }
    }

    return a.find(b) != std::string::npos;
}

struct XorStr {
    const unsigned char *data;
    size_t len;
};

// 明文：/memfd:jit-cache-zygisk_lsposed
static const unsigned char XP_FEATURE_0[] = {
        0x75, 0x37, 0x3F, 0x37, 0x3C, 0x3E, 0x60, 0x30,
        0x33, 0x2E, 0x77, 0x39, 0x3B, 0x39, 0x32, 0x3F,
        0x77, 0x20, 0x23, 0x3D, 0x33, 0x29, 0x31, 0x05,
        0x36, 0x29, 0x2A, 0x35, 0x29, 0x3F, 0x3E
};

// 明文：/data/adb/lspd
static const unsigned char XP_FEATURE_1[] = {
        0x75, 0x3E, 0x3B, 0x2E, 0x3B, 0x75, 0x3B,
        0x3E, 0x38, 0x75, 0x36, 0x29, 0x2A, 0x3E
};

// 明文：/data/adb/modules/zygisk_lsposed
static const unsigned char XP_FEATURE_2[] = {
        0x75, 0x3E, 0x3B, 0x2E, 0x3B, 0x75, 0x3B, 0x3E,
        0x38, 0x75, 0x37, 0x35, 0x3E, 0x2F, 0x36, 0x3F,
        0x29, 0x75, 0x20, 0x23, 0x3D, 0x33, 0x29, 0x31,
        0x05, 0x36, 0x29, 0x2A, 0x35, 0x29, 0x3F, 0x3E
};

// 明文：/data/adb/riru
static const unsigned char XP_FEATURE_3[] = {
        0x75, 0x3E, 0x3B, 0x2E, 0x3B, 0x75, 0x3B,
        0x3E, 0x38, 0x75, 0x28, 0x33, 0x28, 0x2F
};

// 明文：/data/adb/modules/riru
static const unsigned char XP_FEATURE_4[] = {
        0x75, 0x3E, 0x3B, 0x2E, 0x3B, 0x75, 0x3B, 0x3E,
        0x38, 0x75, 0x37, 0x35, 0x3E, 0x2F, 0x36, 0x3F,
        0x29, 0x75, 0x28, 0x33, 0x28, 0x2F
};

// 明文：liblsposed
static const unsigned char XP_FEATURE_5[] = {
        0x36, 0x33, 0x38, 0x36, 0x29, 0x2A, 0x35, 0x29, 0x3F, 0x3E
};

// 明文：libxposed
static const unsigned char XP_FEATURE_6[] = {
        0x36, 0x33, 0x38, 0x22, 0x2A, 0x35, 0x29, 0x3F, 0x3E
};

// 明文：libedxp
static const unsigned char XP_FEATURE_7[] = {
        0x36, 0x33, 0x38, 0x3F, 0x3E, 0x22, 0x2A
};

// 明文：libriru
static const unsigned char XP_FEATURE_8[] = {
        0x36, 0x33, 0x38, 0x28, 0x33, 0x28, 0x2F
};

// 明文：libzygisk
static const unsigned char XP_FEATURE_9[] = {
        0x36, 0x33, 0x38, 0x20, 0x23, 0x3D, 0x33, 0x29, 0x31
};



static const XorStr XP_FEATURES[] = {
        //{XP_FEATURE_0, sizeof(XP_FEATURE_0)},
        {XP_FEATURE_1, sizeof(XP_FEATURE_1)},
        {XP_FEATURE_2, sizeof(XP_FEATURE_2)},
        {XP_FEATURE_3, sizeof(XP_FEATURE_3)},
        {XP_FEATURE_4, sizeof(XP_FEATURE_4)},
        {XP_FEATURE_5, sizeof(XP_FEATURE_5)},
        {XP_FEATURE_6, sizeof(XP_FEATURE_6)},
        {XP_FEATURE_7, sizeof(XP_FEATURE_7)},
        {XP_FEATURE_8, sizeof(XP_FEATURE_8)},
        {XP_FEATURE_9, sizeof(XP_FEATURE_9)}
};

// 明文：/proc/self/fd
static const unsigned char STR_PROC_FD[] = {
        0x75, 0x2A, 0x28, 0x35, 0x39, 0x75, 0x29, 0x3F,
        0x36, 0x3C, 0x75, 0x3C, 0x3E
};

// 明文：/proc/self/fd/%s
static const unsigned char STR_PROC_FD_FORMAT[] = {
        0x75, 0x2A, 0x28, 0x35, 0x39, 0x75, 0x29, 0x3F,
        0x36, 0x3C, 0x75, 0x3C, 0x3E, 0x75, 0x7F, 0x29
};

// 明文：/proc/self/maps
static const unsigned char STR_PROC_MAPS[] = {
        0x75, 0x2A, 0x28, 0x35, 0x39, 0x75, 0x29, 0x3F,
        0x36, 0x3C, 0x75, 0x37, 0x3B, 0x2A, 0x29
};

// 明文：de/robv/android/xposed/XposedBridge
static const unsigned char STR_XPOSED_BRIDGE[] = {
        0x3E, 0x3F, 0x75, 0x28, 0x35, 0x38, 0x2C, 0x75,
        0x3B, 0x34, 0x3E, 0x28, 0x35, 0x33, 0x3E, 0x75,
        0x22, 0x2A, 0x35, 0x29, 0x3F, 0x3E, 0x75, 0x02,
        0x2A, 0x35, 0x29, 0x3F, 0x3E, 0x18, 0x28, 0x33,
        0x3E, 0x3D, 0x3F
};

// 明文：java/lang/Throwable
static const unsigned char STR_THROWABLE[] = {
        0x30, 0x3B, 0x2C, 0x3B, 0x75, 0x36, 0x3B, 0x34,
        0x3D, 0x75, 0x0E, 0x32, 0x28, 0x35, 0x2D, 0x3B,
        0x38, 0x36, 0x3F
};

// 明文：<init>
static const unsigned char STR_INIT[] = {
        0x66, 0x33, 0x34, 0x33, 0x2E, 0x64
};

// 明文：()V
static const unsigned char STR_SIG_VOID[] = {
        0x72, 0x73, 0x0C
};

// 明文：getStackTrace
static const unsigned char STR_GET_STACK_TRACE[] = {
        0x3D, 0x3F, 0x2E, 0x09, 0x2E, 0x3B, 0x39,
        0x31, 0x0E, 0x28, 0x3B, 0x39, 0x3F
};

// 明文：()[Ljava/lang/StackTraceElement;
static const unsigned char STR_SIG_STACK_TRACE[] = {
        0x72, 0x73, 0x01, 0x16, 0x30, 0x3B, 0x2C, 0x3B,
        0x75, 0x36, 0x3B, 0x34, 0x3D, 0x75, 0x09, 0x2E,
        0x3B, 0x39, 0x31, 0x0E, 0x28, 0x3B, 0x39, 0x3F,
        0x1F, 0x36, 0x3F, 0x37, 0x3F, 0x34, 0x2E, 0x61
};

// 明文：java/lang/StackTraceElement
static const unsigned char STR_STACK_TRACE_ELEMENT[] = {
        0x30, 0x3B, 0x2C, 0x3B, 0x75, 0x36, 0x3B, 0x34,
        0x3D, 0x75, 0x09, 0x2E, 0x3B, 0x39, 0x31, 0x0E,
        0x28, 0x3B, 0x39, 0x3F, 0x1F, 0x36, 0x3F, 0x37,
        0x3F, 0x34, 0x2E
};

// 明文：toString
static const unsigned char STR_TO_STRING[] = {
        0x2E, 0x35, 0x09, 0x2E, 0x28, 0x33, 0x34, 0x3D
};

// 明文：()Ljava/lang/String;
static const unsigned char STR_SIG_STRING[] = {
        0x72, 0x73, 0x16, 0x30, 0x3B, 0x2C, 0x3B, 0x75,
        0x36, 0x3B, 0x34, 0x3D, 0x75, 0x09, 0x2E, 0x28,
        0x33, 0x34, 0x3D, 0x61
};

// 明文：/data/app/
static const unsigned char STR_DATA_APP[] = {
        0x75, 0x3E, 0x3B, 0x2E, 0x3B, 0x75, 0x3B, 0x2A, 0x2A, 0x75
};

// 明文：/base.apk
static const unsigned char STR_BASE_APK[] = {
        0x75, 0x38, 0x3B, 0x29, 0x3F, 0x74, 0x3B, 0x2A, 0x31
};

// 明文：getPackageName
static const unsigned char STR_GET_PACKAGE_NAME[] = {
        0x3D, 0x3F, 0x2E, 0x0A, 0x3B, 0x39, 0x31,
        0x3B, 0x3D, 0x3F, 0x14, 0x3B, 0x37, 0x3F
};
static std::string getCurrentPackageName(JNIEnv *env, jobject context) {
    if (env == nullptr || context == nullptr) {
        return "";
    }

    jclass clsContext = env->GetObjectClass(context);
    if (clsContext == nullptr) {
        env->ExceptionClear();
        return "";
    }

    std::string methodName = DEC(STR_GET_PACKAGE_NAME);
    std::string methodSig = DEC(STR_SIG_STRING);

    jmethodID midGetPackageName = env->GetMethodID(
            clsContext,
            methodName.c_str(),
            methodSig.c_str()
    );

    if (midGetPackageName == nullptr) {
        env->ExceptionClear();
        return "";
    }

    jstring packageNameJ = (jstring) env->CallObjectMethod(context, midGetPackageName);
    if (env->ExceptionCheck() || packageNameJ == nullptr) {
        env->ExceptionClear();
        return "";
    }

    const char *packageName = env->GetStringUTFChars(packageNameJ, nullptr);
    if (packageName == nullptr) {
        env->DeleteLocalRef(packageNameJ);
        return "";
    }

    std::string result(packageName);

    env->ReleaseStringUTFChars(packageNameJ, packageName);
    env->DeleteLocalRef(packageNameJ);

    return result;
}

static bool checkThirdPartyApkInjected(JNIEnv *env, jobject context, const std::string &line) {
    std::string dataApp = DEC(STR_DATA_APP);
    std::string baseApk = DEC(STR_BASE_APK);

    if (!containsIgnoreCase(line, dataApp.c_str())) {
        return false;
    }

    if (!containsIgnoreCase(line, baseApk.c_str())) {
        return false;
    }

    std::string currentPackage = getCurrentPackageName(env, context);
    if (!currentPackage.empty() && containsIgnoreCase(line, currentPackage.c_str())) {
        return false;
    }

    //LOGE("检测到第三方 APK 注入");
    //LOGE("当前包名：%s", currentPackage.c_str());
    //LOGE("命中maps：%s", line.c_str());

    return true;
}
static std::string findMatchedFeature(const std::string &text) {
    for (size_t i = 0; i < sizeof(XP_FEATURES) / sizeof(XP_FEATURES[0]); i++) {
        std::string feature = decStr(XP_FEATURES[i].data, XP_FEATURES[i].len);

        if (containsIgnoreCase(text, feature.c_str())) {
            return feature;
        }
    }

    return "";
}

static bool checkFdForXp() {
    std::string procFd = DEC(STR_PROC_FD);

    DIR *dir = opendir(procFd.c_str());
    if (dir == nullptr) {
        //LOGE("打开 /proc/self/fd 失败");
        return false;
    }

    struct dirent *entry;
    char linkPath[PATH_MAX];
    char realPath[PATH_MAX];

    std::string procFdFormat = DEC(STR_PROC_FD_FORMAT);

    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_name[0] == '.') {
            continue;
        }

        snprintf(linkPath, sizeof(linkPath), procFdFormat.c_str(), entry->d_name);

        ssize_t len = readlink(linkPath, realPath, sizeof(realPath) - 1);
        if (len <= 0) {
            continue;
        }

        realPath[len] = '\0';

        std::string s(realPath);
        std::string matched = findMatchedFeature(s);

        if (!matched.empty()) {
            //LOGE("检测到 fd 特征");
            //LOGE("命中特征：%s", matched.c_str());
            //LOGE("命中fd：%s -> %s", linkPath, realPath);

            closedir(dir);
            return true;
        }
    }

    closedir(dir);
    return false;
}

static bool checkMapsForXp(JNIEnv *env, jobject context) {
    std::string procMaps = DEC(STR_PROC_MAPS);

    int fd = safeOpenReadOnly(procMaps.c_str());
    if (fd < 0) {
        //LOGE("syscall打开 /proc/self/maps 失败");
        return false;
    }

    char buffer[4096];
    std::string cache;

    while (true) {
        ssize_t readSize = syscall(__NR_read, fd, buffer, sizeof(buffer));
        if (readSize <= 0) {
            break;
        }

        cache.append(buffer, readSize);

        size_t pos;
        while ((pos = cache.find('\n')) != std::string::npos) {
            std::string line = cache.substr(0, pos);
            cache.erase(0, pos + 1);

            if (checkThirdPartyApkInjected(env, context, line)) {
                syscall(__NR_close, fd);
                return true;
            }

            #if DEBUG_PRINT_MAPS
                //LOGE("maps行：%s", line.c_str());
            #endif

            std::string matched = findMatchedFeature(line);
            if (!matched.empty()) {
                //LOGE("检测到 maps 特征");
                //LOGE("命中特征：%s", matched.c_str());
                //LOGE("命中maps：%s", line.c_str());

                syscall(__NR_close, fd);
                return true;
            }
        }
    }

    if (!cache.empty()) {
        if (checkThirdPartyApkInjected(env, context, cache)) {
            syscall(__NR_close, fd);
            return true;
        }
        std::string matched = findMatchedFeature(cache);
        if (!matched.empty()) {
            //LOGE("检测到 maps 特征");
            //LOGE("命中特征：%s", matched.c_str());
            //LOGE("命中maps：%s", cache.c_str());

            syscall(__NR_close, fd);
            return true;
        }
    }

    syscall(__NR_close, fd);
    return false;
}

static bool checkXposedBridgeClass(JNIEnv *env) {
    if (env == nullptr) {
        return false;
    }

    std::string xposedBridgeClass = DEC(STR_XPOSED_BRIDGE);

    jclass cls = env->FindClass(xposedBridgeClass.c_str());
    if (cls != nullptr) {
        env->DeleteLocalRef(cls);

        //LOGE("检测到 Java 类特征");
        //LOGE("命中特征：de.robv.android.xposed.XposedBridge");

        return true;
    }

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    return false;
}

static bool checkStackForXp(JNIEnv *env) {
    if (env == nullptr) {
        return false;
    }

    std::string throwableClass = DEC(STR_THROWABLE);
    jclass clsThrowable = env->FindClass(throwableClass.c_str());

    if (clsThrowable == nullptr) {
        env->ExceptionClear();
        return false;
    }

    std::string initName = DEC(STR_INIT);
    std::string sigVoid = DEC(STR_SIG_VOID);

    jmethodID midInit = env->GetMethodID(
            clsThrowable,
            initName.c_str(),
            sigVoid.c_str()
    );

    std::string getStackTraceName = DEC(STR_GET_STACK_TRACE);
    std::string getStackTraceSig = DEC(STR_SIG_STACK_TRACE);

    jmethodID midGetStackTrace = env->GetMethodID(
            clsThrowable,
            getStackTraceName.c_str(),
            getStackTraceSig.c_str()
    );

    if (midInit == nullptr || midGetStackTrace == nullptr) {
        env->ExceptionClear();
        return false;
    }

    jobject throwable = env->NewObject(clsThrowable, midInit);
    if (throwable == nullptr) {
        env->ExceptionClear();
        return false;
    }

    jobjectArray stackArray = (jobjectArray) env->CallObjectMethod(throwable, midGetStackTrace);
    if (env->ExceptionCheck() || stackArray == nullptr) {
        env->ExceptionClear();
        return false;
    }

    std::string stackTraceElementClass = DEC(STR_STACK_TRACE_ELEMENT);
    jclass clsStackTraceElement = env->FindClass(stackTraceElementClass.c_str());

    std::string toStringName = DEC(STR_TO_STRING);
    std::string sigString = DEC(STR_SIG_STRING);

    jmethodID midToString = env->GetMethodID(
            clsStackTraceElement,
            toStringName.c_str(),
            sigString.c_str()
    );

    if (midToString == nullptr) {
        env->ExceptionClear();
        return false;
    }

    jsize count = env->GetArrayLength(stackArray);

    for (jsize i = 0; i < count; i++) {
        jobject item = env->GetObjectArrayElement(stackArray, i);
        if (item == nullptr) {
            continue;
        }

        jstring textJ = (jstring) env->CallObjectMethod(item, midToString);
        if (textJ == nullptr) {
            env->DeleteLocalRef(item);
            continue;
        }

        const char *text = env->GetStringUTFChars(textJ, nullptr);
        if (text != nullptr) {
            std::string s(text);

            env->ReleaseStringUTFChars(textJ, text);

            std::string matched = findMatchedFeature(s);
            if (!matched.empty()) {
                //LOGE("检测到调用栈特征");
                //LOGE("命中特征：%s", matched.c_str());
                //LOGE("命中堆栈：%s", s.c_str());

                env->DeleteLocalRef(textJ);
                env->DeleteLocalRef(item);
                return true;
            }
        }

        env->DeleteLocalRef(textJ);
        env->DeleteLocalRef(item);
    }

    return false;
}

static bool ArkEnvGuard_DetectXp(JNIEnv *env, jobject context) {
    if (checkMapsForXp(env, context)) {
        //LOGE("检测到 maps 中存在 XP/LSPosed 特征");
        return true;
    }

    if (checkXposedBridgeClass(env)) {
        //LOGE("检测到 XposedBridge 类");
        return true;
    }

    if (checkStackForXp(env)) {
        //LOGE("检测到调用栈中存在 XP/LSPosed 特征");
        return true;
    }

    if (checkFdForXp()) {
        //LOGE("检测到 fd 中存在 XP/LSPosed 特征");
        return true;
    }

    return false;
}

static bool ArkEnvGuard_CheckAndLoad_Impl(JNIEnv *env, jobject context) {
    //LOGI("进入 ArkEnvGuard_CheckAndLoad_Impl");

    if (env == nullptr || context == nullptr) {
        //LOGE("env或context为空");
        return false;
    }

    ArkDexLoaderFunc loaderFunc = ArkDexLoader_GetEntry();
    if (loaderFunc == nullptr) {
        //LOGE("获取LoaderDEX入口失败");
        return false;
    }

    if (ArkEnvGuard_DetectXp(env, context)) {
        //LOGE("环境检测不通过，停止加载DEX");
        return false;
    }

    //LOGI("开始通过函数指针调用LoaderDEX");
    return loaderFunc(env, context);
}

ArkEnvGuardFunc ArkEnvGuard_GetEntry() {
    volatile ArkEnvGuardFunc fn = ArkEnvGuard_CheckAndLoad_Impl;
    return fn;
}