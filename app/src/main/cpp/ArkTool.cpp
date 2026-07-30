#include "ArkTool.h"

#include <jni.h>
#include <vector>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <cstdio>
#include <unistd.h>
#include <stdint.h>

#if __has_include(<sys/random.h>)
#include <sys/random.h>
#endif
static const unsigned char ARK_BLOCK_USE_SIGN_KEY_FLAG[4] = {
        0x41, 0x52, 0x4B, 0x53 // "ARKS"
};
static bool readOptionalByteArray(
        JNIEnv *env,
        jbyteArray array,
        std::vector<unsigned char> &out
) {
    out.clear();

    if (array == nullptr) {
        return true;
    }

    jsize len = env->GetArrayLength(array);
    if (len != 64) {
        return false;
    }

    out.resize(64);

    env->GetByteArrayRegion(
            array,
            0,
            64,
            reinterpret_cast<jbyte *>(out.data())
    );

    return !env->ExceptionCheck();
}
static jbyteArray makeByteArray(JNIEnv *env, const unsigned char *data, int len) {
    if (data == nullptr || len < 0) {
        return nullptr;
    }

    jbyteArray out = env->NewByteArray(len);
    if (out == nullptr) {
        return nullptr;
    }

    if (len > 0) {
        env->SetByteArrayRegion(
                out,
                0,
                len,
                reinterpret_cast<const jbyte *>(data)
        );
    }

    return out;
}

static bool readByteArray(JNIEnv *env, jbyteArray array, std::vector<unsigned char> &out) {
    if (array == nullptr) {
        return false;
    }

    jsize len = env->GetArrayLength(array);
    if (len <= 0) {
        return false;
    }

    out.resize(len);

    env->GetByteArrayRegion(
            array,
            0,
            len,
            reinterpret_cast<jbyte *>(out.data())
    );

    return !env->ExceptionCheck();
}

static void intToLe4Bytes(int value, unsigned char out[4]) {
    out[0] = static_cast<unsigned char>(value & 0xff);
    out[1] = static_cast<unsigned char>((value >> 8) & 0xff);
    out[2] = static_cast<unsigned char>((value >> 16) & 0xff);
    out[3] = static_cast<unsigned char>((value >> 24) & 0xff);
}

static void xorBytes(
        const unsigned char *data,
        int dataLen,
        const unsigned char *key,
        int keyLen,
        std::vector<unsigned char> &out
) {
    out.resize(dataLen);

    for (int i = 0; i < dataLen; i++) {
        out[i] = data[i] ^ key[i % keyLen];
    }
}

static bool fillRandomBytes(unsigned char *buffer, int len) {
    if (buffer == nullptr || len <= 0) {
        return false;
    }

#if defined(SYS_getrandom)
    ssize_t n = getrandom(buffer, len, 0);
    if (n == len) {
        return true;
    }
#endif

    FILE *fp = fopen("/dev/urandom", "rb");
    if (fp != nullptr) {
        size_t n = fread(buffer, 1, len, fp);
        fclose(fp);

        if (n == static_cast<size_t>(len)) {
            return true;
        }
    }

    srand(static_cast<unsigned int>(time(nullptr) ^ getpid()));

    for (int i = 0; i < len; i++) {
        buffer[i] = static_cast<unsigned char>(rand() & 0xff);
    }

    return true;
}

static uint32_t leftRotate(uint32_t value, uint32_t bits) {
    return (value << bits) | (value >> (32 - bits));
}

static void sha1Bytes(const unsigned char *data, size_t len, unsigned char out[20]) {
    uint32_t h0 = 0x67452301;
    uint32_t h1 = 0xEFCDAB89;
    uint32_t h2 = 0x98BADCFE;
    uint32_t h3 = 0x10325476;
    uint32_t h4 = 0xC3D2E1F0;

    uint64_t bitLen = static_cast<uint64_t>(len) * 8;

    size_t newLen = len + 1;
    while ((newLen % 64) != 56) {
        newLen++;
    }

    std::vector<unsigned char> msg(newLen + 8);
    memcpy(msg.data(), data, len);
    msg[len] = 0x80;

    for (int i = 0; i < 8; i++) {
        msg[newLen + i] = static_cast<unsigned char>((bitLen >> ((7 - i) * 8)) & 0xff);
    }

    for (size_t offset = 0; offset < msg.size(); offset += 64) {
        uint32_t w[80];

        for (int i = 0; i < 16; i++) {
            size_t j = offset + i * 4;
            w[i] = (static_cast<uint32_t>(msg[j]) << 24)
                   | (static_cast<uint32_t>(msg[j + 1]) << 16)
                   | (static_cast<uint32_t>(msg[j + 2]) << 8)
                   | static_cast<uint32_t>(msg[j + 3]);
        }

        for (int i = 16; i < 80; i++) {
            w[i] = leftRotate(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
        }

        uint32_t a = h0;
        uint32_t b = h1;
        uint32_t c = h2;
        uint32_t d = h3;
        uint32_t e = h4;

        for (int i = 0; i < 80; i++) {
            uint32_t f;
            uint32_t k;

            if (i < 20) {
                f = (b & c) | ((~b) & d);
                k = 0x5A827999;
            } else if (i < 40) {
                f = b ^ c ^ d;
                k = 0x6ED9EBA1;
            } else if (i < 60) {
                f = (b & c) | (b & d) | (c & d);
                k = 0x8F1BBCDC;
            } else {
                f = b ^ c ^ d;
                k = 0xCA62C1D6;
            }

            uint32_t temp = leftRotate(a, 5) + f + e + k + w[i];
            e = d;
            d = c;
            c = leftRotate(b, 30);
            b = a;
            a = temp;
        }

        h0 += a;
        h1 += b;
        h2 += c;
        h3 += d;
        h4 += e;
    }

    uint32_t h[5] = {h0, h1, h2, h3, h4};

    for (int i = 0; i < 5; i++) {
        out[i * 4] = static_cast<unsigned char>((h[i] >> 24) & 0xff);
        out[i * 4 + 1] = static_cast<unsigned char>((h[i] >> 16) & 0xff);
        out[i * 4 + 2] = static_cast<unsigned char>((h[i] >> 8) & 0xff);
        out[i * 4 + 3] = static_cast<unsigned char>(h[i] & 0xff);
    }
}

static uint32_t adler32Bytes(const unsigned char *data, size_t len) {
    const uint32_t MOD_ADLER = 65521;

    uint32_t a = 1;
    uint32_t b = 0;

    for (size_t i = 0; i < len; i++) {
        a = (a + data[i]) % MOD_ADLER;
        b = (b + a) % MOD_ADLER;
    }

    return (b << 16) | a;
}

static jbyteArray native_buildEncryptedBlock(JNIEnv *env, jobject thiz, jbyteArray plainData) {
    std::vector<unsigned char> plain;

    if (!readByteArray(env, plainData, plain)) {
        return nullptr;
    }

    unsigned char key[64];
    if (!fillRandomBytes(key, sizeof(key))) {
        return nullptr;
    }

    std::vector<unsigned char> encryptedData;
    xorBytes(
            plain.data(),
            static_cast<int>(plain.size()),
            key,
            sizeof(key),
            encryptedData
    );

    unsigned char lenBytes[4];
    intToLe4Bytes(static_cast<int>(plain.size()), lenBytes);

    std::vector<unsigned char> encryptedLen;
    xorBytes(
            lenBytes,
            4,
            key,
            sizeof(key),
            encryptedLen
    );

    std::vector<unsigned char> result;
    result.reserve(encryptedData.size() + encryptedLen.size() + sizeof(key));

    result.insert(result.end(), encryptedData.begin(), encryptedData.end());
    result.insert(result.end(), encryptedLen.begin(), encryptedLen.end());
    result.insert(result.end(), key, key + sizeof(key));

    return makeByteArray(env, result.data(), static_cast<int>(result.size()));
}

static jbyteArray native_fixDexHeader(JNIEnv *env, jobject thiz, jbyteArray dexData) {
    std::vector<unsigned char> dex;

    if (!readByteArray(env, dexData, dex)) {
        return nullptr;
    }

    if (dex.size() < 0x70) {
        return nullptr;
    }

    int fileSize = static_cast<int>(dex.size());

    unsigned char fileSizeBytes[4];
    intToLe4Bytes(fileSize, fileSizeBytes);
    memcpy(dex.data() + 32, fileSizeBytes, 4);

    unsigned char signature[20];
    sha1Bytes(dex.data() + 32, dex.size() - 32, signature);
    memcpy(dex.data() + 12, signature, 20);

    uint32_t checksum = adler32Bytes(dex.data() + 12, dex.size() - 12);

    unsigned char checksumBytes[4];
    intToLe4Bytes(static_cast<int>(checksum), checksumBytes);
    memcpy(dex.data() + 8, checksumBytes, 4);

    return makeByteArray(env, dex.data(), static_cast<int>(dex.size()));
}

static jboolean native_isValidDex(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (data == nullptr) {
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(data);
    if (len < 0x70) {
        return JNI_FALSE;
    }

    unsigned char magic[4];

    env->GetByteArrayRegion(
            data,
            0,
            4,
            reinterpret_cast<jbyte *>(magic)
    );

    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }

    if (magic[0] == 'd'
        && magic[1] == 'e'
        && magic[2] == 'x'
        && magic[3] == '\n') {
        return JNI_TRUE;
    }

    return JNI_FALSE;
}

static jbyteArray native_intToLe4(JNIEnv *env, jobject thiz, jint value) {
    unsigned char out[4];
    intToLe4Bytes(value, out);
    return makeByteArray(env, out, 4);
}

static void throwRuntimeException(JNIEnv *env, const char *msg) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) {
        env->ThrowNew(cls, msg);
    }
}

static jstring getFileAbsolutePath(JNIEnv *env, jobject fileObj) {
    jclass clsFile = env->FindClass("java/io/File");
    jmethodID midGetAbsolutePath = env->GetMethodID(
            clsFile,
            "getAbsolutePath",
            "()Ljava/lang/String;"
    );

    return (jstring) env->CallObjectMethod(fileObj, midGetAbsolutePath);
}

static std::vector<unsigned char> readAllBytesFromInputStream(JNIEnv *env, jobject inputStream) {
    std::vector<unsigned char> result;

    jclass clsInputStream = env->FindClass("java/io/InputStream");
    jmethodID midRead = env->GetMethodID(clsInputStream, "read", "([B)I");
    jmethodID midClose = env->GetMethodID(clsInputStream, "close", "()V");

    jbyteArray buffer = env->NewByteArray(8192);

    while (true) {
        jint len = env->CallIntMethod(inputStream, midRead, buffer);
        if (env->ExceptionCheck()) {
            result.clear();
            return result;
        }

        if (len <= 0) {
            break;
        }

        size_t oldSize = result.size();
        result.resize(oldSize + len);

        env->GetByteArrayRegion(
                buffer,
                0,
                len,
                reinterpret_cast<jbyte *>(result.data() + oldSize)
        );

        if (env->ExceptionCheck()) {
            result.clear();
            return result;
        }
    }

    env->CallVoidMethod(inputStream, midClose);
    return result;
}

static std::vector<unsigned char> readAllBytesFromFile(JNIEnv *env, jobject fileObj) {
    std::vector<unsigned char> result;

    jclass clsFileInputStream = env->FindClass("java/io/FileInputStream");
    jmethodID midInit = env->GetMethodID(
            clsFileInputStream,
            "<init>",
            "(Ljava/io/File;)V"
    );

    jobject fis = env->NewObject(clsFileInputStream, midInit, fileObj);
    if (env->ExceptionCheck() || fis == nullptr) {
        result.clear();
        return result;
    }

    return readAllBytesFromInputStream(env, fis);
}

static bool writeAllBytesToFile(JNIEnv *env, jobject fileObj, const std::vector<unsigned char> &data) {
    jclass clsFileOutputStream = env->FindClass("java/io/FileOutputStream");
    jmethodID midInit = env->GetMethodID(
            clsFileOutputStream,
            "<init>",
            "(Ljava/io/File;)V"
    );

    jobject fos = env->NewObject(clsFileOutputStream, midInit, fileObj);
    if (env->ExceptionCheck() || fos == nullptr) {
        return false;
    }

    jmethodID midWrite = env->GetMethodID(clsFileOutputStream, "write", "([B)V");
    jmethodID midFlush = env->GetMethodID(clsFileOutputStream, "flush", "()V");
    jmethodID midClose = env->GetMethodID(clsFileOutputStream, "close", "()V");

    jbyteArray arr = makeByteArray(
            env,
            data.data(),
            static_cast<int>(data.size())
    );

    env->CallVoidMethod(fos, midWrite, arr);
    if (env->ExceptionCheck()) {
        return false;
    }

    env->CallVoidMethod(fos, midFlush);
    env->CallVoidMethod(fos, midClose);

    return !env->ExceptionCheck();
}

static void appendLogOnUiNative(JNIEnv *env, jobject thiz, const char *msg) {
    jclass cls = env->GetObjectClass(thiz);
    jmethodID mid = env->GetMethodID(
            cls,
            "appendLogOnUi",
            "(Ljava/lang/String;)V"
    );

    if (mid == nullptr) {
        env->ExceptionClear();
        return;
    }

    jstring text = env->NewStringUTF(msg);
    env->CallVoidMethod(thiz, mid, text);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

static bool isValidDexBytes(const std::vector<unsigned char> &data) {
    return data.size() >= 0x70
           && data[0] == 'd'
           && data[1] == 'e'
           && data[2] == 'x'
           && data[3] == '\n';
}

static std::vector<unsigned char> buildEncryptedBlockBytes(
        const unsigned char *data,
        int len,
        const unsigned char *externalKey,
        int externalKeyLen
) {
    std::vector<unsigned char> result;

    if (data == nullptr || len <= 0) {
        return result;
    }

    bool useExternalKey = externalKey != nullptr && externalKeyLen == 64;

    unsigned char randomKey[64];

    const unsigned char *key = nullptr;
    int keyLen = 64;

    if (useExternalKey) {
        key = externalKey;
    } else {
        if (!fillRandomBytes(randomKey, sizeof(randomKey))) {
            return result;
        }

        key = randomKey;
    }

    std::vector<unsigned char> encryptedData;
    xorBytes(data, len, key, keyLen, encryptedData);

    unsigned char lenBytes[4];
    intToLe4Bytes(len, lenBytes);

    std::vector<unsigned char> encryptedLen;
    xorBytes(lenBytes, 4, key, keyLen, encryptedLen);

    if (useExternalKey) {
        // 格式：
        // [加密数据][加密长度4字节][特征4字节]
        result.reserve(
                encryptedData.size()
                + encryptedLen.size()
                + sizeof(ARK_BLOCK_USE_SIGN_KEY_FLAG)
        );

        result.insert(result.end(), encryptedData.begin(), encryptedData.end());
        result.insert(result.end(), encryptedLen.begin(), encryptedLen.end());
        result.insert(
                result.end(),
                ARK_BLOCK_USE_SIGN_KEY_FLAG,
                ARK_BLOCK_USE_SIGN_KEY_FLAG + sizeof(ARK_BLOCK_USE_SIGN_KEY_FLAG)
        );
    } else {
        // 原格式：
        // [加密数据][加密长度4字节][随机密钥64字节]
        result.reserve(
                encryptedData.size()
                + encryptedLen.size()
                + sizeof(randomKey)
        );

        result.insert(result.end(), encryptedData.begin(), encryptedData.end());
        result.insert(result.end(), encryptedLen.begin(), encryptedLen.end());
        result.insert(result.end(), randomKey, randomKey + sizeof(randomKey));
    }

    return result;
}

static std::vector<unsigned char> fixDexHeaderBytes(std::vector<unsigned char> dex) {
    if (dex.size() < 0x70) {
        dex.clear();
        return dex;
    }

    unsigned char fileSizeBytes[4];
    intToLe4Bytes(static_cast<int>(dex.size()), fileSizeBytes);
    memcpy(dex.data() + 32, fileSizeBytes, 4);

    unsigned char signature[20];
    sha1Bytes(dex.data() + 32, dex.size() - 32, signature);
    memcpy(dex.data() + 12, signature, 20);

    uint32_t checksum = adler32Bytes(dex.data() + 12, dex.size() - 12);

    unsigned char checksumBytes[4];
    intToLe4Bytes(static_cast<int>(checksum), checksumBytes);
    memcpy(dex.data() + 8, checksumBytes, 4);

    return dex;
}

static void native_buildEncryptedShellDex(
        JNIEnv *env,
        jobject thiz,
        jobject apkFile,
        jobject shellDexFile,
        jstring realApplicationName,
        jbyteArray signHash64
) {
    if (apkFile == nullptr || shellDexFile == nullptr || realApplicationName == nullptr) {
        throwRuntimeException(env, "参数为空");
        return;
    }

    std::vector<unsigned char> signKey;

    if (!readOptionalByteArray(env, signHash64, signKey)) {
        throwRuntimeException(env, "签名密钥必须为空或64字节");
        return;
    }

    const unsigned char *dexKey = signKey.empty() ? nullptr : signKey.data();
    int dexKeyLen = signKey.empty() ? 0 : static_cast<int>(signKey.size());

    std::vector<unsigned char> shellDex = readAllBytesFromFile(env, shellDexFile);
    if (env->ExceptionCheck()) {
        return;
    }

    {
        char logText[192];
        snprintf(logText, sizeof(logText), "读取壳 dex 成功，大小：%d", static_cast<int>(shellDex.size()));
        appendLogOnUiNative(env, thiz, logText);
    }

    if (!isValidDexBytes(shellDex)) {
        throwRuntimeException(env, "壳 dex 非法");
        return;
    }

    jstring apkPathJ = getFileAbsolutePath(env, apkFile);
    if (apkPathJ == nullptr) {
        throwRuntimeException(env, "获取 APK 路径失败");
        return;
    }
    appendLogOnUiNative(env, thiz, "开始扫描目标 APK 中的 dex");

    jclass clsZipFile = env->FindClass("java/util/zip/ZipFile");
    jmethodID midZipInit = env->GetMethodID(
            clsZipFile,
            "<init>",
            "(Ljava/lang/String;)V"
    );

    jobject zipFile = env->NewObject(clsZipFile, midZipInit, apkPathJ);
    if (env->ExceptionCheck() || zipFile == nullptr) {
        return;
    }

    jmethodID midGetEntry = env->GetMethodID(
            clsZipFile,
            "getEntry",
            "(Ljava/lang/String;)Ljava/util/zip/ZipEntry;"
    );

    jmethodID midGetInputStream = env->GetMethodID(
            clsZipFile,
            "getInputStream",
            "(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;"
    );

    jmethodID midZipClose = env->GetMethodID(
            clsZipFile,
            "close",
            "()V"
    );

    std::vector<unsigned char> payload;
    int dexCount = 0;
    int totalDexBytes = 0;

    for (int i = 1;; i++) {
        char dexName[64];

        if (i == 1) {
            snprintf(dexName, sizeof(dexName), "classes.dex");
        } else {
            snprintf(dexName, sizeof(dexName), "classes%d.dex", i);
        }

        jstring dexNameJ = env->NewStringUTF(dexName);
        jobject entry = env->CallObjectMethod(zipFile, midGetEntry, dexNameJ);

        if (env->ExceptionCheck()) {
            env->CallVoidMethod(zipFile, midZipClose);
            return;
        }

        if (entry == nullptr) {
            break;
        }

        jobject inputStream = env->CallObjectMethod(zipFile, midGetInputStream, entry);
        if (env->ExceptionCheck() || inputStream == nullptr) {
            env->CallVoidMethod(zipFile, midZipClose);
            return;
        }

        std::vector<unsigned char> dexData = readAllBytesFromInputStream(env, inputStream);
        if (env->ExceptionCheck()) {
            env->CallVoidMethod(zipFile, midZipClose);
            return;
        }

        if (!isValidDexBytes(dexData)) {
            char logText[128];
            snprintf(logText, sizeof(logText), "发现非法 dex，停止处理：%s", dexName);
            appendLogOnUiNative(env, thiz, logText);
            break;
        }

        std::vector<unsigned char> block = buildEncryptedBlockBytes(
                dexData.data(),
                static_cast<int>(dexData.size()),
                dexKey,
                dexKeyLen
        );

        if (block.empty()) {
            env->CallVoidMethod(zipFile, midZipClose);
            throwRuntimeException(env, "dex 加密失败");
            return;
        }

        payload.insert(payload.end(), block.begin(), block.end());
        dexCount++;
        totalDexBytes += static_cast<int>(dexData.size());

        char logText[160];
        snprintf(
                logText,
                sizeof(logText),
                "已加密：%s，大小：%d",
                dexName,
                static_cast<int>(dexData.size())
        );
        appendLogOnUiNative(env, thiz, logText);
    }

    env->CallVoidMethod(zipFile, midZipClose);

    if (dexCount <= 0) {
        throwRuntimeException(env, "APK 中没有找到合法 dex");
        return;
    }

    {
        char logText[192];
        snprintf(logText, sizeof(logText), "目标 dex 扫描完成，共 %d 个，合计 %d 字节", dexCount, totalDexBytes);
        appendLogOnUiNative(env, thiz, logText);
    }

    const char *appNameChars = env->GetStringUTFChars(realApplicationName, nullptr);
    if (appNameChars == nullptr) {
        throwRuntimeException(env, "获取入口类名失败");
        return;
    }

    int appNameLen = static_cast<int>(strlen(appNameChars));

    std::vector<unsigned char> appBlock = buildEncryptedBlockBytes(
            reinterpret_cast<const unsigned char *>(appNameChars),
            appNameLen,
            nullptr,
            0
    );

    char appLog[256];
    snprintf(appLog, sizeof(appLog), "已加密入口：%s", appNameChars);
    appendLogOnUiNative(env, thiz, appLog);

    env->ReleaseStringUTFChars(realApplicationName, appNameChars);

    if (appBlock.empty()) {
        throwRuntimeException(env, "入口类名加密失败");
        return;
    }

    payload.insert(payload.end(), appBlock.begin(), appBlock.end());

    unsigned char dexCountBytes[4];
    intToLe4Bytes(dexCount, dexCountBytes);
    payload.insert(payload.end(), dexCountBytes, dexCountBytes + 4);
    {
        char logText[192];
        snprintf(logText, sizeof(logText), "准备合并壳 dex 与载荷，载荷大小：%d", static_cast<int>(payload.size()));
        appendLogOnUiNative(env, thiz, logText);
    }

    std::vector<unsigned char> finalDex;
    finalDex.reserve(shellDex.size() + payload.size());
    finalDex.insert(finalDex.end(), shellDex.begin(), shellDex.end());
    finalDex.insert(finalDex.end(), payload.begin(), payload.end());

    std::vector<unsigned char> fixedDex = fixDexHeaderBytes(finalDex);
    if (fixedDex.empty()) {
        throwRuntimeException(env, "修复 dex 头失败");
        return;
    }
    {
        char logText[192];
        snprintf(logText, sizeof(logText), "dex 头修复完成，最终大小：%d", static_cast<int>(fixedDex.size()));
        appendLogOnUiNative(env, thiz, logText);
    }

    if (!writeAllBytesToFile(env, shellDexFile, fixedDex)) {
        throwRuntimeException(env, "写入壳 dex 失败");
        return;
    }
    appendLogOnUiNative(env, thiz, "加密壳 dex 已写回文件");
}

static JNINativeMethod gMethods[] = {
        {
                const_cast<char *>("buildEncryptedBlock"),
                const_cast<char *>("([B)[B"),
                reinterpret_cast<void *>(native_buildEncryptedBlock)
        },
        {
                const_cast<char *>("fixDexHeader"),
                const_cast<char *>("([B)[B"),
                reinterpret_cast<void *>(native_fixDexHeader)
        },
        {
                const_cast<char *>("isValidDex"),
                const_cast<char *>("([B)Z"),
                reinterpret_cast<void *>(native_isValidDex)
        },
        {
                const_cast<char *>("intToLe4"),
                const_cast<char *>("(I)[B"),
                reinterpret_cast<void *>(native_intToLe4)
        },
        {
                const_cast<char *>("buildEncryptedShellDex"),
                const_cast<char *>("(Ljava/io/File;Ljava/io/File;Ljava/lang/String;[B)V"),
                reinterpret_cast<void *>(native_buildEncryptedShellDex)
        },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;

    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("top/nkbe/art/MainActivity");
    if (clazz == nullptr) {
        return JNI_ERR;
    }

    jint ret = env->RegisterNatives(
            clazz,
            gMethods,
            sizeof(gMethods) / sizeof(gMethods[0])
    );

    if (ret != JNI_OK) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
