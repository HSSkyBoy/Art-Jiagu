#include "ApkSignatureVerifier.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <array>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace {
constexpr uint32_t kApkSignatureSchemeV2BlockId = 0x7109871aU;
constexpr uint32_t kApkSignatureSchemeV3BlockId = 0xf05368c0U;
constexpr size_t kEocdMinSize = 22;
constexpr size_t kMaxEocdSearch = 0xffff + kEocdMinSize;
constexpr char kSigningBlockMagic[] = "APK Sig Block 42";

uint32_t ReadU32(const uint8_t *data) {
    return static_cast<uint32_t>(data[0]) | (static_cast<uint32_t>(data[1]) << 8U) |
           (static_cast<uint32_t>(data[2]) << 16U) | (static_cast<uint32_t>(data[3]) << 24U);
}

uint16_t ReadU16(const uint8_t *data) {
    return static_cast<uint16_t>(data[0]) | (static_cast<uint16_t>(data[1]) << 8U);
}

uint64_t ReadU64(const uint8_t *data) {
    uint64_t value = 0;
    for (size_t i = 0; i < 8; ++i) value |= static_cast<uint64_t>(data[i]) << (i * 8U);
    return value;
}

bool ReadLengthPrefixed(const uint8_t *data, size_t limit, size_t *offset,
                        const uint8_t **value, size_t *length) {
    if (data == nullptr || offset == nullptr || value == nullptr || length == nullptr ||
        *offset > limit || limit - *offset < 4) return false;
    const uint32_t size = ReadU32(data + *offset);
    *offset += 4;
    if (size > limit - *offset) return false;
    *value = data + *offset;
    *length = size;
    *offset += size;
    return true;
}

bool RawReadAt(int fd, uint64_t offset, void *buffer, size_t length) {
    auto *bytes = static_cast<uint8_t *>(buffer);
    size_t done = 0;
    while (done < length) {
        const ssize_t count = static_cast<ssize_t>(syscall(__NR_pread64, fd, bytes + done,
                                                            length - done, offset + done));
        if (count <= 0) return false;
        done += static_cast<size_t>(count);
    }
    return true;
}

class Sha256 {
public:
    Sha256() { Reset(); }
    void Update(const uint8_t *data, size_t length) {
        if (data == nullptr) return;
        total_ += length;
        while (length > 0) {
            const size_t take = length < (64 - used_) ? length : (64 - used_);
            memcpy(block_.data() + used_, data, take);
            used_ += take;
            data += take;
            length -= take;
            if (used_ == 64) { Transform(block_.data()); used_ = 0; }
        }
    }
    std::array<uint8_t, 32> Final() {
        const uint64_t bits = total_ * 8U;
        const uint8_t marker = 0x80;
        Update(&marker, 1);
        const uint8_t zero = 0;
        while (used_ != 56) Update(&zero, 1);
        uint8_t lengthBytes[8]{};
        for (size_t i = 0; i < 8; ++i) lengthBytes[7 - i] = static_cast<uint8_t>(bits >> (i * 8U));
        Update(lengthBytes, sizeof(lengthBytes));
        std::array<uint8_t, 32> digest{};
        for (size_t i = 0; i < state_.size(); ++i) {
            digest[i * 4] = static_cast<uint8_t>(state_[i] >> 24U);
            digest[i * 4 + 1] = static_cast<uint8_t>(state_[i] >> 16U);
            digest[i * 4 + 2] = static_cast<uint8_t>(state_[i] >> 8U);
            digest[i * 4 + 3] = static_cast<uint8_t>(state_[i]);
        }
        return digest;
    }
private:
    static uint32_t RotateRight(uint32_t value, uint32_t count) {
        return (value >> count) | (value << (32U - count));
    }
    void Reset() {
        state_ = {0x6a09e667U, 0xbb67ae85U, 0x3c6ef372U, 0xa54ff53aU,
                  0x510e527fU, 0x9b05688cU, 0x1f83d9abU, 0x5be0cd19U};
        total_ = 0; used_ = 0;
    }
    void Transform(const uint8_t *block) {
        static constexpr uint32_t k[] = {
                0x428a2f98U,0x71374491U,0xb5c0fbcfU,0xe9b5dba5U,0x3956c25bU,0x59f111f1U,0x923f82a4U,0xab1c5ed5U,
                0xd807aa98U,0x12835b01U,0x243185beU,0x550c7dc3U,0x72be5d74U,0x80deb1feU,0x9bdc06a7U,0xc19bf174U,
                0xe49b69c1U,0xefbe4786U,0x0fc19dc6U,0x240ca1ccU,0x2de92c6fU,0x4a7484aaU,0x5cb0a9dcU,0x76f988daU,
                0x983e5152U,0xa831c66dU,0xb00327c8U,0xbf597fc7U,0xc6e00bf3U,0xd5a79147U,0x06ca6351U,0x14292967U,
                0x27b70a85U,0x2e1b2138U,0x4d2c6dfcU,0x53380d13U,0x650a7354U,0x766a0abbU,0x81c2c92eU,0x92722c85U,
                0xa2bfe8a1U,0xa81a664bU,0xc24b8b70U,0xc76c51a3U,0xd192e819U,0xd6990624U,0xf40e3585U,0x106aa070U,
                0x19a4c116U,0x1e376c08U,0x2748774cU,0x34b0bcb5U,0x391c0cb3U,0x4ed8aa4aU,0x5b9cca4fU,0x682e6ff3U,
                0x748f82eeU,0x78a5636fU,0x84c87814U,0x8cc70208U,0x90befffaU,0xa4506cebU,0xbef9a3f7U,0xc67178f2U};
        uint32_t words[64]{};
        for (size_t i = 0; i < 16; ++i) words[i] = (static_cast<uint32_t>(block[i * 4]) << 24U) |
                (static_cast<uint32_t>(block[i * 4 + 1]) << 16U) |
                (static_cast<uint32_t>(block[i * 4 + 2]) << 8U) | block[i * 4 + 3];
        for (size_t i = 16; i < 64; ++i) {
            const uint32_t s0 = RotateRight(words[i - 15], 7) ^ RotateRight(words[i - 15], 18) ^ (words[i - 15] >> 3U);
            const uint32_t s1 = RotateRight(words[i - 2], 17) ^ RotateRight(words[i - 2], 19) ^ (words[i - 2] >> 10U);
            words[i] = words[i - 16] + s0 + words[i - 7] + s1;
        }
        uint32_t a=state_[0], b=state_[1], c=state_[2], d=state_[3], e=state_[4], f=state_[5], g=state_[6], h=state_[7];
        for (size_t i = 0; i < 64; ++i) {
            const uint32_t s1 = RotateRight(e, 6) ^ RotateRight(e, 11) ^ RotateRight(e, 25);
            const uint32_t choose = (e & f) ^ (~e & g);
            const uint32_t temp1 = h + s1 + choose + k[i] + words[i];
            const uint32_t s0 = RotateRight(a, 2) ^ RotateRight(a, 13) ^ RotateRight(a, 22);
            const uint32_t majority = (a & b) ^ (a & c) ^ (b & c);
            const uint32_t temp2 = s0 + majority;
            h=g; g=f; f=e; e=d+temp1; d=c; c=b; b=a; a=temp1+temp2;
        }
        state_[0]+=a; state_[1]+=b; state_[2]+=c; state_[3]+=d;
        state_[4]+=e; state_[5]+=f; state_[6]+=g; state_[7]+=h;
    }
    std::array<uint32_t, 8> state_{};
    std::array<uint8_t, 64> block_{};
    uint64_t total_{};
    size_t used_{};
};

bool ExtractFirstCertificateSha256(const std::vector<uint8_t> &signers, uint8_t outSha256[32]) {
    size_t outerOffset = 0, signerOffset = 0, signedDataOffset = 0;
    size_t certificatesOffset = 0, certificateOffset = 0;
    const uint8_t *outer = nullptr, *signer = nullptr, *signedData = nullptr, *digests = nullptr, *certificates = nullptr, *certificate = nullptr;
    size_t outerLength = 0, signerLength = 0, signedDataLength = 0, ignoredLength = 0, certificatesLength = 0, certificateLength = 0;
    if (!ReadLengthPrefixed(signers.data(), signers.size(), &outerOffset, &outer, &outerLength) || outerOffset != signers.size() ||
        !ReadLengthPrefixed(outer, outerLength, &signerOffset, &signer, &signerLength) || signerOffset != outerLength ||
        !ReadLengthPrefixed(signer, signerLength, &signedDataOffset, &signedData, &signedDataLength) ||
        !ReadLengthPrefixed(signedData, signedDataLength, &certificatesOffset, &digests, &ignoredLength) ||
        !ReadLengthPrefixed(signedData, signedDataLength, &certificatesOffset, &certificates, &certificatesLength) ||
        !ReadLengthPrefixed(certificates, certificatesLength, &certificateOffset, &certificate, &certificateLength)) return false;
    
    Sha256 hash;
    hash.Update(certificate, certificateLength);
    const auto digest = hash.Final();
    memcpy(outSha256, digest.data(), 32);
    return true;
}

bool ReadSigningBlockSha256FromFile(const std::string &apkPath, uint8_t outSha256[32]) {
    const int fd = static_cast<int>(syscall(__NR_openat, AT_FDCWD, apkPath.c_str(), O_RDONLY | O_CLOEXEC, 0));
    if (fd < 0) return false;
    struct stat statInfo{};
    const bool statOk = fstat(fd, &statInfo) == 0 && statInfo.st_size >= static_cast<off_t>(kEocdMinSize);
    if (!statOk) { close(fd); return false; }
    const uint64_t fileSize = static_cast<uint64_t>(statInfo.st_size);
    const size_t tailSize = static_cast<size_t>(fileSize < kMaxEocdSearch ? fileSize : kMaxEocdSearch);
    std::vector<uint8_t> tail(tailSize);
    if (!RawReadAt(fd, fileSize - tailSize, tail.data(), tail.size())) { close(fd); return false; }
    size_t eocd = tail.size() - kEocdMinSize;
    bool found = false;
    for (;;) {
        if (ReadU32(tail.data() + eocd) == 0x06054b50U &&
            eocd + kEocdMinSize + ReadU16(tail.data() + eocd + 20) == tail.size()) {
            found = true;
            break;
        }
        if (eocd == 0) break;
        --eocd;
    }
    if (!found) { close(fd); return false; }
    const uint64_t centralDirectoryOffset = ReadU32(tail.data() + eocd + 16);
    if (centralDirectoryOffset < 24 || centralDirectoryOffset >= fileSize) { close(fd); return false; }
    uint8_t footer[24]{};
    if (!RawReadAt(fd, centralDirectoryOffset - sizeof(footer), footer, sizeof(footer)) ||
        memcmp(footer + 8, kSigningBlockMagic, sizeof(kSigningBlockMagic) - 1) != 0) { close(fd); return false; }
    const uint64_t blockSize = ReadU64(footer);
    if (blockSize < 24 || blockSize > centralDirectoryOffset - 8 || blockSize > 16U * 1024U * 1024U) { close(fd); return false; }
    const uint64_t blockOffset = centralDirectoryOffset - (blockSize + 8);
    std::vector<uint8_t> block(static_cast<size_t>(blockSize + 8));
    if (!RawReadAt(fd, blockOffset, block.data(), block.size()) || ReadU64(block.data()) != blockSize) { close(fd); return false; }
    close(fd);
    size_t offset = 8;
    const size_t entriesEnd = block.size() - 24;
    const uint8_t *v2 = nullptr, *v3 = nullptr;
    size_t v2Length = 0, v3Length = 0;
    while (offset < entriesEnd) {
        if (entriesEnd - offset < 12) return false;
        const uint64_t length = ReadU64(block.data() + offset);
        offset += 8;
        if (length < 4 || length > entriesEnd - offset) return false;
        const uint32_t id = ReadU32(block.data() + offset);
        const uint8_t *value = block.data() + offset + 4;
        const size_t valueLength = static_cast<size_t>(length - 4);
        if (id == kApkSignatureSchemeV3BlockId) { v3 = value; v3Length = valueLength; }
        if (id == kApkSignatureSchemeV2BlockId) { v2 = value; v2Length = valueLength; }
        offset += static_cast<size_t>(length);
    }
    if (offset != entriesEnd) return false;
    if (v3 != nullptr && ExtractFirstCertificateSha256(std::vector<uint8_t>(v3, v3 + v3Length), outSha256)) return true;
    return v2 != nullptr && ExtractFirstCertificateSha256(std::vector<uint8_t>(v2, v2 + v2Length), outSha256);
}

std::string GetSourceDirFromJni(JNIEnv *env, jobject context) {
    if (env == nullptr || context == nullptr) return "";
    jclass contextClass = env->GetObjectClass(context);
    if (contextClass == nullptr) { env->ExceptionClear(); return ""; }
    jmethodID getApplicationInfo = env->GetMethodID(contextClass, "getApplicationInfo", "()Landroid/content/pm/ApplicationInfo;");
    if (getApplicationInfo == nullptr) { env->ExceptionClear(); return ""; }
    jobject appInfo = env->CallObjectMethod(context, getApplicationInfo);
    if (env->ExceptionCheck() || appInfo == nullptr) { env->ExceptionClear(); return ""; }
    jclass appInfoClass = env->GetObjectClass(appInfo);
    if (appInfoClass == nullptr) { env->ExceptionClear(); return ""; }
    jfieldID sourceDir = env->GetFieldID(appInfoClass, "sourceDir", "Ljava/lang/String;");
    if (sourceDir == nullptr) { env->ExceptionClear(); return ""; }
    auto path = static_cast<jstring>(env->GetObjectField(appInfo, sourceDir));
    if (env->ExceptionCheck() || path == nullptr) { env->ExceptionClear(); return ""; }
    const char *chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(path, chars);
    return result;
}

std::string GetSourceDirFromMaps() {
    int fd = static_cast<int>(syscall(__NR_openat, AT_FDCWD, "/proc/self/maps", O_RDONLY | O_CLOEXEC, 0));
    if (fd < 0) return "";
    char buffer[4096];
    std::string cache;
    std::string foundPath;

    while (true) {
        ssize_t readSize = static_cast<ssize_t>(syscall(__NR_read, fd, buffer, sizeof(buffer)));
        if (readSize <= 0) break;
        cache.append(buffer, readSize);

        size_t pos;
        while ((pos = cache.find('\n')) != std::string::npos) {
            std::string line = cache.substr(0, pos);
            cache.erase(0, pos + 1);

            size_t slashPos = line.find("/data/app/");
            if (slashPos != std::string::npos) {
                std::string pathCandidate = line.substr(slashPos);
                if (pathCandidate.find(".apk") != std::string::npos) {
                    foundPath = pathCandidate;
                    break;
                }
            }
        }
        if (!foundPath.empty()) break;
    }
    syscall(__NR_close, fd);
    return foundPath;
}
} // namespace

bool ReadApkSigningBlockSha256(JNIEnv *env, jobject context, uint8_t outSha256[32]) {
    std::string sourceDir;
    if (env != nullptr && context != nullptr) {
        sourceDir = GetSourceDirFromJni(env, context);
    }
    if (sourceDir.empty()) {
        sourceDir = GetSourceDirFromMaps();
    }
    if (sourceDir.empty()) {
        return false;
    }
    return ReadSigningBlockSha256FromFile(sourceDir, outSha256);
}
