# Neo Art 加固 (Neo Art Jiagu)

[![Android CI](https://github.com/HSSkyBoy/Art-Jiagu/actions/workflows/android.yml/badge.svg)](https://github.com/HSSkyBoy/Art-Jiagu/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/HSSkyBoy/Art-Jiagu?color=blue)](https://github.com/HSSkyBoy/Art-Jiagu/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

Android APK 的本機 DEX 保護工具。它會建立殼 DEX、將目標 DEX 加密後附加至殼內，並在應用程式啟動時以 native loader 在記憶體中還原與載入。

## 功能

- 全量 DEX 加密與殼 APK 重打包。
- 以 `appComponentFactory` 為主要入口；Android 9 以下或相容模式時使用 `android:name` 入口。
- 可選的簽名綁定：以 APK v2/v3 簽名憑證雜湊作為 DEX 解密材料。
- 可選的 libsu RootService 相容模式：保留 RootService 相關 DEX，不納入加密載荷。
- 可選的業務字串加密：將保守篩選後的 `const-string` 改為 native 索引解密，字串池寫入 `assets/top_strings.bin`。
- APK 重打包、zipalign 與可選的 JKS 自動簽名。

## 字串加密的範圍

字串加密預設關閉，於設定頁開啟後才生效。它只處理 DEX 中可安全判斷的字串常量，並跳過：

- 類別描述符、方法簽名、一般類別名稱與過短字串。
- `System.loadLibrary`、反射 API 與 `Resources.getIdentifier` 附近的字串。
- RootService 相容模式中被保留的整個 DEX。

此功能採保守策略：寧可不改寫不確定字串，也不改壞應用程式行為。啟用前請先以測試包驗證目標應用的啟動與核心流程。

## 建置

### 環境需求

- JDK 21
- Android SDK：Compile SDK 37、Target SDK 36、Min SDK 26
- Android Gradle Plugin 9.3.1、Gradle 9.5.1
- NDK 29.0.13846066、CMake 3.22.1


```powershell
.\gradlew testDebugUnitTest assembleDebug assembleRelease
```

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`

## 專案結構

```text
app/src/main/
├── cpp/
│   ├── ArkDexLoader.cpp          # 記憶體 DEX 載入
│   ├── ArkStub.cpp               # 殼 JNI 與字串池解密
│   ├── ApkSignatureVerifier.cpp  # APK 簽名資訊讀取
│   └── ArkEnvGuard.cpp           # 執行環境檢查
├── java/top/nkbe/art/
│   ├── MainActivity.kt           # APK 處理、重打包與設定
│   ├── StringEncryptionRewriter.kt # 字串常量 DEX 重寫
│   ├── RootServiceDexDetector.kt # RootService 相容 DEX 偵測
│   └── NeoArtUi.kt               # 使用者介面
└── assets/
```

## 法律協議

本專案以 [Apache-2.0](LICENSE) 協議發布。
