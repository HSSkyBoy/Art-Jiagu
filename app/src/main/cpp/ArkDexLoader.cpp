#include "ArkDexLoader.h"

#include <jni.h>
#include <android/log.h>
#include <vector>
#include <string>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#include "ArkSelfHash.h"

#define LOG_TAG "ArkDexLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
static const unsigned char ARK_BLOCK_USE_SIGN_KEY_FLAG[4] = {
        0x41, 0x52, 0x4B, 0x53 // "ARKS"
};

static std::string gRealApplicationName;

static std::vector<void *> gDexMemoryList;

static bool checkException(JNIEnv *env, const char *msg) {
    if (env->ExceptionCheck()) {
        //LOGE("%s 出现异常", msg);
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return false;
}

static jstring newString(JNIEnv *env, const char *text) {
    return env->NewStringUTF(text);
}

static int getSdkInt(JNIEnv *env) {
    jclass clsBuildVersion = env->FindClass("android/os/Build$VERSION");
    jfieldID fidSdkInt = env->GetStaticFieldID(clsBuildVersion, "SDK_INT", "I");
    return env->GetStaticIntField(clsBuildVersion, fidSdkInt);
    //return 25;
}

static uint32_t readLe32(const unsigned char *p) {
    return ((uint32_t) p[0])
           | ((uint32_t) p[1] << 8)
           | ((uint32_t) p[2] << 16)
           | ((uint32_t) p[3] << 24);
}
// 【新增】只记录加密 dex 块信息，不保存明文 dex
struct ArkDexBlockInfo {
    size_t dataOffset;
    size_t plainLen;
    unsigned char key[64];
};
static std::vector<unsigned char> xorData(
        const unsigned char *data,
        size_t len,
        const unsigned char *key,
        size_t keyLen) {

    std::vector<unsigned char> out;
    out.resize(len);

    for (size_t i = 0; i < len; i++) {
        out[i] = data[i] ^ key[i % keyLen];
    }

    return out;
}

static bool getSelfSignKey64(JNIEnv *env, unsigned char outKey[64]) {
    if (env == nullptr || outKey == nullptr) {
        return false;
    }

    jbyteArray sha32Array = env->NewByteArray(32);
    if (sha32Array == nullptr) {
        return false;
    }

    if (!ark_get_self_cert_sha256(env, sha32Array)) {
        env->DeleteLocalRef(sha32Array);
        return false;
    }

    unsigned char sha32[32];
    memset(sha32, 0, sizeof(sha32));

    env->GetByteArrayRegion(
            sha32Array,
            0,
            32,
            reinterpret_cast<jbyte *>(sha32)
    );

    env->DeleteLocalRef(sha32Array);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }

    static const char hexTable[] = "0123456789abcdef";

    for (int i = 0; i < 32; i++) {
        outKey[i * 2] = hexTable[(sha32[i] >> 4) & 0x0F];
        outKey[i * 2 + 1] = hexTable[sha32[i] & 0x0F];
    }

    return true;
}

static bool isValidDexData(const std::vector<unsigned char> &data) {
    return data.size() >= 0x70
           && data[0] == 'd'
           && data[1] == 'e'
           && data[2] == 'x'
           && data[3] == '\n';
}
// 【新增】校验裸 dex 内存，避免为了校验再创建 vector
static bool isValidDexRaw(const unsigned char *data, size_t len) {
    return data != nullptr
           && len >= 0x70
           && data[0] == 'd'
           && data[1] == 'e'
           && data[2] == 'x'
           && data[3] == '\n';
}
static std::vector<unsigned char> readAllBytesFromInputStream(JNIEnv *env, jobject inputStream) {
    std::vector<unsigned char> result;

    jclass clsInputStream = env->FindClass("java/io/InputStream");
    jmethodID midRead = env->GetMethodID(clsInputStream, "read", "([B)I");
    jmethodID midClose = env->GetMethodID(clsInputStream, "close", "()V");

    const int bufferSize = 8192;
    jbyteArray buffer = env->NewByteArray(bufferSize);

    while (true) {
        jint readSize = env->CallIntMethod(inputStream, midRead, buffer);
        if (checkException(env, "读取 InputStream")) {
            result.clear();
            return result;
        }

        if (readSize <= 0) {
            break;
        }

        size_t oldSize = result.size();
        result.resize(oldSize + readSize);

        env->GetByteArrayRegion(
                buffer,
                0,
                readSize,
                reinterpret_cast<jbyte *>(result.data() + oldSize)
        );

        if (checkException(env, "复制 InputStream 数据")) {
            result.clear();
            return result;
        }
    }

    env->CallVoidMethod(inputStream, midClose);
    checkException(env, "关闭 InputStream");

    return result;
}

static std::vector<unsigned char> readSelfClassesDex(JNIEnv *env, jobject context) {
    std::vector<unsigned char> result;

    jclass clsContext = env->GetObjectClass(context);
    jmethodID midGetApplicationInfo = env->GetMethodID(
            clsContext,
            "getApplicationInfo",
            "()Landroid/content/pm/ApplicationInfo;"
    );

    jobject appInfo = env->CallObjectMethod(context, midGetApplicationInfo);
    if (checkException(env, "获取 ApplicationInfo") || appInfo == nullptr) {
        return result;
    }

    jclass clsApplicationInfo = env->FindClass("android/content/pm/ApplicationInfo");
    jfieldID fidSourceDir = env->GetFieldID(
            clsApplicationInfo,
            "sourceDir",
            "Ljava/lang/String;"
    );

    jstring sourceDirJ = (jstring) env->GetObjectField(appInfo, fidSourceDir);
    if (sourceDirJ == nullptr) {
        //LOGE("sourceDir 为空");
        return result;
    }

    const char *sourceDir = env->GetStringUTFChars(sourceDirJ, nullptr);
    //LOGI("当前 APK 路径：%s", sourceDir);

    jclass clsZipFile = env->FindClass("java/util/zip/ZipFile");
    jmethodID midZipInit = env->GetMethodID(
            clsZipFile,
            "<init>",
            "(Ljava/lang/String;)V"
    );

    jobject zipFile = env->NewObject(clsZipFile, midZipInit, sourceDirJ);
    env->ReleaseStringUTFChars(sourceDirJ, sourceDir);

    if (checkException(env, "创建 ZipFile") || zipFile == nullptr) {
        return result;
    }

    jmethodID midGetEntry = env->GetMethodID(
            clsZipFile,
            "getEntry",
            "(Ljava/lang/String;)Ljava/util/zip/ZipEntry;"
    );

    jstring entryName = env->NewStringUTF("classes.dex");
    jobject zipEntry = env->CallObjectMethod(zipFile, midGetEntry, entryName);

    if (checkException(env, "获取 classes.dex ZipEntry") || zipEntry == nullptr) {
        //LOGE("APK 中找不到 classes.dex");
        return result;
    }

    jmethodID midGetInputStream = env->GetMethodID(
            clsZipFile,
            "getInputStream",
            "(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;"
    );

    jobject inputStream = env->CallObjectMethod(zipFile, midGetInputStream, zipEntry);
    if (checkException(env, "获取 classes.dex InputStream") || inputStream == nullptr) {
        return result;
    }

    result = readAllBytesFromInputStream(env, inputStream);

    jmethodID midClose = env->GetMethodID(clsZipFile, "close", "()V");
    env->CallVoidMethod(zipFile, midClose);
    checkException(env, "关闭 ZipFile");

    //LOGI("读取自身 classes.dex 完成，大小=%zu", result.size());
    return result;
}

static bool parseOneEncryptedBlockBackward(
        JNIEnv *env,
        const std::vector<unsigned char> &allData,
        size_t &cursor,
        std::vector<unsigned char> &outPlain,
        const unsigned char *signKey64) {

    if (cursor < 8) {
        return false;
    }

    bool useSignKey = false;

    if (cursor >= 4) {
        const unsigned char *tail = allData.data() + cursor - 4;

        if (memcmp(tail, ARK_BLOCK_USE_SIGN_KEY_FLAG, 4) == 0) {
            useSignKey = true;
        }
    }

    const unsigned char *key = nullptr;
    size_t lenOffset = 0;

    if (useSignKey) {
        if (signKey64 == nullptr) {
            return false;
        }

        key = signKey64;

        if (cursor < 8) {
            return false;
        }

        // 新格式：
        // [加密数据][加密长度4字节][ARKS特征4字节]
        lenOffset = cursor - 4 - 4;
    } else {
        if (cursor < 68) {
            return false;
        }

        // 老格式：
        // [加密数据][加密长度4字节][随机密钥64字节]
        size_t keyOffset = cursor - 64;
        key = allData.data() + keyOffset;
        lenOffset = keyOffset - 4;
    }

    std::vector<unsigned char> lenPlain = xorData(
            allData.data() + lenOffset,
            4,
            key,
            64
    );

    uint32_t plainLen = readLe32(lenPlain.data());

    if (plainLen <= 0 || lenOffset < plainLen) {
        return false;
    }

    size_t dataOffset = lenOffset - plainLen;

    outPlain = xorData(
            allData.data() + dataOffset,
            plainLen,
            key,
            64
    );

    cursor = dataOffset;
    return true;
}
// 【新增】只反向解析 dex 块信息，不立即解密明文 dex
static bool parseOneEncryptedBlockInfoBackward(
        const std::vector<unsigned char> &allData,
        size_t &cursor,
        ArkDexBlockInfo &outInfo,
        const unsigned char *signKey64) {

    if (cursor < 8) {
        return false;
    }

    bool useSignKey = false;

    if (cursor >= 4) {
        const unsigned char *tail = allData.data() + cursor - 4;
        if (memcmp(tail, ARK_BLOCK_USE_SIGN_KEY_FLAG, 4) == 0) {
            useSignKey = true;
        }
    }

    const unsigned char *key = nullptr;
    size_t lenOffset = 0;

    if (useSignKey) {
        if (signKey64 == nullptr) {
            return false;
        }

        key = signKey64;
        lenOffset = cursor - 8;
    } else {
        if (cursor < 68) {
            return false;
        }

        size_t keyOffset = cursor - 64;
        key = allData.data() + keyOffset;
        lenOffset = keyOffset - 4;
    }

    std::vector<unsigned char> lenPlain = xorData(
            allData.data() + lenOffset,
            4,
            key,
            64
    );

    uint32_t plainLen = readLe32(lenPlain.data());

    if (plainLen <= 0 || lenOffset < plainLen) {
        return false;
    }

    size_t dataOffset = lenOffset - plainLen;

    outInfo.dataOffset = dataOffset;
    outInfo.plainLen = plainLen;
    memcpy(outInfo.key, key, 64);

    cursor = dataOffset;
    return true;
}


static jobjectArray loadDexBuffersFromSelfClassesDex(JNIEnv *env, jobject context) {
    std::vector<unsigned char> shellDex = readSelfClassesDex(env, context);
    if (shellDex.size() < 4) {
        return nullptr;
    }

    size_t cursor = shellDex.size();

    uint32_t dexCount = readLe32(shellDex.data() + cursor - 4);
    cursor -= 4;

    if (dexCount <= 0 || dexCount > 128) {
        std::vector<unsigned char>().swap(shellDex);
        return nullptr;
    }

    // 真实 Application 名称仍然直接解析，因为它很小
    std::vector<unsigned char> appNameData;
    if (!parseOneEncryptedBlockBackward(env, shellDex, cursor, appNameData, nullptr)) {
        std::vector<unsigned char>().swap(shellDex);
        return nullptr;
    }

    appNameData.push_back('\0');
    gRealApplicationName = reinterpret_cast<const char *>(appNameData.data());

    std::vector<unsigned char>().swap(appNameData);

    if (gRealApplicationName.empty()) {
        std::vector<unsigned char>().swap(shellDex);
        return nullptr;
    }

    unsigned char signKey64[64];
    memset(signKey64, 0, sizeof(signKey64));

    if (!getSelfSignKey64(env, signKey64)) {
        std::vector<unsigned char>().swap(shellDex);
        return nullptr;
    }

    // 【修改点 1】
    // 原来这里是 std::vector<std::vector<unsigned char>> dexListReverse;
    // 会保存所有解密后的 dex 明文。
    // 现在只保存 offset / len / key，不保存明文 dex。
    std::vector<ArkDexBlockInfo> dexBlockListReverse;
    dexBlockListReverse.reserve(dexCount);

    for (uint32_t i = 0; i < dexCount; i++) {
        ArkDexBlockInfo info;
        memset(&info, 0, sizeof(info));

        if (!parseOneEncryptedBlockInfoBackward(shellDex, cursor, info, signKey64)) {
            std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
            std::vector<unsigned char>().swap(shellDex);
            return nullptr;
        }

        dexBlockListReverse.push_back(info);
    }

    jclass clsByteBuffer = env->FindClass("java/nio/ByteBuffer");
    if (clsByteBuffer == nullptr) {
        std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
        std::vector<unsigned char>().swap(shellDex);
        return nullptr;
    }

    jmethodID midWrap = env->GetStaticMethodID(
            clsByteBuffer,
            "wrap",
            "([B)Ljava/nio/ByteBuffer;"
    );

    jobjectArray bufferArray = env->NewObjectArray(dexCount, clsByteBuffer, nullptr);
    if (bufferArray == nullptr) {
        std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
        std::vector<unsigned char>().swap(shellDex);
        return nullptr;
    }

    int sdk = getSdkInt(env);

    for (uint32_t i = 0; i < dexCount; i++) {
        ArkDexBlockInfo &info = dexBlockListReverse[dexCount - 1 - i];

        jobject byteBuffer = nullptr;

        if (sdk >= 26) {
            // 【修改点 2】
            // Android 8+ 直接 malloc 目标内存，并边 xor 边写入。
            // 不再创建临时 dexData vector，减少一份明文 dex 内存。
            static size_t totalDexMemory = 0;

            totalDexMemory += info.plainLen;

            //LOGI("开始解密 dex[%u/%u] 大小=%zu KB 累计=%zu MB",i + 1,dexCount,info.plainLen / 1024,totalDexMemory / 1024 / 1024);
            void *dexMemory = malloc(info.plainLen);
            if (dexMemory == nullptr) {
                std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
                std::vector<unsigned char>().swap(shellDex);
                return nullptr;
            }

            unsigned char *out = reinterpret_cast<unsigned char *>(dexMemory);
            const unsigned char *enc = shellDex.data() + info.dataOffset;

            for (size_t k = 0; k < info.plainLen; k++) {
                out[k] = enc[k] ^ info.key[k % 64];
            }

            if (!isValidDexRaw(out, info.plainLen)) {
                free(dexMemory);
                std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
                std::vector<unsigned char>().swap(shellDex);
                return nullptr;
            }

            gDexMemoryList.push_back(dexMemory);

            byteBuffer = env->NewDirectByteBuffer(
                    dexMemory,
                    static_cast<jlong>(info.plainLen)
            );

            if (byteBuffer == nullptr) {
                free(dexMemory);
                gDexMemoryList.pop_back();
                std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
                std::vector<unsigned char>().swap(shellDex);
                return nullptr;
            }
        } else {
            // Android 8 以下仍然走 HeapByteBuffer。
            // 这里每次只解密一个 dex，用完临时 vector 立即释放。
            std::vector<unsigned char> dexData = xorData(
                    shellDex.data() + info.dataOffset,
                    info.plainLen,
                    info.key,
                    64
            );

            if (!isValidDexData(dexData)) {
                std::vector<unsigned char>().swap(dexData);
                std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
                std::vector<unsigned char>().swap(shellDex);
                return nullptr;
            }

            jbyteArray dexArray = env->NewByteArray(static_cast<jsize>(dexData.size()));
            if (dexArray == nullptr) {
                std::vector<unsigned char>().swap(dexData);
                std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
                std::vector<unsigned char>().swap(shellDex);
                return nullptr;
            }

            env->SetByteArrayRegion(
                    dexArray,
                    0,
                    static_cast<jsize>(dexData.size()),
                    reinterpret_cast<const jbyte *>(dexData.data())
            );

            if (checkException(env, "写入 dex byte[]")) {
                env->DeleteLocalRef(dexArray);
                std::vector<unsigned char>().swap(dexData);
                std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
                std::vector<unsigned char>().swap(shellDex);
                return nullptr;
            }

            byteBuffer = env->CallStaticObjectMethod(
                    clsByteBuffer,
                    midWrap,
                    dexArray
            );

            if (checkException(env, "ByteBuffer.wrap") || byteBuffer == nullptr) {
                env->DeleteLocalRef(dexArray);
                std::vector<unsigned char>().swap(dexData);
                std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
                std::vector<unsigned char>().swap(shellDex);
                return nullptr;
            }

            // ByteBuffer.wrap 后，ByteBuffer 已经持有 dexArray。
            // 当前 JNI 局部引用可以删除。
            env->DeleteLocalRef(dexArray);

            // 立即释放当前 dex 临时明文 vector
            std::vector<unsigned char>().swap(dexData);
        }

        env->SetObjectArrayElement(bufferArray, i, byteBuffer);
        if (checkException(env, "设置 ByteBuffer 数组元素")) {
            if (byteBuffer != nullptr) {
                env->DeleteLocalRef(byteBuffer);
            }
            std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
            std::vector<unsigned char>().swap(shellDex);
            return nullptr;
        }

        if (byteBuffer != nullptr) {
            env->DeleteLocalRef(byteBuffer);
        }
    }

    // 【修改点 3】
    // 所有 ByteBuffer 创建完成后，壳 dex 和块信息已经没用了，立即释放。
    std::vector<ArkDexBlockInfo>().swap(dexBlockListReverse);
    std::vector<unsigned char>().swap(shellDex);

    return bufferArray;
}

/**
 * 获取当前包名
 */
static jstring getPackageName(JNIEnv *env, jobject context) {
    jclass clsContext = env->GetObjectClass(context);
    jmethodID midGetPackageName = env->GetMethodID(
            clsContext,
            "getPackageName",
            "()Ljava/lang/String;"
    );

    return (jstring) env->CallObjectMethod(context, midGetPackageName);
}


static bool writeFileBytes(const char *path, const unsigned char *data, int len) {
    FILE *fp = fopen(path, "wb");
    if (fp == nullptr) {
        //LOGE("打开 dex 输出文件失败：%s", path);
        return false;
    }

    int written = fwrite(data, 1, len, fp);
    fclose(fp);

    if (written != len) {
        //LOGE("写入 dex 文件不完整：%s，期望=%d，实际=%d", path, len, written);
        return false;
    }

    chmod(path, 0600);
    //LOGI("写入 dex 文件成功：%s，大小=%d", path, len);
    return true;
}

static jstring getAbsolutePath(JNIEnv *env, jobject fileObj) {
    jclass clsFile = env->FindClass("java/io/File");
    jmethodID midGetAbsolutePath = env->GetMethodID(
            clsFile,
            "getAbsolutePath",
            "()Ljava/lang/String;"
    );
    return (jstring) env->CallObjectMethod(fileObj, midGetAbsolutePath);
}

static jobject getCodeCacheDir(JNIEnv *env, jobject context) {
    jclass clsContext = env->GetObjectClass(context);
    jmethodID midGetCodeCacheDir = env->GetMethodID(
            clsContext,
            "getCodeCacheDir",
            "()Ljava/io/File;"
    );

    if (midGetCodeCacheDir != nullptr) {
        jobject dir = env->CallObjectMethod(context, midGetCodeCacheDir);
        if (!checkException(env, "调用 getCodeCacheDir") && dir != nullptr) {
            return dir;
        }
    }

    jmethodID midGetDir = env->GetMethodID(
            clsContext,
            "getDir",
            "(Ljava/lang/String;I)Ljava/io/File;"
    );

    jstring name = env->NewStringUTF("ark_code_cache");
    return env->CallObjectMethod(context, midGetDir, name, 0);
}

static bool ensureDir(JNIEnv *env, jobject fileObj) {
    jclass clsFile = env->FindClass("java/io/File");

    jmethodID midExists = env->GetMethodID(clsFile, "exists", "()Z");
    jmethodID midMkdirs = env->GetMethodID(clsFile, "mkdirs", "()Z");

    jboolean exists = env->CallBooleanMethod(fileObj, midExists);
    if (exists) {
        return true;
    }

    jboolean ok = env->CallBooleanMethod(fileObj, midMkdirs);
    return ok == JNI_TRUE;
}

static jobject newFile(JNIEnv *env, jobject parent, const char *child) {
    jclass clsFile = env->FindClass("java/io/File");
    jmethodID midInit = env->GetMethodID(
            clsFile,
            "<init>",
            "(Ljava/io/File;Ljava/lang/String;)V"
    );

    jstring childName = env->NewStringUTF(child);
    return env->NewObject(clsFile, midInit, parent, childName);
}

static jstring buildDexPathFromBuffers(JNIEnv *env, jobject context, jobjectArray dexBuffers) {
    jobject codeCacheDir = getCodeCacheDir(env, context);
    if (codeCacheDir == nullptr) {
        //LOGE("获取 code_cache 目录失败");
        return nullptr;
    }

    jobject dexDir = newFile(env, codeCacheDir, "ark_dex");
    jobject optDir = newFile(env, codeCacheDir, "ark_opt");

    if (!ensureDir(env, dexDir) || !ensureDir(env, optDir)) {
        //LOGE("创建 dex/opt 私有目录失败");
        return nullptr;
    }

    int dexCount = env->GetArrayLength(dexBuffers);
    std::string dexPath;

    jclass clsByteBuffer = env->FindClass("java/nio/ByteBuffer");
    jmethodID midRemaining = env->GetMethodID(clsByteBuffer, "remaining", "()I");
    jmethodID midGet = env->GetMethodID(clsByteBuffer, "get", "([B)Ljava/nio/ByteBuffer;");
    jmethodID midPosition = env->GetMethodID(clsByteBuffer, "position", "()I");
    jmethodID midPositionSet = env->GetMethodID(clsByteBuffer, "position", "(I)Ljava/nio/Buffer;");

    for (int i = 0; i < dexCount; i++) {
        jobject buffer = env->GetObjectArrayElement(dexBuffers, i);
        if (buffer == nullptr) {
            //LOGE("dexBuffers[%d] 为空", i);
            return nullptr;
        }

        jint oldPos = env->CallIntMethod(buffer, midPosition);
        jint len = env->CallIntMethod(buffer, midRemaining);

        if (len <= 0) {
            //LOGE("dexBuffers[%d] 数据长度异常：%d", i, len);
            return nullptr;
        }

        jbyteArray byteArray = env->NewByteArray(len);
        if (byteArray == nullptr) {
            //LOGE("创建 dex byte[] 失败：%d", i);
            return nullptr;
        }

        env->CallObjectMethod(buffer, midGet, byteArray);
        if (checkException(env, "读取 ByteBuffer 数据")) {
            return nullptr;
        }

        env->CallObjectMethod(buffer, midPositionSet, oldPos);

        std::vector<unsigned char> dexData(len);
        env->GetByteArrayRegion(
                byteArray,
                0,
                len,
                reinterpret_cast<jbyte *>(dexData.data())
        );

        if (checkException(env, "复制 dex byte[]")) {
            return nullptr;
        }

        char dexName[64];
        snprintf(dexName, sizeof(dexName), "ark_payload_%d.dex", i);

        jobject dexFile = newFile(env, dexDir, dexName);
        jstring dexFilePathJ = getAbsolutePath(env, dexFile);
        const char *dexFilePath = env->GetStringUTFChars(dexFilePathJ, nullptr);

        bool writeOk = writeFileBytes(dexFilePath, dexData.data(), len);

        if (dexPath.empty()) {
            dexPath = dexFilePath;
        } else {
            dexPath += ":";
            dexPath += dexFilePath;
        }

        env->ReleaseStringUTFChars(dexFilePathJ, dexFilePath);

        if (!writeOk) {
            return nullptr;
        }

        //LOGI("添加 dexPath：%s", dexPath.c_str());
    }

    return env->NewStringUTF(dexPath.c_str());
}

static jobject createFileDexClassLoader(JNIEnv *env, jobject context, jobjectArray dexBuffers, jobject parentClassLoader) {
    jstring dexPath = buildDexPathFromBuffers(env, context, dexBuffers);
    if (dexPath == nullptr) {
        return nullptr;
    }

    jobject codeCacheDir = getCodeCacheDir(env, context);
    jobject optDir = newFile(env, codeCacheDir, "ark_opt");

    if (!ensureDir(env, optDir)) {
        return nullptr;
    }

    jstring optPath = getAbsolutePath(env, optDir);

    jclass clsContext = env->GetObjectClass(context);

    jmethodID midGetApplicationInfo = env->GetMethodID(
            clsContext,
            "getApplicationInfo",
            "()Landroid/content/pm/ApplicationInfo;"
    );

    jobject appInfo = env->CallObjectMethod(context, midGetApplicationInfo);
    if (checkException(env, "获取 ApplicationInfo") || appInfo == nullptr) {
        return nullptr;
    }

    jclass clsApplicationInfo = env->FindClass("android/content/pm/ApplicationInfo");
    jfieldID fidNativeLibraryDir = env->GetFieldID(
            clsApplicationInfo,
            "nativeLibraryDir",
            "Ljava/lang/String;"
    );

    jstring nativeLibDir = (jstring) env->GetObjectField(appInfo, fidNativeLibraryDir);
    if (nativeLibDir == nullptr) {
        return nullptr;
    }

    jclass clsDexClassLoader = env->FindClass("dalvik/system/DexClassLoader");
    if (clsDexClassLoader == nullptr) {
        return nullptr;
    }

    jmethodID midInit = env->GetMethodID(
            clsDexClassLoader,
            "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V"
    );

    if (midInit == nullptr) {
        return nullptr;
    }

    jobject loader = env->NewObject(
            clsDexClassLoader,
            midInit,
            dexPath,
            optPath,
            nativeLibDir,
            parentClassLoader
    );

    if (checkException(env, "创建 DexClassLoader")) {
        return nullptr;
    }

    const char *dexPathChars = env->GetStringUTFChars(dexPath, nullptr);
    if (dexPathChars != nullptr) {
        std::string allPath = dexPathChars;
        env->ReleaseStringUTFChars(dexPath, dexPathChars);

        size_t start = 0;
        while (start < allPath.length()) {
            size_t pos = allPath.find(':', start);
            std::string onePath = allPath.substr(
                    start,
                    pos == std::string::npos ? std::string::npos : pos - start
            );

            if (!onePath.empty()) {
                unlink(onePath.c_str());
            }

            if (pos == std::string::npos) {
                break;
            }

            start = pos + 1;
        }
    }

    return loader;
}



static jobject createDexClassLoader(JNIEnv *env, jobject context, jobjectArray dexBuffers) {
    jclass clsContext = env->GetObjectClass(context);

    jmethodID midGetClassLoader = env->GetMethodID(
            clsContext,
            "getClassLoader",
            "()Ljava/lang/ClassLoader;"
    );

    jobject parentClassLoader = env->CallObjectMethod(context, midGetClassLoader);
    if (checkException(env, "获取父 ClassLoader")) {
        return nullptr;
    }

    jmethodID midGetApplicationInfo = env->GetMethodID(
            clsContext,
            "getApplicationInfo",
            "()Landroid/content/pm/ApplicationInfo;"
    );

    jobject appInfo = env->CallObjectMethod(context, midGetApplicationInfo);
    if (checkException(env, "获取 ApplicationInfo") || appInfo == nullptr) {
        return nullptr;
    }

    jclass clsApplicationInfo = env->FindClass("android/content/pm/ApplicationInfo");
    jfieldID fidNativeLibraryDir = env->GetFieldID(
            clsApplicationInfo,
            "nativeLibraryDir",
            "Ljava/lang/String;"
    );

    jstring nativeLibDir = (jstring) env->GetObjectField(appInfo, fidNativeLibraryDir);
    if (nativeLibDir == nullptr) {
        return nullptr;
    }

    int sdk = getSdkInt(env);

    if (sdk >= 26) {
        jclass clsLoader = env->FindClass("dalvik/system/InMemoryDexClassLoader");
        if (clsLoader == nullptr) {
            return nullptr;
        }

        jmethodID initMethod = env->GetMethodID(
                clsLoader,
                "<init>",
                "([Ljava/nio/ByteBuffer;Ljava/lang/String;Ljava/lang/ClassLoader;)V"
        );

        if (initMethod == nullptr) {
            return nullptr;
        }

        jobject loader = env->NewObject(
                clsLoader,
                initMethod,
                dexBuffers,
                nativeLibDir,
                parentClassLoader
        );

        if (checkException(env, "创建系统 InMemoryDexClassLoader")) {
            return nullptr;
        }

        return loader;
    }

    return createFileDexClassLoader(env, context, dexBuffers, parentClassLoader);
}

/**
 * 替换 LoadedApk.mClassLoader
 */
static bool replaceLoadedApkClassLoader(JNIEnv *env, jobject context, jobject newClassLoader) {
    jclass clsActivityThread = env->FindClass("android/app/ActivityThread");
    jclass clsMap = env->FindClass("java/util/Map");
    jclass clsReference = env->FindClass("java/lang/ref/Reference");
    jclass clsLoadedApk = env->FindClass("android/app/LoadedApk");

    if (!clsActivityThread || !clsMap || !clsReference || !clsLoadedApk) {
        //LOGE("查找系统类失败");
        return false;
    }

    jmethodID midCurrentActivityThread = env->GetStaticMethodID(
            clsActivityThread,
            "currentActivityThread",
            "()Landroid/app/ActivityThread;"
    );

    jobject currentActivityThread = env->CallStaticObjectMethod(clsActivityThread, midCurrentActivityThread);
    if (checkException(env, "获取 currentActivityThread")) {
        return false;
    }

    if (currentActivityThread == nullptr) {
        //LOGE("currentActivityThread 为空");
        return false;
    }

    jfieldID fidPackages = env->GetFieldID(
            clsActivityThread,
            "mPackages",
            "Landroid/util/ArrayMap;"
    );

    jobject mPackages = env->GetObjectField(currentActivityThread, fidPackages);
    if (mPackages == nullptr) {
        //LOGE("mPackages 为空");
        return false;
    }

    jstring packageName = getPackageName(env, context);

    jmethodID midMapGet = env->GetMethodID(
            clsMap,
            "get",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
    );

    jobject weakRefLoadedApk = env->CallObjectMethod(mPackages, midMapGet, packageName);
    if (checkException(env, "从 mPackages 获取 LoadedApk 引用")) {
        return false;
    }

    if (weakRefLoadedApk == nullptr) {
        //LOGE("LoadedApk 弱引用为空");
        return false;
    }

    jmethodID midReferenceGet = env->GetMethodID(
            clsReference,
            "get",
            "()Ljava/lang/Object;"
    );

    jobject loadedApk = env->CallObjectMethod(weakRefLoadedApk, midReferenceGet);
    if (loadedApk == nullptr) {
        //LOGE("LoadedApk 为空");
        return false;
    }

    jfieldID fidClassLoader = env->GetFieldID(
            clsLoadedApk,
            "mClassLoader",
            "Ljava/lang/ClassLoader;"
    );

    env->SetObjectField(loadedApk, fidClassLoader, newClassLoader);

    //LOGI("替换 LoadedApk.mClassLoader 成功");
    return true;
}

/**
 * 启动真实 Application
 */
static bool startRealApplication(JNIEnv *env) {
    jclass clsActivityThread = env->FindClass("android/app/ActivityThread");
    jclass clsAppBindData = env->FindClass("android/app/ActivityThread$AppBindData");
    jclass clsApplicationInfo = env->FindClass("android/content/pm/ApplicationInfo");
    jclass clsLoadedApk = env->FindClass("android/app/LoadedApk");
    jclass clsApplication = env->FindClass("android/app/Application");
    jclass clsList = env->FindClass("java/util/List");

    if (!clsActivityThread || !clsAppBindData || !clsApplicationInfo || !clsLoadedApk || !clsApplication || !clsList) {
        //LOGE("启动真实 Application 时查找系统类失败");
        return false;
    }

    jmethodID midCurrentActivityThread = env->GetStaticMethodID(
            clsActivityThread,
            "currentActivityThread",
            "()Landroid/app/ActivityThread;"
    );

    jobject currentActivityThread = env->CallStaticObjectMethod(clsActivityThread, midCurrentActivityThread);
    if (currentActivityThread == nullptr) {
        //LOGE("currentActivityThread 为空");
        return false;
    }

    jfieldID fidBoundApplication = env->GetFieldID(
            clsActivityThread,
            "mBoundApplication",
            "Landroid/app/ActivityThread$AppBindData;"
    );

    jobject boundApplication = env->GetObjectField(currentActivityThread, fidBoundApplication);
    if (boundApplication == nullptr) {
        //LOGE("mBoundApplication 为空");
        return false;
    }

    jfieldID fidInfo = env->GetFieldID(
            clsAppBindData,
            "info",
            "Landroid/app/LoadedApk;"
    );

    jobject loadedApk = env->GetObjectField(boundApplication, fidInfo);
    if (loadedApk == nullptr) {
        //LOGE("LoadedApk 为空");
        return false;
    }

    jfieldID fidLoadedApkApplication = env->GetFieldID(
            clsLoadedApk,
            "mApplication",
            "Landroid/app/Application;"
    );

    env->SetObjectField(loadedApk, fidLoadedApkApplication, nullptr);

    jfieldID fidInitialApplication = env->GetFieldID(
            clsActivityThread,
            "mInitialApplication",
            "Landroid/app/Application;"
    );

    jobject oldApplication = env->GetObjectField(currentActivityThread, fidInitialApplication);

    jfieldID fidAllApplications = env->GetFieldID(
            clsActivityThread,
            "mAllApplications",
            "Ljava/util/ArrayList;"
    );

    jobject allApplications = env->GetObjectField(currentActivityThread, fidAllApplications);

    if (oldApplication != nullptr && allApplications != nullptr) {
        jmethodID midRemove = env->GetMethodID(
                clsList,
                "remove",
                "(Ljava/lang/Object;)Z"
        );

        env->CallBooleanMethod(allApplications, midRemove, oldApplication);
        checkException(env, "移除旧 Application");
    }

    if (gRealApplicationName.empty()) {
        //LOGE("真实 Application 类名尚未解析");
        return false;
    }

    jstring realAppName = newString(env, gRealApplicationName.c_str());

    jfieldID fidLoadedApkApplicationInfo = env->GetFieldID(
            clsLoadedApk,
            "mApplicationInfo",
            "Landroid/content/pm/ApplicationInfo;"
    );

    jobject loadedApkApplicationInfo = env->GetObjectField(loadedApk, fidLoadedApkApplicationInfo);

    jfieldID fidClassName = env->GetFieldID(
            clsApplicationInfo,
            "className",
            "Ljava/lang/String;"
    );

    env->SetObjectField(loadedApkApplicationInfo, fidClassName, realAppName);

    jfieldID fidAppInfo = env->GetFieldID(
            clsAppBindData,
            "appInfo",
            "Landroid/content/pm/ApplicationInfo;"
    );

    jobject appInfo = env->GetObjectField(boundApplication, fidAppInfo);
    env->SetObjectField(appInfo, fidClassName, realAppName);

    jmethodID midMakeApplication = env->GetMethodID(
            clsLoadedApk,
            "makeApplication",
            "(ZLandroid/app/Instrumentation;)Landroid/app/Application;"
    );

    jobject realApplication = env->CallObjectMethod(
            loadedApk,
            midMakeApplication,
            JNI_FALSE,
            nullptr
    );

    if (checkException(env, "makeApplication")) {
        return false;
    }

    if (realApplication == nullptr) {
        //LOGE("真实 Application 创建失败");
        return false;
    }

    env->SetObjectField(currentActivityThread, fidInitialApplication, realApplication);

    jmethodID midOnCreate = env->GetMethodID(
            clsApplication,
            "onCreate",
            "()V"
    );

    env->CallVoidMethod(realApplication, midOnCreate);
    if (checkException(env, "调用真实 Application.onCreate")) {
        return false;
    }

    //LOGI("真实 Application 启动完成：%s", gRealApplicationName.c_str());
    return true;
}

/**
 * Native 层 Dex 加载入口
 */
bool LoaderDEX(JNIEnv *env, jobject context) {
    //LOGI("开始执行 LoaderDEX");
    //LOGI("LoaderDEX开始 PID=%d",getpid());
    jobjectArray dexBuffers = loadDexBuffersFromSelfClassesDex(env, context);
    if (dexBuffers == nullptr) {
        //LOGE("从自身 classes.dex 解密加载 dex 失败");
        return false;
    }

    //jobject newClassLoader = createInMemoryClassLoader(env, context, dexBuffers);//安卓8+
    jobject newClassLoader = createDexClassLoader(env, context, dexBuffers);//安卓8-
    if (newClassLoader == nullptr) {
        //LOGE("创建新的 ClassLoader 失败");
        return false;
    }

    if (!replaceLoadedApkClassLoader(env, context, newClassLoader)) {
        //LOGE("替换 ClassLoader 失败");
        return false;
    }

    if (!startRealApplication(env)) {
        //LOGE("启动真实 Application 失败");
        return false;
    }

    //LOGI("LoaderDEX 执行完成");
    //LOGI("LoaderDEX结束 PID=%d",getpid());
    return true;
}
ArkDexLoaderFunc ArkDexLoader_GetEntry() {
    volatile ArkDexLoaderFunc fn = LoaderDEX;
    return fn;
}