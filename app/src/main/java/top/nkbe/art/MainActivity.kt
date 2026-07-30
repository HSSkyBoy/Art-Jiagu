package top.nkbe.art

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
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

class MainActivity : ComponentActivity() {

    // ── Properties ──
    private lateinit var uiController: NeoArtUiController
    private var isPermissionDialogShowing = false
    private var hasInitMain = false
    private lateinit var soNamePresets: Array<SoNamePreset>

    // ── Inner types ──
    private data class ArkSettings(
        var soName: String,
        var stubClassName: String,
        var savePath: String,
        var autoSign: Boolean,
        var fake360Type: Int,
        var useCustomJks: Boolean,
        var jksPath: String,
        var jksStorePass: String,
        var jksAlias: String,
        var jksKeyPass: String,
    )

    private data class SoNamePreset(val feature: String, val soName: String)

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

        checkPermissionOrShowDialog()
        soNamePresets = loadSoNamePresets()
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

        AlertDialog.Builder(this)
            .setTitle("需要文件访问权限")
            .setMessage("本工具需要文件访问权限，才能读取和处理 APK 文件。请点击去授权。")
            .setCancelable(false)
            .setPositiveButton("去授权") { dialog, _ ->
                isPermissionDialogShowing = false
                dialog.dismiss()
                openAllFilePermissionPage()
            }
            .show()
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

        return ArkSettings(soName, stubClassName, savePath, autoSign, fake360Type, useCustomJks, jksPath, jksStorePass, jksAlias, jksKeyPass)
    }

    private fun saveArkSettings(
        soName: String, stubClassName: String, savePath: String, autoSign: Boolean,
        fake360Type: Int, useCustomJks: Boolean, jksPath: String,
        jksStorePass: String, jksAlias: String, jksKeyPass: String,
    ) {
        getSharedPreferences(SP_SETTINGS, MODE_PRIVATE).edit()
            .putString(KEY_SO_NAME, soName)
            .putString(KEY_STUB_CLASS_NAME, stubClassName)
            .putString(KEY_SAVE_PATH, savePath)
            .putBoolean(KEY_AUTO_SIGN, autoSign)
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
        if (useCustomJks && !isValidJksSettings(jksPath, jksStorePass, jksAlias, jksKeyPass)) return "JKS 证书配置无效或未填完整"

        saveArkSettings(soName, stubClassName, savePath, autoSign, fake360Type, useCustomJks, jksPath, jksStorePass, jksAlias, jksKeyPass)
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
                val copiedApk = File(workDir, "待加固.apk")
                copyUriToFile(uri, copiedApk)
                ApkValidator.validate(copiedApk)
                appendLogOnUi("APK 结构校验通过")

                val appName = readApplicationName(copiedApk)
                appendLogOnUi("原始入口：" + appName)

                val shellDex = generateShellDex(workDir)
                val signHash64 = getSignHash64ForShell()
                buildEncryptedShellDex(copiedApk, shellDex, appName, signHash64)
                appendLogOnUi("加密完成：" + shellDex.absolutePath)

                extractStubSoByTargetAbi(copiedApk, workDir)
                val newManifest = modifyAndroidManifest(copiedApk, workDir)
                var protectedApk = rebuildProtectedApk(copiedApk, workDir, originalApkName)

                appendLogOnUi("开始进行 ZIPALIGN")
                protectedApk = zipAlignApk(protectedApk)
                appendLogOnUi("ZIPALIGN 完成")

                val settings = readArkSettings()
                if (settings.autoSign) {
                    appendLogOnUi("检测到已开启自动签名")
                    protectedApk = if (settings.useCustomJks) {
                        ApkSignUtil.signApk(
                            this, protectedApk, File(settings.jksPath),
                            settings.jksStorePass, settings.jksAlias, settings.jksKeyPass,
                        )
                    } else {
                        ApkSignUtil.signApk(this, protectedApk)
                    }
                    appendLogOnUi("APK 签名完成")
                } else {
                    appendLogOnUi("未开启自动签名，跳过签名")
                }

                appendLogOnUi("加固包输出：" + protectedApk.absolutePath)
                appendLogOnUi("----------->>>加固完成<<<-----------")

                val finalApk = protectedApk
                runOnUiThread {
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("加固完成")
                        .setMessage("APK 已加固完成，是否立即安装？")
                        .setPositiveButton("安装", null)
                        .setNegativeButton("取消") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .create()
                    dialog.show()
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { installApk(finalApk) }
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
    @Throws(Exception::class)
    private fun generateShellDex(outputDir: File): File {
        if (!outputDir.exists() && !outputDir.mkdirs())
            throw RuntimeException("创建输出目录失败：" + outputDir.absolutePath)

        val outputDex = File(outputDir, "classes.dex")
        val customStubClassName = validStubClassName
        val stubClass = "L" + customStubClassName.replace('.', '/') + ";"
        val applicationClass = "Landroid/app/Application;"
        val contextClass = "Landroid/content/Context;"

        val dexPool = DexPool(Opcodes.getDefault())

        val clinitMethod = ImmutableMethod(
            stubClass, "<clinit>", emptyList(), "V",
            AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(), null,
            ImmutableMethodImplementation(
                2,
                listOf(
                    ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("ark")),
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
                ), emptyList(), emptyList(),
            ),
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

        val attachMethod = ImmutableMethod(
            stubClass, "attachBaseContext",
            listOf(ImmutableMethodParameter(contextClass, emptySet(), null)), "V",
            AccessFlags.PROTECTED.value or AccessFlags.NATIVE.value,
            emptySet(), null, null,
        )

        val classDef = ImmutableClassDef(
            stubClass, AccessFlags.PUBLIC.value, applicationClass,
            emptyList(), "StubApp.java", emptySet(), emptyList(),
            listOf(clinitMethod, initMethod, attachMethod),
        )

        dexPool.internClass(classDef)
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

        val soFileName = validSoFileName
        for (abi in finalAbiList) {
            val outFile = File(workDir, "lib/$abi/$soFileName")
            val parent = outFile.parentFile ?: continue
            if (!parent.exists() && !parent.mkdirs())
                throw RuntimeException("创建 so 输出目录失败：" + parent.absolutePath)
            copySelfApkStubSoToFile(abi, outFile)
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
    private fun modifyAndroidManifest(apkFile: File, workDir: File): File {
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

            rewriteApplicationAttributes(application)

            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "utf-8")
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            }
            transformer.transform(DOMSource(document), StreamResult(manifestNewXml))
            Xml2AxmlTool.encode2(this, manifestNewXml.absolutePath, manifestNewAxml.absolutePath)
            return manifestNewAxml
        } finally {
            deleteFileQuietly(manifestAxml)
            deleteFileQuietly(manifestXml)
            deleteFileQuietly(manifestNewXml)
        }
    }

    private fun rewriteApplicationAttributes(application: Element) {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val oldAttrs = ArrayList<Attr>()

        val attrMap = application.attributes
        for (i in 0 until attrMap.length) {
            val node = attrMap.item(i)
            if (node is Attr) {
                val name = node.name
                if (name == "android:name" || name == "android:extractNativeLibs" ||
                    name == "name" || name == "extractNativeLibs") continue
                oldAttrs.add(node)
            }
        }

        while (application.attributes.length > 0) {
            application.removeAttributeNode(application.attributes.item(0) as Attr)
        }

        var hasLabelWritten = false
        var hasIconWritten = false
        var inserted = false

        for (attr in oldAttrs) {
            val attrName = attr.name
            val attrValue = attr.value
            if (attr.namespaceURI != null && attr.namespaceURI.isNotEmpty()) {
                application.setAttributeNS(attr.namespaceURI, attrName, attrValue)
            } else {
                application.setAttribute(attrName, attrValue)
            }
            if (attrName == "android:label") hasLabelWritten = true
            if (attrName == "android:icon") hasIconWritten = true
            if (!inserted && hasLabelWritten && hasIconWritten) {
                application.setAttributeNS(androidNs, "android:name", validStubClassName)
                application.setAttributeNS(androidNs, "android:extractNativeLibs", "true")
                inserted = true
            }
        }

        if (!inserted) {
            application.setAttributeNS(androidNs, "android:name", validStubClassName)
            application.setAttributeNS(androidNs, "android:extractNativeLibs", "true")
        }
    }

    // ── Repackaging ──
    @Throws(Exception::class)
    private fun rebuildProtectedApk(apkFile: File, workDir: File, originalApkName: String?): File {
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
        if (libDir.exists() && libDir.isDirectory) collectLibSkipNames(libDir, libDir, skipNames)

        val outApk = File(finalOutputDir, buildProtectedApkName(originalApkName))

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

                FileInputStream(newManifest).use { input ->
                    addZipEntryStream(zos, "AndroidManifest.xml", input, null)
                }
                appendLogOnUi("已写入新 AndroidManifest.xml")

                if (libDir.exists() && libDir.isDirectory) {
                    addLibDirToZipStream(zos, libDir, libDir)
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
            AlertDialog.Builder(this)
                .setTitle("需要安装权限")
                .setMessage("请先允许本应用安装未知来源应用")
                .setPositiveButton("去授权") { dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        doInstallApk(apkFile)
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

        init {
            System.loadLibrary("ArkTool")
        }
    }
}
