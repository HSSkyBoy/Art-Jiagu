package top.nkbe.art

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21s
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction12x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22b
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction23x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.ark.jar.xml2axml.test.Xml2AxmlTool
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    // ── Properties ──
    private lateinit var uiController: NeoArtUiController
    private var isPermissionDialogShowing = false
    private var hasInitMain = false
    private lateinit var soNamePresets: Array<SoNamePreset>
    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        appendLogOnUi("Shizuku 服务已连接")
        refreshShizukuStatus()
    }
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        appendLogOnUi("Shizuku 服务已断开")
        refreshShizukuStatus()
    }
    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQ_SHIZUKU_PERMISSION) return@OnRequestPermissionResultListener
            if (grantResult == PERMISSION_GRANTED) {
                appendLogOnUi("Shizuku 授权成功")
                uiController.showSnackbar("Shizuku 已授权", StatusColor.ACTIVE)
            } else {
                appendLogOnUi("Shizuku 授权被拒绝")
                uiController.showSnackbar("Shizuku 授权失败", StatusColor.ERROR)
            }
            refreshShizukuStatus()
        }

    // ── Inner types ──
    private data class ArkSettings(
        var soName: String,
        var stubClassName: String,
        var savePath: String,
        var autoSign: Boolean,
        var emulatorCompatibility: Boolean,
        var rootServiceCompatibility: Boolean,
        var stringEncryption: Boolean,
        var shizukuSilentInstall: Boolean,
        var fake360Type: Int,
        var useCustomJks: Boolean,
        var jksPath: String,
        var jksStorePass: String,
        var jksAlias: String,
        var jksKeyPass: String,
    )

    private data class SoNamePreset(val feature: String, val soName: String)
    private data class StringEncryptionInput(val apk: File, val poolFile: File, val rewrittenStrings: Int)

    // ── Native methods ──
    private external fun buildEncryptedBlock(plainData: ByteArray?): ByteArray?
    private external fun fixDexHeader(dexData: ByteArray?): ByteArray?
    private external fun isValidDex(data: ByteArray?): Boolean
    private external fun intToLe4(value: Int): ByteArray?
    @Throws(Exception::class)
    private external fun buildEncryptedShellDex(
        apkFile: File,
        shellDexFile: File,
        realApplicationName: String,
        signHash64: ByteArray?,
        preservedDexEntries: Array<String>,
    )

    // ── Lifecycle ──
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        uiController = NeoArtUi.install(
            this,
            Runnable { openApkSelector() },
            java.util.concurrent.Callable { loadSettingsFlow() },
        )
        uiController.onSaveSettingsHandler = { handleSaveSettingsFromCompose(it) }
        uiController.onRequestShizukuAuthHandler = { requestShizukuAuthorization() }
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        refreshShizukuStatus()

        checkPermissionOrShowDialog()
        soNamePresets = loadSoNamePresets()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (hasInitMain) return
        if (hasAllFilePermission()) initMainPage() else showPermissionDialog()
    }

    // ── Permission handling ──
    private fun checkPermissionOrShowDialog() {
        if (hasAllFilePermission()) initMainPage() else showPermissionDialog()
    }

    private fun showPermissionDialog() {
        if (hasAllFilePermission()) { initMainPage(); return }
        if (isPermissionDialogShowing) return
        isPermissionDialogShowing = true

        uiController.showDialog(
            NeoArtDialogState(
                title = "需要文件访问权限",
                message = "本工具需要文件访问权限，才能读取和处理 APK 文件。请点击去授权。",
                confirmText = "去授权",
                cancelable = false,
                onConfirm = {
                    isPermissionDialogShowing = false
                    openAllFilePermissionPage()
                },
                onDismiss = {
                    isPermissionDialogShowing = false
                },
            )
        )
    }

    private fun hasAllFilePermission(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    private fun openAllFilePermissionPage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
                REQ_STORAGE_PERMISSION,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE_PERMISSION) {
            if (hasAllFilePermission()) initMainPage()
            else {
                Toast.makeText(this, "未授予文件访问权限", Toast.LENGTH_LONG).show()
                showPermissionDialog()
            }
        }
    }

    // ── Init ──
    private fun initMainPage() {
        if (hasInitMain) return
        hasInitMain = true
        val workDir = workDir
        cleanWorkDirOnStart(workDir)
        appendLog("加固器初始化完成")
        appendLog("等待选择 APK 文件")
    }

    private fun cleanWorkDirOnStart(workDir: File) {
        if (workDir.exists()) cleanTempFiles(workDir)
    }

    // ── SO presets ──
    private fun loadSoNamePresets(): Array<SoNamePreset> {
        val list = ArrayList<SoNamePreset>()
        try {
            val json = assets.open("so_name_presets.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.optString("title", "").trim()
                val name = obj.optString("name", "").trim()
                if (title.isNotEmpty() && name.isNotEmpty()) {
                    list.add(SoNamePreset(title, name))
                }
            }
        } catch (_: Exception) {
            list.add(SoNamePreset("Ark默认", DEFAULT_SO_NAME))
        }
        return list.toTypedArray()
    }

    // ── Settings persistence ──
    private fun readArkSettings(): ArkSettings {
        val sp = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE)
        val defaultSavePath = workDir.absolutePath

        var soName = sp.getString(KEY_SO_NAME, DEFAULT_SO_NAME) ?: DEFAULT_SO_NAME
        var stubClassName = sp.getString(KEY_STUB_CLASS_NAME, DEFAULT_STUB_CLASS_NAME) ?: DEFAULT_STUB_CLASS_NAME
        var savePath = sp.getString(KEY_SAVE_PATH, defaultSavePath) ?: defaultSavePath
        val autoSign = sp.getBoolean(KEY_AUTO_SIGN, false)
        val emulatorCompatibility = sp.getBoolean(KEY_EMULATOR_COMPATIBILITY, false)
        val rootServiceCompatibility = sp.getBoolean(KEY_ROOT_SERVICE_COMPATIBILITY, false)
        val stringEncryption = sp.getBoolean(KEY_STRING_ENCRYPTION, false)
        val shizukuSilentInstall = sp.getBoolean(KEY_SHIZUKU_SILENT_INSTALL, false)
        var fake360Type = sp.getInt(KEY_FAKE_360_TYPE, FAKE_360_OFF)
        if (fake360Type !in FAKE_360_OFF..FAKE_360_ENTERPRISE) fake360Type = FAKE_360_OFF
        val useCustomJks = sp.getBoolean(KEY_USE_CUSTOM_JKS, false)
        val jksPath = sp.getString(KEY_JKS_PATH, "") ?: ""
        val jksStorePass = sp.getString(KEY_JKS_STORE_PASS, "") ?: ""
        val jksAlias = sp.getString(KEY_JKS_ALIAS, "") ?: ""
        val jksKeyPass = sp.getString(KEY_JKS_KEY_PASS, "") ?: ""

        if (soName.isBlank()) soName = DEFAULT_SO_NAME
        if (stubClassName.isBlank() || ShellClassNamePolicy.containsArt(stubClassName)) {
            stubClassName = ShellClassNamePolicy.normalize(stubClassName)
            sp.edit().putString(KEY_STUB_CLASS_NAME, stubClassName).apply()
        }
        if (savePath.isBlank()) savePath = defaultSavePath

        return ArkSettings(
            soName, stubClassName, savePath, autoSign, emulatorCompatibility, rootServiceCompatibility, stringEncryption, shizukuSilentInstall,
            fake360Type, useCustomJks, jksPath, jksStorePass, jksAlias, jksKeyPass,
        )
    }

    private fun saveArkSettings(
        soName: String, stubClassName: String, savePath: String, autoSign: Boolean,
        emulatorCompatibility: Boolean,
        rootServiceCompatibility: Boolean,
        stringEncryption: Boolean,
        shizukuSilentInstall: Boolean,
        fake360Type: Int, useCustomJks: Boolean, jksPath: String,
        jksStorePass: String, jksAlias: String, jksKeyPass: String,
    ) {
        getSharedPreferences(SP_SETTINGS, MODE_PRIVATE).edit()
            .putString(KEY_SO_NAME, soName)
            .putString(KEY_STUB_CLASS_NAME, stubClassName)
            .putString(KEY_SAVE_PATH, savePath)
            .putBoolean(KEY_AUTO_SIGN, autoSign)
            .putBoolean(KEY_EMULATOR_COMPATIBILITY, emulatorCompatibility)
            .putBoolean(KEY_ROOT_SERVICE_COMPATIBILITY, rootServiceCompatibility)
            .putBoolean(KEY_STRING_ENCRYPTION, stringEncryption)
            .putBoolean(KEY_SHIZUKU_SILENT_INSTALL, shizukuSilentInstall)
            .putInt(KEY_FAKE_360_TYPE, fake360Type)
            .putBoolean(KEY_USE_CUSTOM_JKS, useCustomJks)
            .putString(KEY_JKS_PATH, jksPath)
            .putString(KEY_JKS_STORE_PASS, jksStorePass)
            .putString(KEY_JKS_ALIAS, jksAlias)
            .putString(KEY_JKS_KEY_PASS, jksKeyPass)
            .apply()
    }

    private fun loadSettingsFlow(): ArkSettingsData {
        val s = readArkSettings()
        return ArkSettingsData(
            soName = s.soName,
            stubClassName = s.stubClassName,
            savePath = s.savePath,
            autoSign = s.autoSign,
            emulatorCompatibility = s.emulatorCompatibility,
            rootServiceCompatibility = s.rootServiceCompatibility,
            stringEncryption = s.stringEncryption,
            shizukuSilentInstall = s.shizukuSilentInstall,
            fake360Type = s.fake360Type,
            useCustomJks = s.useCustomJks,
            jksPath = s.jksPath,
            jksStorePass = s.jksStorePass,
            jksAlias = s.jksAlias,
            jksKeyPass = s.jksKeyPass,
        )
    }

    private fun handleSaveSettingsFromCompose(data: ArkSettingsData): String? {
        val soName = data.soName.trim()
        var stubClassName = data.stubClassName.trim()
        val savePath = data.savePath.trim()
        val autoSign = data.autoSign
        val emulatorCompatibility = data.emulatorCompatibility
        val rootServiceCompatibility = data.rootServiceCompatibility
        val stringEncryption = data.stringEncryption
        val shizukuSilentInstall = data.shizukuSilentInstall
        val fake360Type = data.fake360Type
        if (fake360Type != FAKE_360_OFF) stubClassName = FAKE_360_STUB_CLASS_NAME
        val useCustomJks = data.useCustomJks
        val jksPath = data.jksPath.trim()
        val jksStorePass = data.jksStorePass
        val jksAlias = data.jksAlias.trim()
        val jksKeyPass = data.jksKeyPass

        if (!isValidSoName(soName)) return "so名称不合法，只能使用字母、数字、下划线，不要带lib和.so"
        if (!isValidStubClassName(stubClassName)) return "自定义壳类名不合法。需包含包名与类名（如 top.nkbe.safe.StubApp），不能以数字开头。"
        if (ShellClassNamePolicy.containsArt(stubClassName)) return "壳类名不能包含 art，请改用 nkbe 或其他名称"
        if (!isValidSavePath(savePath)) return "文件保存路径无效或不可写"
        if (shizukuSilentInstall && !autoSign) return "Shizuku 静默安装需要先开启自动签名"
        if (useCustomJks && !isValidJksSettings(jksPath, jksStorePass, jksAlias, jksKeyPass)) return "JKS 证书配置无效或未填完整"

        saveArkSettings(
            soName, stubClassName, savePath, autoSign, emulatorCompatibility, rootServiceCompatibility, stringEncryption, shizukuSilentInstall,
            fake360Type, useCustomJks, jksPath, jksStorePass, jksAlias, jksKeyPass,
        )
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        return null
    }

    // ── Validation ──
    private fun isValidSoName(soName: String?): Boolean {
        val name = soName?.trim() ?: return false
        return name.isNotEmpty() && !name.startsWith("lib") && !name.endsWith(".so") && name.matches(Regex("[A-Za-z0-9_]+"))
    }

    private fun isValidSavePath(savePath: String?): Boolean {
        val path = savePath?.trim() ?: return false
        if (path.isEmpty()) return false
        val dir = File(path)
        return if (dir.exists()) dir.isDirectory && dir.canWrite()
        else dir.mkdirs() && dir.isDirectory && dir.canWrite()
    }

    private fun isValidStubClassName(className: String?): Boolean {
        val name = className?.trim() ?: return false
        if (name.isEmpty() || name.startsWith(".") || name.endsWith(".") || name.contains("..")) return false
        val parts = name.split(".")
        if (parts.size < 2) return false
        for (part in parts) {
            if (part.isEmpty()) return false
            val first = part[0]
            if (first.isDigit() || !first.isJavaIdentifierStart()) return false
            for (i in 1 until part.length) {
                if (!part[i].isJavaIdentifierPart()) return false
            }
        }
        return true
    }

    private fun isValidJksSettings(jksPath: String?, storePass: String?, alias: String?, keyPass: String?): Boolean {
        if (jksPath.isNullOrBlank()) { Toast.makeText(this, "JKS证书路径不能为空", Toast.LENGTH_LONG).show(); return false }
        val jksFile = File(jksPath.trim())
        if (!jksFile.exists() || !jksFile.isFile) { Toast.makeText(this, "JKS证书文件不存在", Toast.LENGTH_LONG).show(); return false }
        if (storePass.isNullOrBlank()) { Toast.makeText(this, "证书密码不能为空", Toast.LENGTH_LONG).show(); return false }
        if (alias.isNullOrBlank()) { Toast.makeText(this, "别名不能为空", Toast.LENGTH_LONG).show(); return false }
        if (keyPass.isNullOrBlank()) { Toast.makeText(this, "别名密码不能为空", Toast.LENGTH_LONG).show(); return false }
        return true
    }

    // ── Derived settings ──
    private val validSoName: String
        get() {
            try {
                val settings = readArkSettings()
                if (isValidSoName(settings.soName)) return settings.soName.trim()
            } catch (e: Exception) {
                appendLogOnUi("读取so名称设置失败，使用默认名称：" + e.message)
            }
            return DEFAULT_SO_NAME
        }

    private val validSoFileName: String get() = "lib$validSoName.so"

    private val validStubClassName: String
        get() {
            try {
                val settings = readArkSettings()
                if (settings.stubClassName.isNotEmpty()
                    && isValidStubClassName(settings.stubClassName)
                    && !ShellClassNamePolicy.containsArt(settings.stubClassName)
                ) return settings.stubClassName.trim()
            } catch (_: Exception) {}
            return DEFAULT_STUB_CLASS_NAME
        }

    // ── Logging ──
    private fun appendLog(text: String) {
        if (::uiController.isInitialized) uiController.appendLog(text)
    }

    private fun appendLogOnUi(text: String) {
        println("[log] $text")
        runOnUiThread { appendLog(text) }
    }

    // ── APK selector ──
    private fun openApkSelector() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.android.package-archive"
            },
            REQ_SELECT_APK,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SELECT_APK && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (uri == null) { appendLog("选择文件失败：Uri为空"); return }
            handleSelectedApk(uri)
        }
    }

    // ═══════════════════════════════════════════════════════
    // APK PROCESSING PIPELINE
    // ═══════════════════════════════════════════════════════

    private fun handleSelectedApk(uri: Uri) {
        uiController.setSelectEnabled(false)
        Thread {
            val workDir = workDir
            try {
                appendLogOnUi("开始处理 APK")
                if (!workDir.exists() && !workDir.mkdirs()) {
                    throw RuntimeException("创建临时目录失败：" + workDir.absolutePath)
                }
                appendLogOnUi("临时目录：" + workDir.absolutePath)

                var originalApkName = getFileNameFromUri(uri)
                originalApkName = ApkValidator.sanitizeApkFileName(originalApkName)
                appendLogOnUi("目标 APK 名称：$originalApkName")
                val copiedApk = File(workDir, "待加固.apk")
                appendLogOnUi("开始复制目标 APK 到工作目录")
                copyUriToFile(uri, copiedApk)
                appendLogOnUi("APK 复制完成：${copiedApk.absolutePath}（${copiedApk.length()} 字节）")
                appendLogOnUi("开始校验 APK 结构")
                ApkValidator.validate(copiedApk)
                appendLogOnUi("APK 结构校验通过")

                val appName = readApplicationName(copiedApk)
                appendLogOnUi("原始入口：" + appName)
                val settings = readArkSettings()
                val preservedRootDexEntries = resolveRootServiceCompatibilityDexes(
                    copiedApk,
                    settings.rootServiceCompatibility,
                )
                val stringEncryptionInput = if (settings.stringEncryption) {
                    prepareStringEncryptionInput(copiedApk, workDir, preservedRootDexEntries)
                } else null

                appendLogOnUi("开始生成壳 DEX")
                val useApplicationEntry = settings.emulatorCompatibility || readTargetMinSdk(copiedApk) < 28
                if (!settings.emulatorCompatibility && useApplicationEntry) {
                    appendLogOnUi("目标 APK 最低版本低于 Android 9，自动启用 android:name 兼容入口")
                }
                val shellDex = generateShellDex(workDir, useApplicationEntry)
                appendLogOnUi("壳 DEX 生成完成：${shellDex.absolutePath}（${shellDex.length()} 字节）")
                val signHash64 = getSignHash64ForShell()
                appendLogOnUi("开始加密原始 DEX")
                buildEncryptedShellDex(
                    stringEncryptionInput?.apk ?: copiedApk, shellDex, appName, signHash64,
                    preservedRootDexEntries.toTypedArray(),
                )
                appendLogOnUi("加密完成：" + shellDex.absolutePath + "（" + shellDex.length() + " 字节）")

                appendLogOnUi("开始提取壳 SO")
                extractStubSoByTargetAbi(copiedApk, workDir)
                appendLogOnUi("壳 SO 提取完成")
                appendLogOnUi("开始改写 AndroidManifest.xml")
                val newManifest = modifyAndroidManifest(copiedApk, workDir, appName, useApplicationEntry)
                appendLogOnUi("Manifest 改写完成：${newManifest.absolutePath}（${newManifest.length()} 字节）")
                appendLogOnUi("开始重建加固 APK")
                var protectedApk = rebuildProtectedApk(
                    copiedApk, workDir, originalApkName, preservedRootDexEntries, stringEncryptionInput?.poolFile,
                )
                appendLogOnUi("重建 APK 完成：${protectedApk.absolutePath}（${protectedApk.length()} 字节）")
                verifyRootServiceCompatibilityOutput(
                    copiedApk,
                    protectedApk,
                    preservedRootDexEntries,
                )

                appendLogOnUi("开始进行 ZIPALIGN")
                protectedApk = zipAlignApk(protectedApk)
                appendLogOnUi("ZIPALIGN 完成：${protectedApk.absolutePath}（${protectedApk.length()} 字节）")

                if (settings.autoSign) {
                    appendLogOnUi("检测到已开启自动签名")
                    protectedApk = if (settings.useCustomJks) {
                        ApkSignUtil.signApk(
                            this, protectedApk, File(settings.jksPath),
                            settings.jksStorePass, settings.jksAlias, settings.jksKeyPass, ::appendLogOnUi,
                        )
                    } else {
                        ApkSignUtil.signApk(
                            this,
                            protectedApk,
                            null,
                            null,
                            null,
                            null,
                            ::appendLogOnUi,
                        )
                    }
                    appendLogOnUi("APK 签名完成")
                } else {
                    appendLogOnUi("未开启自动签名，跳过签名")
                }

                appendLogOnUi("加固包输出：" + protectedApk.absolutePath)
                appendLogOnUi("----------->>>加固完成<<<-----------")

                val finalApk = protectedApk
                if (settings.autoSign && settings.shizukuSilentInstall) {
                    appendLogOnUi("检测到已开启 Shizuku 静默安装")
                    val installResult = installApkSilentlyWithShizuku(finalApk)
                    if (installResult.isSuccess) {
                        appendLogOnUi("Shizuku 静默安装成功：" + installResult.getOrDefault("Success"))
                        runOnUiThread {
                            uiController.showDialog(
                                NeoArtDialogState(
                                    title = "加固并安装完成",
                                    message = "修补后的 APK 已完成静默安装。",
                                    confirmText = "知道了",
                                    cancelable = false,
                                    onConfirm = {},
                                )
                            )
                        }
                    } else {
                        appendLogOnUi(
                            "Shizuku 静默安装失败，回退普通安装：" +
                                (installResult.exceptionOrNull()?.message ?: "未知错误")
                        )
                        runOnUiThread { showInstallDialog(finalApk) }
                    }
                } else {
                    runOnUiThread { showInstallDialog(finalApk) }
                }
            } catch (e: Exception) {
                appendLogOnUi("处理失败：" + e.message)
            } finally {
                cleanTempFiles(workDir)
                runOnUiThread { uiController.setSelectEnabled(true) }
            }
        }.start()
    }

    // ── Sign hash for shell binding ──
    private fun getSignHash64ForShell(): ByteArray? {
        try {
            val settings = readArkSettings()
            if (!settings.autoSign) {
                appendLogOnUi("未开启自动签名，签名证书绑定值为空")
                return null
            }
            var sha256: String?
            if (settings.useCustomJks) {
                appendLogOnUi("使用自定义证书获取指纹")
                if (!isValidJksSettings(settings.jksPath, settings.jksStorePass, settings.jksAlias, settings.jksKeyPass))
                    return null
                sha256 = JksSha256Util.getJksSha256FromFile(
                    File(settings.jksPath), settings.jksStorePass, settings.jksAlias, settings.jksKeyPass, cacheDir,
                )
            } else {
                appendLogOnUi("使用内置 npatch.key 获取指纹")
                sha256 = JksSha256Util.getNpatchKeySha256(this)
            }
            if (sha256 == null) { appendLogOnUi("证书指纹获取失败：结果为空"); return null }
            sha256 = sha256.trim().lowercase(Locale.ROOT)
            if (sha256.length != 64) { appendLogOnUi("证书指纹长度异常：" + sha256.length); return null }
            appendLogOnUi("证书指纹获取成功")
            appendLogOnUi("证书指纹：" + sha256)
            return sha256.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            appendLogOnUi("证书指纹获取失败：" + e.message)
            return null
        }
    }

    // ── Shell DEX generation ──
    private fun resolveRootServiceCompatibilityDexes(
        apkFile: File,
        enabled: Boolean,
    ): List<String> {
        if (!enabled) return emptyList()

        appendLogOnUi("已开启 libsu RootService 兼容模式，正在扫描 DEX")
        val result = RootServiceDexDetector.scan(apkFile)
        if (result.rootServiceDexEntries.isEmpty()) {
            appendLogOnUi("未发现 libsu RootService，继续全量加密")
            return emptyList()
        }
        if (result.rootServiceDexEntries.size == result.allDexEntries.size) {
            appendLogOnUi("RootService 覆盖全部 DEX，无法安全保留兼容载荷，继续全量加密")
            return emptyList()
        }

        appendLogOnUi(
            "已保留 libsu RootService 所在 DEX（未加密）：" +
                result.rootServiceDexEntries.joinToString(),
        )
        return result.rootServiceDexEntries
    }

    @Throws(Exception::class)
    private fun prepareStringEncryptionInput(
        sourceApk: File,
        workDir: File,
        preservedDexEntries: List<String>,
    ): StringEncryptionInput? {
        appendLogOnUi("已开启字符串加密，开始生成 DEX 重写输入")
        val inputApk = File(workDir, "string_encryption_input.apk")
        val dexDir = File(workDir, "string_encryption_dex")
        deleteFileQuietly(inputApk); deleteDirQuietly(dexDir); dexDir.mkdirs()
        val pool = StringEncryptionRewriter.StringPoolBuilder()
        var rewritten = 0
        ZipFile(sourceApk).use { zip ->
            ZipOutputStream(FileOutputStream(inputApk)).use { output ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name !in preservedDexEntries && name.matches(Regex("classes([2-9][0-9]*)?\\.dex"))) {
                        val sourceDex = File(dexDir, "$name.in")
                        val rewrittenDex = File(dexDir, name)
                        zip.getInputStream(entry).use { input -> FileOutputStream(sourceDex).use { input.copyTo(it) } }
                        val result = StringEncryptionRewriter.rewrite(sourceDex, rewrittenDex, validStubClassName, pool)
                        rewritten += result.rewrittenStrings
                        FileInputStream(rewrittenDex).use { addZipEntryStream(output, name, it, null) }
                    } else if (entry.isDirectory) addDirectoryZipEntry(output, name, entry)
                    else zip.getInputStream(entry).use { addZipEntryStream(output, name, it, entry) }
                }
            }
        }
        if (rewritten == 0 || pool.isEmpty) {
            deleteFileQuietly(inputApk)
            appendLogOnUi("未找到可安全加密的字符串，继续使用原始 DEX")
            return null
        }
        val poolFile = File(workDir, "top_strings.bin")
        StringEncryptionRewriter.writeEncryptedStringPool(poolFile, pool.buildEncryptedStringPool())
        appendLogOnUi("字符串重写完成：$rewritten 条，字串池：${poolFile.length()} 字节")
        return StringEncryptionInput(inputApk, poolFile, rewritten)
    }
    @Throws(Exception::class)
    private fun verifyRootServiceCompatibilityOutput(
        sourceApk: File,
        protectedApk: File,
        preservedDexEntries: List<String>,
    ) {
        if (preservedDexEntries.isEmpty()) return

        ZipFile(sourceApk).use { sourceZip ->
            ZipFile(protectedApk).use { protectedZip ->
                preservedDexEntries.forEachIndexed { index, sourceDexName ->
                    val outputDexName = "classes${index + 2}.dex"
                    val sourceEntry = sourceZip.getEntry(sourceDexName)
                        ?: throw RuntimeException("RootService 原始 DEX 不存在：$sourceDexName")
                    val outputEntry = protectedZip.getEntry(outputDexName)
                        ?: throw RuntimeException("RootService 兼容 DEX 未写入输出 APK：$outputDexName")

                    val matches = sourceZip.getInputStream(sourceEntry).use { sourceInput ->
                        protectedZip.getInputStream(outputEntry).use { outputInput ->
                            streamsHaveSameBytes(sourceInput, outputInput)
                        }
                    }
                    if (!matches) {
                        throw RuntimeException(
                            "RootService 兼容 DEX 内容不一致：$sourceDexName -> $outputDexName",
                        )
                    }
                    appendLogOnUi("已验证 RootService 兼容 DEX：$sourceDexName -> $outputDexName")
                }
            }
        }
    }

    private fun streamsHaveSameBytes(first: InputStream, second: InputStream): Boolean {
        val firstBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val secondBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val firstCount = first.read(firstBuffer)
            val secondCount = second.read(secondBuffer)
            if (firstCount != secondCount) return false
            if (firstCount == -1) return true
            for (index in 0 until firstCount) {
                if (firstBuffer[index] != secondBuffer[index]) return false
            }
        }
    }

    @Throws(Exception::class)
    private fun generateShellDex(outputDir: File, useApplicationEntry: Boolean): File {
        if (!outputDir.exists() && !outputDir.mkdirs())
            throw RuntimeException("创建输出目录失败：" + outputDir.absolutePath)

        val outputDex = File(outputDir, "classes.dex")
        val customStubClassName = validStubClassName
        val stubClass = "L" + customStubClassName.replace('.', '/') + ";"
        val factoryClass = "L" + "${customStubClassName}Factory".replace('.', '/') + ";"
        val applicationClass = "Landroid/app/Application;"
        val appComponentFactoryClass = "Landroid/app/AppComponentFactory;"
        val classLoaderClass = "Ljava/lang/ClassLoader;"
        val stringClass = "Ljava/lang/String;"
        val contextClass = "Landroid/content/Context;"

        val dexPool = DexPool(Opcodes.getDefault())

        // ── Obfuscated <clinit> ──
        // Junk arithmetic acts as opaque predicate: (13*7)%13 = 0, always evaluates to 0
        // but appears as a dynamic condition to naive decompilers.
        val clinitInstructions = mutableListOf(
            // Junk code: compute value that is always 0
            ImmutableInstruction21s(Opcode.CONST_16, 2, 13),       // v2 = 13
            ImmutableInstruction22b(Opcode.MUL_INT_LIT8, 2, 2, 7), // v2 = 13 * 7 = 91
            ImmutableInstruction22b(Opcode.REM_INT_LIT8, 2, 2, 13),// v2 = 91 % 13 = 0 (always)
            ImmutableInstruction11n(Opcode.CONST_4, 3, 0x0),       // v3 = 0 (dead reg fill)
            // Real code
            ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("top")),
            ImmutableInstruction21c(Opcode.CONST_STRING, 1, ImmutableStringReference(customStubClassName)),
            ImmutableInstruction35c(
                Opcode.INVOKE_STATIC, 2, 0, 1, 0, 0, 0,
                ImmutableMethodReference("Ljava/lang/System;", "setProperty",
                    listOf("Ljava/lang/String;", "Ljava/lang/String;"), "Ljava/lang/String;"),
            ),
            ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
            ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(validSoName)),
            ImmutableInstruction35c(
                Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                ImmutableMethodReference("Ljava/lang/System;", "loadLibrary",
                    listOf("Ljava/lang/String;"), "V"),
            ),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        )

        val clinitMethod = ImmutableMethod(
            stubClass, "<clinit>", emptyList(), "V",
            AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(), null,
            ImmutableMethodImplementation(4, clinitInstructions, emptyList(), emptyList()),
        )

        val initMethod = ImmutableMethod(
            stubClass, "<init>", emptyList(), "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(), null,
            ImmutableMethodImplementation(
                1,
                listOf(
                    ImmutableInstruction35c(
                        Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                        ImmutableMethodReference(applicationClass, "<init>", emptyList(), "V"),
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID),
                ), emptyList(), emptyList(),
            ),
        )

        // ── Fake method: never called, adds noise to class ──
        val fakeMethod = ImmutableMethod(
            stubClass, "a",
            listOf(ImmutableMethodParameter("I", emptySet(), null)), "I",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(), null,
            ImmutableMethodImplementation(
                3,
                listOf(
                    ImmutableInstruction12x(Opcode.MOVE, 0, 2),                 // v0 = p0
                    ImmutableInstruction22b(Opcode.MUL_INT_LIT8, 1, 0, 7),      // v1 = v0 * 7
                    ImmutableInstruction22b(Opcode.ADD_INT_LIT8, 1, 1, 13),     // v1 = v1 + 13
                    ImmutableInstruction22b(Opcode.REM_INT_LIT8, 1, 1, 11),     // v1 = v1 % 11
                    ImmutableInstruction11n(Opcode.CONST_4, 0, 0x3),            // v0 = 3
                    ImmutableInstruction23x(Opcode.ADD_INT, 0, 1, 0),           // v0 = v1 + 3
                    ImmutableInstruction11x(Opcode.RETURN, 0),                  // return v0
                ), emptyList(), emptyList(),
            ),
        )

        val attachMethod = ImmutableMethod(
            stubClass, "attachBaseContext",
            listOf(ImmutableMethodParameter(contextClass, emptySet(), null)), "V",
            AccessFlags.PROTECTED.value or AccessFlags.NATIVE.value,
            emptySet(), null, null,
        )

        val decodeStringMethod = ImmutableMethod(
            stubClass, "decodeString",
            listOf(ImmutableMethodParameter("I", emptySet(), null)), stringClass,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.NATIVE.value,
            emptySet(), null, null,
        )

        val classDef = ImmutableClassDef(
            stubClass, AccessFlags.PUBLIC.value, applicationClass,
            emptyList(), "StubApp.java", emptySet(), emptyList(),
            listOf(clinitMethod, initMethod, fakeMethod, decodeStringMethod, attachMethod),
        )

        dexPool.internClass(classDef)
        if (!useApplicationEntry) {
            val factoryInitMethod = ImmutableMethod(
                factoryClass, "<init>", emptyList(), "V",
                AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
                emptySet(), null,
                ImmutableMethodImplementation(
                    1,
                    listOf(
                        ImmutableInstruction35c(
                            Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                            ImmutableMethodReference(appComponentFactoryClass, "<init>", emptyList(), "V"),
                        ),
                        ImmutableInstruction10x(Opcode.RETURN_VOID),
                    ), emptyList(), emptyList(),
                ),
            )
            val factoryInstantiateApplication = ImmutableMethod(
                factoryClass,
                "instantiateApplication",
                listOf(
                    ImmutableMethodParameter(classLoaderClass, emptySet(), null),
                    ImmutableMethodParameter(stringClass, emptySet(), null),
                ),
                applicationClass,
                AccessFlags.PUBLIC.value,
                emptySet(), null,
                ImmutableMethodImplementation(
                    4,
                    listOf(
                        ImmutableInstruction21c(
                            Opcode.NEW_INSTANCE, 0, ImmutableTypeReference(stubClass),
                        ),
                        ImmutableInstruction35c(
                            Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                            ImmutableMethodReference(stubClass, "<init>", emptyList(), "V"),
                        ),
                        ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0),
                    ), emptyList(), emptyList(),
                ),
            )
            dexPool.internClass(
                ImmutableClassDef(
                    factoryClass,
                    AccessFlags.PUBLIC.value,
                    appComponentFactoryClass,
                    emptyList(), "ShellFactory.java", emptySet(), emptyList(),
                    listOf(factoryInitMethod, factoryInstantiateApplication),
                ),
            )
        }
        dexPool.writeTo(FileDataStore(outputDex))
        return outputDex
    }

    // ── Stub SO extraction ──
    @Throws(Exception::class)
    private fun extractStubSoByTargetAbi(apkFile: File, workDir: File) {
        appendLogOnUi("开始读取目标 APK ABI")
        val selfAbiList = selfApkStubAbiList
        if (selfAbiList.isEmpty()) throw RuntimeException("assets/lib 下没有可用 ABI")

        val targetAbiList = readApkAbiList(apkFile)
        val finalAbiList = ArrayList<String>()

        if (targetAbiList.isEmpty()) {
            appendLogOnUi("目标 APK 没有 lib 目录，使用 assets/lib 下全部 ABI")
            for (abi in selfAbiList) {
                if (abi == "armeabi") { appendLogOnUi("跳过 armeabi"); continue }
                finalAbiList.add(abi)
            }
        } else {
            appendLogOnUi("目标 APK ABI：" + targetAbiList.toString())
            for (abi in targetAbiList) {
                if (abi == "armeabi") { appendLogOnUi("跳过目标 armeabi"); continue }
                if (abi !in selfAbiList) { appendLogOnUi("不支持该 ABI，跳过：" + abi); continue }
                finalAbiList.add(abi)
            }
        }
        if (finalAbiList.isEmpty()) throw RuntimeException("没有匹配到可解压的 ABI")
        appendLogOnUi("最终使用 ABI：" + finalAbiList.toString())

        val soFileName = validSoFileName
        for (abi in finalAbiList) {
            val outFile = File(workDir, "lib/$abi/$soFileName")
            val parent = outFile.parentFile ?: continue
            if (!parent.exists() && !parent.mkdirs())
                throw RuntimeException("创建 so 输出目录失败：" + parent.absolutePath)
            copySelfApkStubSoToFile(abi, outFile)
            appendLogOnUi("已提取壳 SO：${outFile.absolutePath}（${outFile.length()} 字节）")
        }
    }

    private fun readApkAbiList(apkFile: File): ArrayList<String> {
        val abiList = ArrayList<String>()
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (!name.startsWith("lib/")) continue
                val parts = name.split("/")
                if (parts.size < 3) continue
                val abi = parts[1]
                if (abi !in abiList) abiList.add(abi)
            }
        }
        return abiList
    }

    private val selfApkStubAbiList: ArrayList<String>
        @Throws(Exception::class)
        get() {
            val abiList = ArrayList<String>()
            ZipFile(applicationInfo.sourceDir).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (!name.startsWith("lib/")) continue
                    val parts = name.split("/")
                    if (parts.size != 3) continue
                    val abi = parts[1]
                    if (abi == "armeabi") continue
                    if (parts[2] != "libArkStub.so") continue
                    if (abi !in abiList) abiList.add(abi)
                }
            }
            return abiList
        }

    @Throws(Exception::class)
    private fun copySelfApkStubSoToFile(abi: String, outFile: File) {
        val zipPath = "lib/$abi/libArkStub.so"
        ZipFile(applicationInfo.sourceDir).use { zip ->
            val entry = zip.getEntry(zipPath) ?: throw RuntimeException("自身 APK 中未找到：$zipPath")
            val parent = outFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw RuntimeException("创建 so 输出目录失败：" + parent.absolutePath)
            zip.getInputStream(entry).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
    }

    // ── Manifest modification ──
    @Throws(Exception::class)
    private fun modifyAndroidManifest(
        apkFile: File,
        workDir: File,
        originalApplicationName: String,
        useApplicationEntry: Boolean,
    ): File {
        appendLogOnUi("开始处理 AndroidManifest.xml")
        val manifestAxml = File(workDir, "AndroidManifest_origin.xml")
        val manifestXml = File(workDir, "AndroidManifest_decode.xml")
        val manifestNewXml = File(workDir, "AndroidManifest_modify.xml")
        val manifestNewAxml = File(workDir, "AndroidManifest.xml")

        try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml")
                    ?: throw RuntimeException("APK 中未找到 AndroidManifest.xml")
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(manifestAxml).use { output -> input.copyTo(output) }
                }
            }
            appendLogOnUi("已提取 AndroidManifest.xml")

            Xml2AxmlTool.decode(manifestAxml.absolutePath, manifestXml.absolutePath)
            appendLogOnUi("Manifest 解码完成")

            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(manifestXml)
            val manifest = document.documentElement
                ?: throw RuntimeException("Manifest XML 结构异常")

            var application: Element? = null
            for (i in 0 until manifest.childNodes.length) {
                val item = manifest.childNodes.item(i)
                if (item is Element && item.nodeName == "application") {
                    application = item; break
                }
            }
            if (application == null) throw RuntimeException("Manifest 中未找到 application 标签")

            rewriteApplicationAttributes(application, originalApplicationName, useApplicationEntry)
            appendLogOnUi(
                if (useApplicationEntry) {
                    "Manifest 已使用 android:name 兼容入口：$validStubClassName"
                } else {
                    "Manifest 已使用 appComponentFactory 入口：${validStubClassName}Factory"
                },
            )

            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "utf-8")
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            }
            transformer.transform(DOMSource(document), StreamResult(manifestNewXml))
            Xml2AxmlTool.encode2(this, manifestNewXml.absolutePath, manifestNewAxml.absolutePath)
            appendLogOnUi("Manifest 重新编码完成")
            return manifestNewAxml
        } finally {
            deleteFileQuietly(manifestAxml)
            deleteFileQuietly(manifestXml)
            deleteFileQuietly(manifestNewXml)
        }
    }

    private fun rewriteApplicationAttributes(
        application: Element,
        originalApplicationName: String,
        useApplicationEntry: Boolean,
    ) {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val oldAttrs = ArrayList<Attr>()

        val attrMap = application.attributes
        for (i in 0 until attrMap.length) {
            val node = attrMap.item(i)
            if (node is Attr) {
                val name = node.name
                if (name == "android:name" || name == "android:extractNativeLibs" ||
                    name == "android:appComponentFactory" || name == "name" ||
                    name == "extractNativeLibs" || name == "appComponentFactory") continue
                oldAttrs.add(node)
            }
        }

        while (application.attributes.length > 0) {
            application.removeAttributeNode(application.attributes.item(0) as Attr)
        }

        for (attr in oldAttrs) {
            val attrName = attr.name
            val attrValue = attr.value
            if (attr.namespaceURI != null && attr.namespaceURI.isNotEmpty()) {
                application.setAttributeNS(attr.namespaceURI, attrName, attrValue)
            } else {
                application.setAttribute(attrName, attrValue)
            }
        }

        if (useApplicationEntry) {
            application.setAttributeNS(androidNs, "android:name", validStubClassName)
        } else {
            application.setAttributeNS(androidNs, "android:name", originalApplicationName)
            application.setAttributeNS(
                androidNs,
                "android:appComponentFactory",
                "${validStubClassName}Factory",
            )
        }
        application.setAttributeNS(androidNs, "android:extractNativeLibs", "true")
    }

    // ── Repackaging ──
    @Throws(Exception::class)
    private fun rebuildProtectedApk(
        apkFile: File,
        workDir: File,
        originalApkName: String?,
        preservedRootDexEntries: List<String>,
        stringPoolFile: File?,
    ): File {
        appendLogOnUi("开始重打包 APK")

        val newClassesDex = File(workDir, "classes.dex")
        val newManifest = File(workDir, "AndroidManifest.xml")
        val libDir = File(workDir, "lib")

        if (!newClassesDex.exists()) throw RuntimeException("未找到新的 classes.dex")
        if (!newManifest.exists()) throw RuntimeException("未找到修改后的 AndroidManifest.xml")

        val skipNames = HashSet<String>()
        val repackSettings = readArkSettings()
        val fake360AssetName = getFake360AssetName(repackSettings.fake360Type)

        ZipFile(apkFile).use { checkZip ->
            var i = 1
            while (true) {
                val dexName = if (i == 1) "classes.dex" else "classes$i.dex"
                if (checkZip.getEntry(dexName) == null) break
                skipNames.add(dexName)
                i++
            }
        }

        skipNames.add("AndroidManifest.xml")
        if (fake360AssetName != null) skipNames.add(fake360AssetName)
        if (stringPoolFile != null) skipNames.add("assets/top_strings.bin")
        if (libDir.exists() && libDir.isDirectory) collectLibSkipNames(libDir, libDir, skipNames)
        appendLogOnUi("重打包时跳过条目数：" + skipNames.size)

        val outApk = File(finalOutputDir, buildProtectedApkName(originalApkName))
        appendLogOnUi("重打包输出路径：" + outApk.absolutePath)

        ZipFile(apkFile).use { zipFile ->
            ZipOutputStream(FileOutputStream(outApk)).use { zos ->
                zos.setLevel(9)
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val oldEntry = entries.nextElement()
                    val name = oldEntry.name
                    if (name in skipNames) continue
                    if (oldEntry.isDirectory) {
                        addDirectoryZipEntry(zos, name, oldEntry)
                        continue
                    }
                    zipFile.getInputStream(oldEntry).use { input ->
                        addZipEntryStream(zos, name, input, oldEntry)
                    }
                }

                FileInputStream(newClassesDex).use { input ->
                    addZipEntryStream(zos, "classes.dex", input, null)
                }
                appendLogOnUi("已写入新 classes.dex")

                var outputDexIndex = 2
                for (sourceDexName in preservedRootDexEntries) {
                    val sourceDex = zipFile.getEntry(sourceDexName)
                        ?: throw RuntimeException("RootService 兼容 DEX 不存在：$sourceDexName")
                    val outputDexName = "classes${outputDexIndex++}.dex"
                    zipFile.getInputStream(sourceDex).use { input ->
                        addZipEntryStream(zos, outputDexName, input, sourceDex)
                    }
                    appendLogOnUi("已写入 RootService 兼容 DEX：$sourceDexName -> $outputDexName")
                }

                FileInputStream(newManifest).use { input ->
                    addZipEntryStream(zos, "AndroidManifest.xml", input, null)
                }
                appendLogOnUi("已写入新 AndroidManifest.xml")

                if (libDir.exists() && libDir.isDirectory) {
                    addLibDirToZipStream(zos, libDir, libDir)
                }

                if (stringPoolFile != null) {
                    FileInputStream(stringPoolFile).use { addZipEntryStream(zos, "assets/top_strings.bin", it, null) }
                    appendLogOnUi("已写入加密字符串池")
                }

                if (fake360AssetName != null) {
                    val marker = "NeoArk fake 360 marker for tool identification only;\n" +
                        "https://github.com/HSSkyBoy/Art-Jiagu\n"
                    addZipEntryStream(
                        zos, fake360AssetName,
                        marker.toByteArray(Charsets.UTF_8).inputStream(), null,
                    )
                    appendLogOnUi("已添加 360 ${getFake360TypeLabel(repackSettings.fake360Type)}识别特征：$fake360AssetName")
                }
                zos.finish()
            }
        }

        appendLogOnUi("重打包完成：" + outApk.absolutePath)
        return outApk
    }

    @Throws(Exception::class)
    private fun addZipEntryStream(zos: ZipOutputStream, name: String, input: InputStream, oldEntry: ZipEntry?) {
        var tempFile: File? = null
        try {
            val newEntry = ZipEntry(name)
            if (oldEntry != null) {
                newEntry.time = oldEntry.time
                newEntry.comment = oldEntry.comment
                newEntry.extra = oldEntry.extra
            }

            if (oldEntry != null && shouldStoreEntry(name, oldEntry)) {
                tempFile = File.createTempFile("ark_zip_", ".tmp", cacheDir)
                val crc32 = CRC32()
                var size = 0L

                FileOutputStream(tempFile).use { tempOut ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        tempOut.write(buffer, 0, len)
                        crc32.update(buffer, 0, len)
                        size += len
                    }
                }

                newEntry.method = ZipEntry.STORED
                newEntry.size = size
                newEntry.compressedSize = size
                newEntry.crc = crc32.value

                zos.putNextEntry(newEntry)
                FileInputStream(tempFile).use { it.copyTo(zos) }
                zos.closeEntry()
            } else {
                newEntry.method = ZipEntry.DEFLATED
                zos.putNextEntry(newEntry)
                input.copyTo(zos)
                zos.closeEntry()
            }
        } finally {
            try { input.close() } catch (_: Exception) {}
            tempFile?.delete()
        }
    }

    @Throws(Exception::class)
    private fun addDirectoryZipEntry(zos: ZipOutputStream, name: String, oldEntry: ZipEntry) {
        val dirName = if (name.endsWith("/")) name else "$name/"
        val newEntry = ZipEntry(dirName).apply {
            time = oldEntry.time
            comment = oldEntry.comment
            extra = oldEntry.extra
        }
        zos.putNextEntry(newEntry)
        zos.closeEntry()
    }

    @Throws(Exception::class)
    private fun addLibDirToZipStream(zos: ZipOutputStream, rootDir: File, currentDir: File) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                addLibDirToZipStream(zos, rootDir, file)
            } else {
                val relativePath = file.relativeTo(rootDir).path.replace("\\", "/")
                FileInputStream(file).use { input ->
                    addZipEntryStream(zos, "lib/$relativePath", input, null)
                }
            }
        }
    }

    private fun collectLibSkipNames(rootLibDir: File, current: File, skipNames: MutableSet<String>) {
        val files = current.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                collectLibSkipNames(rootLibDir, file, skipNames)
            } else {
                val relative = file.relativeTo(rootLibDir).path.replace("\\", "/")
                skipNames.add("lib/$relative")
            }
        }
    }

    private val finalOutputDir: File
        get() {
            try {
                val settings = readArkSettings()
                if (settings.savePath.isNotBlank()) {
                    val saveDir = File(settings.savePath.trim())
                    if (!saveDir.exists()) saveDir.mkdirs()
                    if (saveDir.exists() && saveDir.isDirectory && saveDir.canWrite()) return saveDir
                }
            } catch (e: Exception) {
                appendLogOnUi("读取输出目录设置失败，使用默认目录：" + e.message)
            }
            return workDir
        }

    private fun buildProtectedApkName(originalName: String?): String {
        val name = originalName?.trim().orEmpty()
        if (name.isEmpty()) return "已加固.apk"
        return if (name.lowercase(Locale.ROOT).endsWith(".apk"))
            name.removeSuffix(".apk") + "(已加固).apk"
        else name + "(已加固).apk"
    }

    // ── APK install ──
    private fun installApk(apkFile: File) {
        if (!apkFile.exists()) { Toast.makeText(this, "APK文件不存在", Toast.LENGTH_SHORT).show(); return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            uiController.showDialog(
                NeoArtDialogState(
                    title = "需要安装权限",
                    message = "请先允许本应用安装未知来源应用",
                    confirmText = "去授权",
                    dismissText = "取消",
                    onConfirm = {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    },
                )
            )
            return
        }
        doInstallApk(apkFile)
    }

    private fun showInstallDialog(apkFile: File) {
        uiController.showDialog(
            NeoArtDialogState(
                title = "加固完成",
                message = "APK 已加固完成，是否立即安装？",
                confirmText = "安装",
                dismissText = "取消",
                cancelable = false,
                onConfirm = { installApk(apkFile) },
            )
        )
    }

    private fun doInstallApk(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        startActivity(intent)
    }

    private fun refreshShizukuStatus() {
        val (text, granted) = try {
            when {
                !Shizuku.pingBinder() -> "未连接或未启动" to false
                Shizuku.isPreV11() -> "版本过低，不支持授权" to false
                Shizuku.checkSelfPermission() == PERMISSION_GRANTED -> "已授权，可静默安装" to true
                else -> "已连接，等待授权" to false
            }
        } catch (_: Throwable) {
            "不可用" to false
        }
        runOnUiThread { uiController.updateShizukuStatus(text, granted) }
    }

    private fun requestShizukuAuthorization(): String? {
        return try {
            if (!Shizuku.pingBinder()) {
                appendLogOnUi("Shizuku 未连接，请先安装并启动 Shizuku")
                refreshShizukuStatus()
                "请先安装并启动 Shizuku"
            } else if (Shizuku.isPreV11()) {
                appendLogOnUi("Shizuku 版本过低，不支持当前授权流程")
                "Shizuku 版本过低"
            } else if (Shizuku.checkSelfPermission() == PERMISSION_GRANTED) {
                appendLogOnUi("Shizuku 已授权")
                refreshShizukuStatus()
                "Shizuku 已授权"
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                appendLogOnUi("Shizuku 权限此前被拒绝，请到 Shizuku 中重新授权")
                refreshShizukuStatus()
                "请到 Shizuku 应用中重新授权"
            } else {
                appendLogOnUi("正在请求 Shizuku 授权")
                Shizuku.requestPermission(REQ_SHIZUKU_PERMISSION)
                null
            }
        } catch (e: Throwable) {
            appendLogOnUi("Shizuku 授权请求失败：" + e.message)
            refreshShizukuStatus()
            "授权请求失败：${e.message}"
        }
    }

    private fun installApkSilentlyWithShizuku(apkFile: File): Result<String> {
        return runCatching {
            if (!apkFile.exists()) error("APK 文件不存在")
            if (!Shizuku.pingBinder()) error("Shizuku 未连接")
            if (Shizuku.checkSelfPermission() != PERMISSION_GRANTED) error("Shizuku 未授权")

            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }

            val command = arrayOf("/system/bin/sh", "-c", "pm install -r ${shellQuote(apkFile.absolutePath)}")
            appendLogOnUi("开始通过 Shizuku 执行静默安装：${apkFile.absolutePath}")
            val process = newProcessMethod.invoke(null, command, null, null) as Process
            val stdout = process.inputStream.use { InputStreamReader(it).readText() }.trim()
            val stderr = process.errorStream.use { InputStreamReader(it).readText() }.trim()
            val exitCode = process.waitFor()
            val summary = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString(" | ")
            appendLogOnUi("Shizuku 安装返回码：$exitCode")
            if (summary.isNotBlank()) appendLogOnUi("Shizuku 安装输出：$summary")
            if (exitCode != 0) error(summary.ifBlank { "pm install 失败，exit=$exitCode" })
            summary.ifBlank { "Success" }
        }
    }

    private fun shellQuote(text: String): String =
        "'" + text.replace("'", "'\\''") + "'"

    // ── Zip alignment ──
    @Throws(Exception::class)
    private fun zipAlignApk(inputApk: File): File {
        if (!inputApk.exists()) throw RuntimeException("待对齐 APK 不存在")
        val parentDir = inputApk.parentFile ?: throw RuntimeException("APK 所在目录不存在")

        val alignedApk = File(parentDir, inputApk.name + ".aligning")
        deleteFileQuietly(alignedApk)

        val success = ZipAlign.doZipAlign(inputApk.absolutePath, alignedApk.absolutePath, 4, true, true)
        if (!success || !alignedApk.exists()) throw RuntimeException("zipalign 对齐失败")

        val verified = ZipAlign.isZipAligned(alignedApk.absolutePath, 4, true)
        if (!verified) { deleteFileQuietly(alignedApk); throw RuntimeException("zipalign 校验失败") }
        if (!inputApk.delete()) { deleteFileQuietly(alignedApk); throw RuntimeException("删除原 APK 失败") }
        if (!alignedApk.renameTo(inputApk)) { deleteFileQuietly(alignedApk); throw RuntimeException("重命名对齐 APK 失败") }
        return inputApk
    }

    // ── File utilities ──
    private val workDir: File get() = File(Environment.getExternalStorageDirectory(), TEMP_DIR_NAME)

    @Throws(Exception::class)
    private fun copyUriToFile(uri: Uri, outFile: File) {
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        } ?: throw RuntimeException("无法打开输入文件")
    }

    private fun readTargetMinSdk(apkFile: File): Int {
        val info = packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_META_DATA,
        )
        return info?.applicationInfo?.minSdkVersion?.takeIf { it > 0 } ?: 1
    }

    private fun readApplicationName(apkFile: File): String {
        val info = packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA,
        ) ?: return "android.app.Application"
        val className = info.applicationInfo?.className
        return if (className.isNullOrBlank()) "android.app.Application" else className
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) result = cursor.getString(index)
                    }
                }
        } catch (_: Exception) {}

        if (result.isNullOrBlank()) {
            val path = uri.path
            if (path != null) {
                val index = path.lastIndexOf('/')
                if (index >= 0 && index < path.length - 1) result = path.substring(index + 1)
            }
        }
        return result
    }

    private fun shouldStoreEntry(name: String, oldEntry: ZipEntry): Boolean {
        if (oldEntry.method == ZipEntry.STORED) return true
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".arsc") || lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".mp3") ||
            lower.endsWith(".mp4") || lower.endsWith(".ogg") || lower.endsWith(".wav")
    }

    // ── Temp file cleanup ──
    private fun cleanTempFiles(workDir: File) {
        if (!workDir.exists()) return
        deleteFileQuietly(File(workDir, "待加固.apk"))
        deleteFileQuietly(File(workDir, "AndroidManifest.xml"))
        deleteFileQuietly(File(workDir, "AndroidManifest_origin.xml"))
        deleteFileQuietly(File(workDir, "AndroidManifest_decode.xml"))
        deleteFileQuietly(File(workDir, "AndroidManifest_modify.xml"))
        deleteFileQuietly(File(workDir, "classes.dex"))
        deleteDirQuietly(File(workDir, "lib"))
        appendLogOnUi("临时文件清理完成")
    }

    private fun deleteFileQuietly(file: File) {
        try { if (file.exists() && file.isFile) file.delete() } catch (_: Exception) {}
    }

    private fun deleteDirQuietly(dir: File) {
        if (!dir.exists()) return
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) deleteDirQuietly(file) else deleteFileQuietly(file)
            }
            dir.delete()
        } catch (_: Exception) {}
    }

    private fun getFake360AssetName(type: Int): String? = when (type) {
        FAKE_360_NORMAL -> "assets/libjiagu.so"
        FAKE_360_PAID -> "assets/libjiagu_mips.a"
        FAKE_360_ENTERPRISE -> "assets/libjiagu_vip.so"
        else -> null
    }

    private fun getFake360TypeLabel(type: Int): String = when (type) {
        FAKE_360_NORMAL -> "普通"
        FAKE_360_PAID -> "付费"
        FAKE_360_ENTERPRISE -> "企业"
        else -> "关闭"
    }

    // ── Companion object ──
    companion object {
        private const val REQ_SELECT_APK = 1001
        private const val TEMP_DIR_NAME = "ArkJiagu"
        private const val SP_SETTINGS = "ark_settings"
        private const val KEY_SO_NAME = "so_name"
        private const val KEY_SAVE_PATH = "save_path"
        private const val KEY_AUTO_SIGN = "auto_sign"
        private const val KEY_EMULATOR_COMPATIBILITY = "emulator_compatibility"
        private const val KEY_ROOT_SERVICE_COMPATIBILITY = "root_service_compatibility"
        private const val KEY_STRING_ENCRYPTION = "string_encryption"
        private const val KEY_SHIZUKU_SILENT_INSTALL = "shizuku_silent_install"
        private const val DEFAULT_SO_NAME = "ArkStub"
        private const val KEY_USE_CUSTOM_JKS = "use_custom_jks"
        private const val KEY_JKS_PATH = "jks_path"
        private const val KEY_JKS_STORE_PASS = "jks_store_pass"
        private const val KEY_JKS_ALIAS = "jks_alias"
        private const val KEY_JKS_KEY_PASS = "jks_key_pass"
        private const val KEY_STUB_CLASS_NAME = "stub_class_name"
        private const val DEFAULT_STUB_CLASS_NAME = ShellClassNamePolicy.DEFAULT_CLASS_NAME
        private const val KEY_FAKE_360_TYPE = "fake_360_type"
        private const val FAKE_360_OFF = 0
        private const val FAKE_360_NORMAL = 1
        private const val FAKE_360_PAID = 2
        private const val FAKE_360_ENTERPRISE = 3
        private const val FAKE_360_STUB_CLASS_NAME = "com.nkbe.StubApp"
        private const val REQ_STORAGE_PERMISSION = 10086
        private const val REQ_SHIZUKU_PERMISSION = 10087

        init {
            System.loadLibrary("ArkTool")
        }
    }
}
