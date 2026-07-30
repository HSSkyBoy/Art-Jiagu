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

#define XOR_KEY_0 0x73
#define XOR_KEY_1 0x42
#define DEC(arr) decStr(arr, sizeof(arr))
#define DEBUG_PRINT_MAPS 1

static std::string decStr(const unsigned char *data, size_t len) {
    std::string out;
    out.resize(len);
    static const unsigned char key[] = {XOR_KEY_0, XOR_KEY_1};

    for (size_t i = 0; i < len; i++) {
        out[i] = (char) (data[i] ^ key[i % 2]);
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
static const unsigned char XP_FEATURE_0[] = {0x5C, 0x2F, 0x16, 0x2F, 0x15, 0x26, 0x49, 0x28, 0x1A, 0x36, 0x5E, 0x21, 0x12, 0x21, 0x1B, 0x27, 0x5E, 0x38, 0x0A, 0x25, 0x1A, 0x31, 0x18, 0x1D, 0x1F, 0x31, 0x03, 0x2D, 0x00, 0x27, 0x17};

// 明文：/data/adb/lspd
static const unsigned char XP_FEATURE_1[] = {0x5C, 0x26, 0x12, 0x36, 0x12, 0x6D, 0x12, 0x26, 0x11, 0x6D, 0x1F, 0x31, 0x03, 0x26};

// 明文：/data/adb/modules/zygisk_lsposed
static const unsigned char XP_FEATURE_2[] = {0x5C, 0x26, 0x12, 0x36, 0x12, 0x6D, 0x12, 0x26, 0x11, 0x6D, 0x1E, 0x2D, 0x17, 0x37, 0x1F, 0x27, 0x00, 0x6D, 0x09, 0x3B, 0x14, 0x2B, 0x00, 0x29, 0x2C, 0x2E, 0x00, 0x32, 0x1C, 0x31, 0x16, 0x26};

// 明文：/data/adb/riru
static const unsigned char XP_FEATURE_3[] = {0x5C, 0x26, 0x12, 0x36, 0x12, 0x6D, 0x12, 0x26, 0x11, 0x6D, 0x01, 0x2B, 0x01, 0x37};

// 明文：/data/adb/modules/riru
static const unsigned char XP_FEATURE_4[] = {0x5C, 0x26, 0x12, 0x36, 0x12, 0x6D, 0x12, 0x26, 0x11, 0x6D, 0x1E, 0x2D, 0x17, 0x37, 0x1F, 0x27, 0x00, 0x6D, 0x01, 0x2B, 0x01, 0x37};

// 明文：liblsposed
static const unsigned char XP_FEATURE_5[] = {0x1F, 0x2B, 0x11, 0x2E, 0x00, 0x32, 0x1C, 0x31, 0x16, 0x26};

// 明文：libxposed
static const unsigned char XP_FEATURE_6[] = {0x1F, 0x2B, 0x11, 0x3A, 0x03, 0x2D, 0x00, 0x27, 0x17};

// 明文：libedxp
static const unsigned char XP_FEATURE_7[] = {0x1F, 0x2B, 0x11, 0x27, 0x17, 0x3A, 0x03};

// 明文：libriru
static const unsigned char XP_FEATURE_8[] = {0x1F, 0x2B, 0x11, 0x30, 0x1A, 0x30, 0x06};

// 明文：libzygisk
static const unsigned char XP_FEATURE_9[] = {0x1F, 0x2B, 0x11, 0x38, 0x0A, 0x25, 0x1A, 0x31, 0x18};



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
static const unsigned char STR_PROC_FD[] = {0x5C, 0x32, 0x01, 0x2D, 0x10, 0x6D, 0x00, 0x27, 0x1F, 0x24, 0x5C, 0x24, 0x17};

// 明文：/proc/self/fd/%s
static const unsigned char STR_PROC_FD_FORMAT[] = {0x5C, 0x32, 0x01, 0x2D, 0x10, 0x6D, 0x00, 0x27, 0x1F, 0x24, 0x5C, 0x24, 0x17, 0x6D, 0x56, 0x31};

// 明文：/proc/self/maps
static const unsigned char STR_PROC_MAPS[] = {0x5C, 0x32, 0x01, 0x2D, 0x10, 0x6D, 0x00, 0x27, 0x1F, 0x24, 0x5C, 0x2F, 0x12, 0x32, 0x00};

// 明文：de/robv/android/xposed/XposedBridge
static const unsigned char STR_XPOSED_BRIDGE[] = {0x17, 0x27, 0x5C, 0x30, 0x1C, 0x20, 0x05, 0x6D, 0x12, 0x2C, 0x17, 0x30, 0x1C, 0x2B, 0x17, 0x6D, 0x0B, 0x32, 0x1C, 0x31, 0x16, 0x26, 0x5C, 0x1A, 0x03, 0x2D, 0x00, 0x27, 0x17, 0x00, 0x01, 0x2B, 0x17, 0x25, 0x16};

// 明文：java/lang/Throwable
static const unsigned char STR_THROWABLE[] = {0x19, 0x23, 0x05, 0x23, 0x5C, 0x2E, 0x12, 0x2C, 0x14, 0x6D, 0x27, 0x2A, 0x01, 0x2D, 0x04, 0x23, 0x11, 0x2E, 0x16};

// 明文：<init>
static const unsigned char STR_INIT[] = {0x4F, 0x2B, 0x1D, 0x2B, 0x07, 0x7C};

// 明文：()V
static const unsigned char STR_SIG_VOID[] = {0x5B, 0x6B, 0x25};

// 明文：getStackTrace
static const unsigned char STR_GET_STACK_TRACE[] = {0x14, 0x27, 0x07, 0x11, 0x07, 0x23, 0x10, 0x29, 0x27, 0x30, 0x12, 0x21, 0x16};

// 明文：()[Ljava/lang/StackTraceElement;
static const unsigned char STR_SIG_STACK_TRACE[] = {0x5B, 0x6B, 0x28, 0x0E, 0x19, 0x23, 0x05, 0x23, 0x5C, 0x2E, 0x12, 0x2C, 0x14, 0x6D, 0x20, 0x36, 0x12, 0x21, 0x18, 0x16, 0x01, 0x23, 0x10, 0x27, 0x36, 0x2E, 0x16, 0x2F, 0x16, 0x2C, 0x07, 0x79};

// 明文：java/lang/StackTraceElement
static const unsigned char STR_STACK_TRACE_ELEMENT[] = {0x19, 0x23, 0x05, 0x23, 0x5C, 0x2E, 0x12, 0x2C, 0x14, 0x6D, 0x20, 0x36, 0x12, 0x21, 0x18, 0x16, 0x01, 0x23, 0x10, 0x27, 0x36, 0x2E, 0x16, 0x2F, 0x16, 0x2C, 0x07};

// 明文：toString
static const unsigned char STR_TO_STRING[] = {0x07, 0x2D, 0x20, 0x36, 0x01, 0x2B, 0x1D, 0x25};

// 明文：()Ljava/lang/String;
static const unsigned char STR_SIG_STRING[] = {0x5B, 0x6B, 0x3F, 0x28, 0x12, 0x34, 0x12, 0x6D, 0x1F, 0x23, 0x1D, 0x25, 0x5C, 0x11, 0x07, 0x30, 0x1A, 0x2C, 0x14, 0x79};

// 明文：/data/app/
static const unsigned char STR_DATA_APP[] = {0x5C, 0x26, 0x12, 0x36, 0x12, 0x6D, 0x12, 0x32, 0x03, 0x6D};

// 明文：/base.apk
static const unsigned char STR_BASE_APK[] = {0x5C, 0x20, 0x12, 0x31, 0x16, 0x6C, 0x12, 0x32, 0x18};

// 明文：getPackageName
static const unsigned char STR_GET_PACKAGE_NAME[] = {0x14, 0x27, 0x07, 0x12, 0x12, 0x21, 0x18, 0x23, 0x14, 0x27, 0x3D, 0x23, 0x1E, 0x27};
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

// ── 反调试检测 ─────────────────────────────────────────────────────

// 明文：frida
static const unsigned char STR_FRIDA_0[] = {0x15, 0x30, 0x1A, 0x26, 0x12};
// 明文：gum-js
static const unsigned char STR_FRIDA_1[] = {0x14, 0x37, 0x1E, 0x6F, 0x19, 0x31};
// 明文：linjector
static const unsigned char STR_FRIDA_2[] = {0x1F, 0x2B, 0x1D, 0x28, 0x16, 0x21, 0x07, 0x2D, 0x01};
// 明文：frida-agent
static const unsigned char STR_FRIDA_3[] = {0x15, 0x30, 0x1A, 0x26, 0x12, 0x6F, 0x12, 0x25, 0x16, 0x2C, 0x07};
// 明文：fridaserver
static const unsigned char STR_FRIDA_4[] = {0x15, 0x30, 0x1A, 0x26, 0x12, 0x31, 0x16, 0x30, 0x05, 0x27, 0x01};
// 明文：/proc/self/status
static const unsigned char STR_PROC_STATUS[] = {0x5C, 0x32, 0x01, 0x2D, 0x10, 0x6D, 0x00, 0x27, 0x1F, 0x24, 0x5C, 0x31, 0x07, 0x23, 0x07, 0x37, 0x00};
// 明文：TracerPid
static const unsigned char STR_TRACER_PID[] = {0x27, 0x30, 0x12, 0x21, 0x16, 0x30, 0x23, 0x2B, 0x17};

static const XorStr FRIDA_FEATURES[] = {
    {STR_FRIDA_0, sizeof(STR_FRIDA_0)},
    {STR_FRIDA_1, sizeof(STR_FRIDA_1)},
    {STR_FRIDA_2, sizeof(STR_FRIDA_2)},
    {STR_FRIDA_3, sizeof(STR_FRIDA_3)},
    {STR_FRIDA_4, sizeof(STR_FRIDA_4)},
};

// 检查 TracerPid (反调试)
static bool checkTracerPid() {
    int fd = safeOpenReadOnly(DEC(STR_PROC_STATUS).c_str());
    if (fd < 0) {
        return false;
    }

    char buffer[1024];
    ssize_t n = syscall(__NR_read, fd, buffer, sizeof(buffer) - 1);
    syscall(__NR_close, fd);

    if (n <= 0) {
        return false;
    }

    buffer[n] = '\0';
    std::string content(buffer, n);
    std::string tracerPidKey = DEC(STR_TRACER_PID);

    size_t pos = content.find(tracerPidKey);
    if (pos == std::string::npos) {
        return false;
    }

    // Skip "TracerPid:" and whitespace
    pos += tracerPidKey.length();
    while (pos < content.length() && (content[pos] == ':' || content[pos] == '\t' || content[pos] == ' ')) {
        pos++;
    }

    // Read the digit value
    std::string pidStr;
    while (pos < content.length() && content[pos] >= '0' && content[pos] <= '9') {
        pidStr += content[pos];
        pos++;
    }

    if (pidStr.empty()) {
        return false;
    }

    int tracerPid = atoi(pidStr.c_str());
    return tracerPid != 0;
}

// 检查 maps 中是否存在 Frida 特征
static bool checkMapsForFrida() {
    int fd = safeOpenReadOnly(DEC(STR_PROC_MAPS).c_str());
    if (fd < 0) {
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

            for (size_t i = 0; i < sizeof(FRIDA_FEATURES) / sizeof(FRIDA_FEATURES[0]); i++) {
                std::string feature = decStr(FRIDA_FEATURES[i].data, FRIDA_FEATURES[i].len);
                if (containsIgnoreCase(line, feature.c_str())) {
                    syscall(__NR_close, fd);
                    return true;
                }
            }
        }
    }

    // Check remaining data
    if (!cache.empty()) {
        for (size_t i = 0; i < sizeof(FRIDA_FEATURES) / sizeof(FRIDA_FEATURES[0]); i++) {
            std::string feature = decStr(FRIDA_FEATURES[i].data, FRIDA_FEATURES[i].len);
            if (containsIgnoreCase(cache, feature.c_str())) {
                syscall(__NR_close, fd);
                return true;
            }
        }
    }

    syscall(__NR_close, fd);
    return false;
}

// 检查 fd 中的 frida 特征
static bool checkFdForFrida() {
    std::string procFd = DEC(STR_PROC_FD);

    DIR *dir = opendir(procFd.c_str());
    if (dir == nullptr) {
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

        for (size_t i = 0; i < sizeof(FRIDA_FEATURES) / sizeof(FRIDA_FEATURES[0]); i++) {
            std::string feature = decStr(FRIDA_FEATURES[i].data, FRIDA_FEATURES[i].len);
            if (containsIgnoreCase(s, feature.c_str())) {
                closedir(dir);
                return true;
            }
        }
    }

    closedir(dir);
    return false;
}

// 综合反调试检测
static bool ArkEnvGuard_DetectDebug() {
    if (checkTracerPid()) {
        return true;
    }

    if (checkMapsForFrida()) {
        return true;
    }

    if (checkFdForFrida()) {
        return true;
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

    if (ArkEnvGuard_DetectDebug()) {
        //LOGE("检测到调试器/Frida，停止加载DEX");
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