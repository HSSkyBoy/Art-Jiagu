# ArkDex 加固工具说明

## 项目简介

这是一个 APK Dex 加固工具。

整个加固工具仅包含一个 `MainActivity`，所有核心逻辑均集中在该类中实现。

项目编译后会生成两个动态库：

* `libArkTool.so`
* `libArkStub.so`

其中：

### libArkTool.so

用于实现加固过程中所需的各种加密、解密及辅助功能。

### libArkStub.so

用于实现壳程序的启动、环境检测以及 Dex 动态加载逻辑。

---

# libArkStub.so 工作原理

## 1. 动态注册入口方法

在 `ArkStub.cpp` 中完成 JNI 方法动态注册。

注册的目标类名并非固定写死，而是在壳 Dex 加载 So 时动态传入，因此可以适配不同的壳 Dex。

---

## 2. 环境安全检测

JNI 方法注册完成后，会进入 `ArkEnvGuard.cpp` 中执行环境检测逻辑。

核心入口：

```cpp
ArkEnvGuard_CheckAndLoad_Impl()
```

当前已实现部分基础检测，例如：

* Xposed 注入检测
* LSPosed 注入检测
* 常见 Hook 框架检测

开发者可根据需要在此处增加更多安全检测逻辑，例如：

* Root 检测
* Frida 检测
* Magisk 检测
* 模拟器检测
* 调试器检测
* 内存篡改检测

只有当所有检测全部通过后，才会继续执行 Dex 加载流程。

---

## 3. Dex 解密与加载

环境检测通过后，会进入 `ArkDexLoader.cpp` 中的：

```cpp
LoaderDEX()
```

进行 Dex 解密和动态加载。

### Android 8.0 及以上

采用系统提供的：

```java
InMemoryDexClassLoader
```

实现 Dex 不落地加载。

解密后的 Dex 直接驻留在内存中，不会写入磁盘。

---

### Android 8.0 以下

采用：

```java
DexClassLoader
```

进行加载。

由于系统限制，需要先将 Dex 临时写入磁盘再进行加载。

这种方式存在一定安全风险，因此仅作为低版本系统兼容方案。

---

# 加固流程

整体加固流程如下：

```text
解压 APK
    ↓
提取 AndroidManifest.xml 和 Dex
    ↓
使用 dexlib2 动态生成壳 Dex
    ↓
将原始 Dex 追加到壳 Dex 尾部
    ↓
替换 Application
    ↓
重新打包 APK
    ↓
重新签名
```

---

# 壳 So 的获取方式

理论上壳程序（`libArkStub.so`）可以独立维护和发布。

但为了简化开发流程，本项目直接将壳 So 编译到加固工具自身 APK 中。

在 `MainActivity` 的：

```java
getSelfApkStubAbiList()
```

方法中，会将当前 APK 视为一个 Zip 压缩包进行解析，并从 APK 内部提取对应 ABI 的壳 So 文件。

这样可以避免：

* 单独维护壳资源包
* 单独下载壳文件
* 版本同步问题

同时也使整个加固工具能够以单 APK 的形式独立运行。
