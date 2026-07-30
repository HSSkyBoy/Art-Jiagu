#ifndef ARK_APK_SIGNATURE_VERIFIER_H
#define ARK_APK_SIGNATURE_VERIFIER_H

#include <jni.h>
#include <cstdint>
#include <string>

// Reads the installed APK directly via raw Linux syscalls (__NR_openat, __NR_pread64),
// parses the v2 / v3 APK Signing Block, and extracts the 32-byte SHA-256 digest
// of the signing certificate. Returns true on success, false on failure.
bool ReadApkSigningBlockSha256(JNIEnv *env, jobject context, uint8_t outSha256[32]);

#endif // ARK_APK_SIGNATURE_VERIFIER_H
