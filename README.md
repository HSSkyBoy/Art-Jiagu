# Neo Art 加固 (Neo Art Jiagu)

[![Android CI](https://github.com/HSSkyBoy/Art-Jiagu/actions/workflows/android.yml/badge.svg)](https://github.com/HSSkyBoy/Art-Jiagu/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/HSSkyBoy/Art-Jiagu?color=blue)](https://github.com/HSSkyBoy/Art-Jiagu/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

基于 Jetpack Compose 与 Native C++ 底层 Syscall 的 Android APK 深度 DEX 加固 protection 工具。

---

## 🌟 核心特性 (Key Features)

### 1. 🛡️ Native C++ 直接 Syscall 防破簽 / 去簽名 (Anti-Signature-Killer)
- 使用 C++ 底層 Linux 系統呼叫 `syscall(__NR_openat)` 與 `syscall(__NR_pread64)` 直接讀取物理硬碟上的 `/data/app/.../base.apk`。
- 獨立解析 APK v2 (`0x7109871a`) 与 v3 (`0xf05368c0`) 簽名區塊 (`APK Sig Block 42`)，不依賴任何 Java `PackageManager` API。
- **完全繞過 `L-JINBIN/ApkSignatureKillerEx` 等所有 Java / libc `open` Hook！**
- 將提取出的證書 SHA-256 雜湊與殼 DEX 動態綁定解密，一旦被去簽名重簽名，殼 DEX 自動解密失敗並立即崩潰閃退 (Fail-Closed)。

### 2. 🎨 液態玻璃 (Liquid Glassmorphism) 3 頁式全新 UI
- 採用 **Jetpack Compose** 與 **[COUI KMP 模組庫](https://suqi8.github.io/coui/)** 打造。
- 支援 **Android 12+ 莫奈 (Monet) 動態色彩** 擷取與深色/淺色模式切換。
- **三頁式架構**：
  - **⚡ 管理 (Management)**：APK 保護工作台、即時策略預覽、加固主按鈕。
  - **⚙️ 設定 (Settings)**：全螢幕參數配置（預設關閉相容 NPatch 策略）、自訂 SO/殼類名、自訂 JKS。
  - **📜 日誌 (Logs)**：全螢幕 Console 主控台，支援一鍵複製與清空。
- **液態玻璃懸浮底欄 (Liquid Glassmorphism Navigation Bar)**：磨砂亞克力質感與平滑微動畫。

---

## 🚀 快速開始与构建 (Building)

### 環境要求
- **JDK**: 21
- **Android SDK**: 37 (Target 36 / Min 26)
- **AGP**: 9.3.1
- **Gradle**: 9.5.1
- **NDK**: 29.0.13846066
- **CMake**: 3.22.1

### Windows (PowerShell)
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

### Linux / macOS
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 📂 專案架構 (Architecture)

```text
Art-Jiagu/
├── app/src/main/
   ├── cpp/                        # Native C++ 殼與 Syscall 防護引擎
   │   ├── ApkSignatureVerifier.cpp # 直接 Syscall APK v2/v3 簽名區塊解析器
   │   ├── ArkDexLoader.cpp         # 記憶體 DEX 加密解密與 InMemoryDexClassLoader
   │   ├── ArkEnvGuard.cpp          # 環境安全檢測 (Xposed / LSPosed / Hook 檢測)
   │   └── ArkStub.cpp              # JNI 動態註冊與殼程序初始化
   ├── java/top/nkbe/art/
   │   ├── engine/                 # NPatch 可用之獨立加固引擎模組
   │   │   ├── JiaguEngine.java     # 線程安全、非阻塞式加固執行器
   │   │   ├── JiaguOptions.java    # 加固參數 (預設關閉符合按需策略)
   │   │   └── JiaguListener.java   # 日誌與進度監聽介面
   │   ├── NeoArtUi.kt             # Jetpack Compose 3 頁式與液態玻璃底欄 UI
   │   └── MainActivity.java       # 主入口與 Activity 相容層
   └── assets/                     # 預設 SO 廠商資料與偽 360 特徵
```

---

## 📄 授權協定 (License)

本项目基于 [Apache-2.0]([LICENSE](https://www.apache.org/licenses/LICENSE-2.0.txt)) 开源发布。欢迎提交 PR 和 Issue！
