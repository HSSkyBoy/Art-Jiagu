package com.ark.jiagu

import com.ark.jiagu.vm.VmpJiaguEntry.extractOnCreateToBin
import com.ark.jiagu.vm.VmpJiaguEntry.parseVmpBinByClassId
import com.ark.jiagu.vm.VmpJiaguEntry.printDexlib2Opcodes
import com.ark.jiagu.vm.VmpJiaguEntry.printOnCreateExtractInfo
import com.ark.jiagu.vm.VmpJiaguEntry.rewriteExtractedMethodsToNativeDex

import android.app.Activity
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle

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
import com.ark.jiagu.vm.VmpJiaguEntry

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.EdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Enumeration
import java.util.HashSet
import java.util.Set
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class MainActivity : ComponentActivity() {
    private lateinit var btnSelectApk: Button
    private lateinit var tvLog: TextView
    private lateinit var logScrollView: ScrollView
    private var isPermissionDialogShowing = false
    private var hasInitMain = false
    private lateinit var btnSettings: android.widget.ImageButton
    private lateinit var SO_NAME_PRESETS: Array<SoNamePreset>

    companion object {
        const val REQ_SELECT_APK = 1001
        const val TEMP_DIR_NAME = "ArkJiagu"
        const val SP_SETTINGS = "ark_settings"
        const val KEY_SO_NAME = "so_name"
        const val KEY_SAVE_PATH = "save_path"
        const val KEY_AUTO_SIGN = "auto_sign"
        const val DEFAULT_SO_NAME = "ArkStub"
        const val KEY_USE_CUSTOM_JKS = "use_custom_jks"
        const val KEY_JKS_PATH = "jks_path"
        const val KEY_JKS_STORE_PASS = "jks_store_pass"
        const val KEY_JKS_ALIAS = "jks_alias"
        const val KEY_JKS_KEY_PASS = "jks_key_pass"
        const val KEY_STUB_CLASS_NAME = "stub_class_name"
        const val DEFAULT_STUB_CLASS_NAME = "com.ark.safe.StubApp"
        const val REQ_STORAGE_PERMISSION = 10086

        init {
            System.loadLibrary("ArkTool")
        }
    }

    private external fun buildEncryptedBlock(plainData: ByteArray): ByteArray

    private external fun fixDexHeader(dexData: ByteArray): ByteArray

    private external fun isValidDex(data: ByteArray): Boolean

    private external fun intToLe4(value: Int): ByteArray

    private external fun buildEncryptedShellDex(
        apkFile: File,
        shellDexFile: File,
        realApplicationName: String,
        signHash64: ByteArray
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(this)
        setContentView(R.layout.activity_main)


        /*new Thread(() -> {
            //测试抽vmp方法
            File apkFile = new File("/sdcard/demo.apk");
            File outDir = new File("/sdcard/Arkjiagu/");

            try {
                VmpJiaguEntry.extractManifestAndDex(apkFile, outDir);

                runOnUiThread(() -> appendLog("解压成功：" + outDir.getAbsolutePath()));
                extractOnCreateToBin(
                        new File("/sdcard/Arkjiagu"),
                        //"com.dev.demo.MainActivity.onCreate"
                        "*.*.onCreate"
                );//抽代码
                parseVmpBinByClassId(new File("/sdcard/Arkjiagu/vmp.bin"), 1);//解析抽出来的代码
                rewriteExtractedMethodsToNativeDex(
                        new File("/sdcard/Arkjiagu"),
                        "arkvm",
                        "AVMP"
                );

            } catch (IOException e) {
                runOnUiThread(() -> appendLog("解压失败：" + e.getMessage()));
            }
        }).start();*/









        initWindowInsets()

        checkPermissionOrShowDialog()
        SO_NAME_PRESETS = loadSoNamePresets()
    }

    override fun onResume() {
        super.onResume()

        if (hasInitMain) {
            return
        }

        if (hasAllFilePermission()) {
            initMainPage()
        } else {
            showPermissionDialog()
        }
    }

    private fun checkPermissionOrShowDialog() {
        if (hasAllFilePermission()) {
            initMainPage()
            return
        }

        showPermissionDialog()
    }




    private fun showPermissionDialog() {
        if (hasAllFilePermission()) {
            initMainPage()
            return
        }

        if (isPermissionDialogShowing) {
            return
        }

        isPermissionDialogShowing = true

        android.app.AlertDialog.Builder(this)
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

    private fun loadSoNamePresets(): Array<SoNamePreset> {
        val list = ArrayList<SoNamePreset>()

        try {
            val inputStream = getAssets().open("so_name_presets.json")
            val baos = ByteArrayOutputStream()

            val buffer = ByteArray(4096)
            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                baos.write(buffer, 0, len)
            }

            inputStream.close()

            val json = baos.toString("UTF-8")
            val array = org.json.JSONArray(json)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                val title = obj.optString("title", "").trim()
                val name = obj.optString("name", "").trim()

                if (title.isEmpty() || name.isEmpty()) {
                    continue
                }

                list.add(SoNamePreset(title, name))
            }

        } catch (e: Exception) {
            e.printStackTrace()

            list.add(SoNamePreset("Ark默认", "ArkStub"))
        }

        return list.toTypedArray()
    }

    private fun initWindowInsets() {
        val main = findViewById<View>(R.id.main)

        val left = main.paddingLeft
        val top = main.paddingTop
        val right = main.paddingRight
        val bottom = main.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.setPadding(
                left + systemBars.left,
                top + systemBars.top,
                right + systemBars.right,
                bottom + systemBars.bottom
            )

            insets
        }
    }

    private fun getValidSoNameFromSettings(): String {
        try {
            val settings = readArkSettings()

            if (settings != null && isValidSoName(settings.soName)) {
                return settings.soName.trim()
            }
        } catch (e: Exception) {
            appendLogOnUi("读取so名称设置失败，使用默认名称：" + e.message)
        }

        return DEFAULT_SO_NAME
    }

    private fun getValidSoFileNameFromSettings(): String {
        return "lib" + getValidSoNameFromSettings() + ".so"
    }

    private fun initMainPage() {
        if (hasInitMain) {
            return
        }

        hasInitMain = true

        val workDir = getWorkDir()
        cleanWorkDirOnStart(workDir)

        btnSelectApk = findViewById(R.id.btnSelectApk)
        tvLog = findViewById(R.id.tvLog)
        logScrollView = findViewById(R.id.logScrollView)
        btnSettings = findViewById(R.id.btnSettings)
        appendLog("加固器初始化完成")
        appendLog("等待选择 APK 文件")

        btnSelectApk.setOnClickListener { openApkSelector() }
        btnSettings.setOnClickListener { showSettingsDialog() }
    }

    private fun cleanWorkDirOnStart(workDir: File) {
        if (workDir == null || !workDir.exists()) {
            return
        }

        cleanTempFiles(workDir)
    }

    class ArkSettings(
        var soName: String,
        var stubClassName: String,
        var savePath: String,
        var autoSign: Boolean,
        var useCustomJks: Boolean,
        var jksPath: String,
        var jksStorePass: String,
        var jksAlias: String,
        var jksKeyPass: String
    )

    private fun readArkSettings(): ArkSettings {
        val sp = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE)

        val defaultSavePath = getWorkDir().absolutePath

        var soName = sp.getString(KEY_SO_NAME, DEFAULT_SO_NAME)
        var stubClassName = sp.getString(KEY_STUB_CLASS_NAME, DEFAULT_STUB_CLASS_NAME)
        var savePath = sp.getString(KEY_SAVE_PATH, defaultSavePath)
        val autoSign = sp.getBoolean(KEY_AUTO_SIGN, false)

        val useCustomJks = sp.getBoolean(KEY_USE_CUSTOM_JKS, false)
        var jksPath = sp.getString(KEY_JKS_PATH, "")
        var jksStorePass = sp.getString(KEY_JKS_STORE_PASS, "")
        var jksAlias = sp.getString(KEY_JKS_ALIAS, "")
        var jksKeyPass = sp.getString(KEY_JKS_KEY_PASS, "")

        if (soName == null || soName.trim().isEmpty()) {
            soName = DEFAULT_SO_NAME
        }

        if (stubClassName == null || stubClassName.trim().isEmpty()) {
            stubClassName = DEFAULT_STUB_CLASS_NAME
        }

        if (savePath == null || savePath.trim().isEmpty()) {
            savePath = defaultSavePath
        }

        return ArkSettings(
            soName ?: DEFAULT_SO_NAME,
            stubClassName ?: DEFAULT_STUB_CLASS_NAME,
            savePath ?: defaultSavePath,
            autoSign,
            useCustomJks,
            jksPath ?: "",
            jksStorePass ?: "",
            jksAlias ?: "",
            jksKeyPass ?: ""
        )
    }

    private fun saveArkSettings(
        soName: String,
        stubClassName: String,
        savePath: String,
        autoSign: Boolean,
        useCustomJks: Boolean,
        jksPath: String,
        jksStorePass: String,
        jksAlias: String,
        jksKeyPass: String
    ) {
        val sp = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE)

        sp.edit()
            .putString(KEY_SO_NAME, soName)
            .putString(KEY_STUB_CLASS_NAME, stubClassName)
            .putString(KEY_SAVE_PATH, savePath)
            .putBoolean(KEY_AUTO_SIGN, autoSign)
            .putBoolean(KEY_USE_CUSTOM_JKS, useCustomJks)
            .putString(KEY_JKS_PATH, jksPath)
            .putString(KEY_JKS_STORE_PASS, jksStorePass)
            .putString(KEY_JKS_ALIAS, jksAlias)
            .putString(KEY_JKS_KEY_PASS, jksKeyPass)
            .apply()
    }

    private fun getValidStubClassNameFromSettings(): String {
        try {
            val settings = readArkSettings()

            if (settings != null
                && settings.stubClassName != null
                && settings.stubClassName.trim().isNotEmpty()
            ) {
                return settings.stubClassName.trim()
            }
        } catch (_: Exception) {
        }

        return DEFAULT_STUB_CLASS_NAME
    }

    private fun isValidJksSettings(jksPath: String, storePass: String, alias: String, keyPass: String): Boolean {
        if (jksPath == null || jksPath.trim().isEmpty()) {
            Toast.makeText(this, "JKS证书路径不能为空", Toast.LENGTH_LONG).show()
            return false
        }

        val jksFile = File(jksPath.trim())
        if (!jksFile.exists() || !jksFile.isFile()) {
            Toast.makeText(this, "JKS证书文件不存在", Toast.LENGTH_LONG).show()
            return false
        }

        if (storePass == null || storePass.trim().isEmpty()) {
            Toast.makeText(this, "证书密码不能为空", Toast.LENGTH_LONG).show()
            return false
        }

        if (alias == null || alias.trim().isEmpty()) {
            Toast.makeText(this, "别名不能为空", Toast.LENGTH_LONG).show()
            return false
        }

        if (keyPass == null || keyPass.trim().isEmpty()) {
            Toast.makeText(this, "别名密码不能为空", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun isValidSoName(soName: String?): Boolean {
        if (soName == null) {
            return false
        }

        var name = soName.trim()

        if (name.isEmpty()) {
            return false
        }

        if (name.startsWith("lib")) {
            return false
        }

        if (name.endsWith(".so")) {
            return false
        }

        return name.matches("[A-Za-z0-9_]+".toRegex())
    }

    private fun isValidSavePath(savePath: String?): Boolean {
        if (savePath == null || savePath.trim().isEmpty()) {
            return false
        }

        val dir = File(savePath.trim())

        if (dir.exists()) {
            return dir.isDirectory() && dir.canWrite()
        }

        return dir.mkdirs() && dir.isDirectory() && dir.canWrite()
    }

    private fun isValidStubClassName(className: String?): Boolean {
        if (className == null) {
            return false
        }

        var name = className.trim()

        if (name.isEmpty()) {
            return false
        }

        if (name.startsWith(".")) {
            return false
        }

        if (name.endsWith(".")) {
            return false
        }

        if (name.contains("..")) {
            return false
        }

        val parts = name.split("\\.".toRegex())

        if (parts.size < 2) {
            return false
        }

        for (part in parts) {
            if (part == null || part.isEmpty()) {
                return false
            }

            val first = part[0]

            // 首字符不能数字
            if (first.isDigit()) {
                return false
            }

            // 首字符必须是合法Java标识符开始
            if (!Character.isJavaIdentifierStart(first)) {
                return false
            }

            for (i in 1 until part.length) {
                if (!Character.isJavaIdentifierPart(part[i])) {
                    return false
                }
            }
        }

        return true
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val etSoName = dialogView.findViewById<android.widget.EditText>(R.id.etSoName)
        val etSavePath = dialogView.findViewById<android.widget.EditText>(R.id.etSavePath)
        val swAutoSign = dialogView.findViewById<android.widget.Switch>(R.id.swAutoSign)

        val swUseCustomJks = dialogView.findViewById<android.widget.Switch>(R.id.swUseCustomJks)
        val llCustomJksForm = dialogView.findViewById<android.widget.LinearLayout>(R.id.llCustomJksForm)
        val etJksPath = dialogView.findViewById<android.widget.EditText>(R.id.etJksPath)
        val etJksStorePass = dialogView.findViewById<android.widget.EditText>(R.id.etJksStorePass)
        val etJksAlias = dialogView.findViewById<android.widget.EditText>(R.id.etJksAlias)
        val etJksKeyPass = dialogView.findViewById<android.widget.EditText>(R.id.etJksKeyPass)
        val btnSoNamePreset = dialogView.findViewById<android.widget.ImageButton>(R.id.btnSoNamePreset)
        val btnSaveSettings = dialogView.findViewById<Button>(R.id.btnSaveSettings)
        val etStubClassName = dialogView.findViewById<android.widget.EditText>(R.id.etStubClassName)
        val btnClearStubClassName = dialogView.findViewById<android.widget.ImageButton>(R.id.btnClearStubClassName)
        val btnClearSavePath = dialogView.findViewById<android.widget.ImageButton>(R.id.btnClearSavePath)


        val settings = readArkSettings()
        etStubClassName.setText(settings.stubClassName)
        etSoName.setText(settings.soName)
        etSavePath.setText(settings.savePath)
        swAutoSign.isChecked = settings.autoSign

        swUseCustomJks.isChecked = settings.useCustomJks
        etJksPath.setText(settings.jksPath)
        etJksStorePass.setText(settings.jksStorePass)
        etJksAlias.setText(settings.jksAlias)
        etJksKeyPass.setText(settings.jksKeyPass)

        llCustomJksForm.visibility = if (settings.useCustomJks) View.VISIBLE else View.GONE

        swUseCustomJks.setOnCheckedChangeListener { _, isChecked ->
            llCustomJksForm.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        btnSoNamePreset.setOnClickListener { showSoNamePresetDialog(etSoName) }
        btnClearStubClassName.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("是否清空自定义壳类名？")
                .setPositiveButton("确定") { d, _ -> etStubClassName.setText("") }
                .setNegativeButton("取消", null)
                .show()
        }

        btnClearSavePath.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("是否清空文件保存路径？")
                .setPositiveButton("确定") { d, _ -> etSavePath.setText("") }
                .setNegativeButton("取消", null)
                .show()
        }
        btnSaveSettings.setOnClickListener {
            val soName = etSoName.text.toString().trim()
            val stubClassName = etStubClassName.text.toString().trim()
            val savePath = etSavePath.text.toString().trim()
            val autoSign = swAutoSign.isChecked

            val useCustomJks = swUseCustomJks.isChecked
            val jksPath = etJksPath.text.toString().trim()
            val jksStorePass = etJksStorePass.text.toString()
            val jksAlias = etJksAlias.text.toString().trim()
            val jksKeyPass = etJksKeyPass.text.toString()

            if (!isValidSoName(soName)) {
                Toast.makeText(this, "so名称不合法，只能使用字母、数字、下划线，不要带lib和.so", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!isValidStubClassName(stubClassName)) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("类名格式错误")
                    .setMessage(
                        "自定义壳类名不合法\n\n" +
                                "正确示例：\n" +
                                "com.ark.safe.StubApp\n\n" +
                                "规则：\n" +
                                "1、至少包含包名和类名\n" +
                                "2、每段不能以数字开头\n" +
                                "3、只能包含字母、数字、下划线和$\n" +
                                "4、不能包含空段"
                    )
                    .setPositiveButton("确定", null)
                    .show()
                return@setOnClickListener
            }
            if (!isValidSavePath(savePath)) {
                Toast.makeText(this, "文件保存路径无效或不可写", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (useCustomJks && !isValidJksSettings(jksPath, jksStorePass, jksAlias, jksKeyPass)) {
                return@setOnClickListener
            }

            saveArkSettings(
                soName,
                stubClassName,
                savePath,
                autoSign,
                useCustomJks,
                jksPath,
                jksStorePass,
                jksAlias,
                jksKeyPass
            )

            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()

        if (dialog.window != null) {
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    class SoNamePreset(
        var feature: String,
        var soName: String
    )

    private fun showSoNamePresetDialog(etSoName: android.widget.EditText) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_so_name_preset, null)

        val rgSoNamePreset = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgSoNamePreset)
        val btnConfirmSoNamePreset = dialogView.findViewById<Button>(R.id.btnConfirmSoNamePreset)

        for (i in SO_NAME_PRESETS.indices) {
            val item = SO_NAME_PRESETS[i]

            val radioButton = android.widget.RadioButton(this)
            radioButton.id = 10000 + i
            radioButton.text = "特征：" + item.feature + "\nso名称：" + item.soName
            radioButton.setTextColor(android.graphics.Color.parseColor("#374151"))
            radioButton.setTextSize(14f)
            radioButton.setPadding(8, 10, 8, 10)
            radioButton.isSingleLine = false

            rgSoNamePreset.addView(radioButton)
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnConfirmSoNamePreset.setOnClickListener {
            val checkedId = rgSoNamePreset.checkedRadioButtonId

            if (checkedId == -1) {
                Toast.makeText(this, "请选择一个预设so名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val index = checkedId - 10000

            if (index < 0 || index >= SO_NAME_PRESETS.size) {
                Toast.makeText(this, "选择项无效", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            etSoName.setText(SO_NAME_PRESETS[index].soName)
            etSoName.setSelection(etSoName.text.length)

            dialog.dismiss()
        }

        dialog.show()

        if (dialog.window != null) {
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun getWorkDir(): File {
        return File(Environment.getExternalStorageDirectory(), TEMP_DIR_NAME)
    }

    private fun hasAllFilePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
        }

        return true
    }

    private fun openAllFilePermissionPage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + getPackageName())
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQ_STORAGE_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_STORAGE_PERMISSION) {
            if (hasAllFilePermission()) {
                initMainPage()
            } else {
                Toast.makeText(this, "未授予文件访问权限", Toast.LENGTH_LONG).show()
                showPermissionDialog()
            }
        }
    }

    private fun appendLog(text: String) {
        if (tvLog == null) {
            return
        }

        tvLog.append(text + "\n")

        if (logScrollView != null) {
            logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /**
     * 在指定目录生成壳 classes.dex 此版本是attach间接调用
     */
    private fun generateShellDex2(outputDir: File): File {
        if (outputDir == null) {
            throw IllegalArgumentException("输出目录为空")
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw RuntimeException("创建输出目录失败：" + outputDir.absolutePath)
        }

        val outputDex = File(outputDir, "classes.dex")

        val customStubClassName = getValidStubClassNameFromSettings()

        val stubClass = "L" + customStubClassName.replace('.', '/') + ";"
        val applicationClass = "Landroid/app/Application;"
        val contextClass = "Landroid/content/Context;"

        val dexPool = DexPool(Opcodes.getDefault())

        val clinitMethod = ImmutableMethod(
            stubClass,
            "<clinit>",
            emptyList<ImmutableMethodParameter>(),
            "V",
            AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(),
            null,
            ImmutableMethodImplementation(
                2,
                listOf(
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        0,
                        ImmutableStringReference("ark")
                    ),
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        1,
                        ImmutableStringReference(customStubClassName)
                    ),
                    ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC,
                        2,
                        0,
                        1,
                        0,
                        0,
                        0,
                        ImmutableMethodReference(
                            "Ljava/lang/System;",
                            "setProperty",
                            listOf(
                                "Ljava/lang/String;",
                                "Ljava/lang/String;"
                            ),
                            "Ljava/lang/String;"
                        )
                    ),
                    ImmutableInstruction11x(
                        Opcode.MOVE_RESULT_OBJECT,
                        0
                    ),
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        0,
                        ImmutableStringReference(getValidSoNameFromSettings())
                    ),
                    ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        ImmutableMethodReference(
                            "Ljava/lang/System;",
                            "loadLibrary",
                            listOf("Ljava/lang/String;"),
                            "V"
                        )
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                emptyList(),
                emptyList()
            )
        )

        val initMethod = ImmutableMethod(
            stubClass,
            "<init>",
            emptyList<ImmutableMethodParameter>(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(),
            null,
            ImmutableMethodImplementation(
                1,
                listOf(
                    ImmutableInstruction35c(
                        Opcode.INVOKE_DIRECT,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        ImmutableMethodReference(
                            applicationClass,
                            "<init>",
                            emptyList<String>(),
                            "V"
                        )
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                emptyList(),
                emptyList()
            )
        )

        val dtcLoaderMethod = ImmutableMethod(
            stubClass,
            "DtcLoader",
            listOf(
                ImmutableMethodParameter(
                    contextClass,
                    emptySet(),
                    null
                )
            ),
            "V",
            AccessFlags.PRIVATE.value
                    or AccessFlags.STATIC.value
                    or AccessFlags.NATIVE.value,
            emptySet(),
            null,
            null
        )

        val attachBaseContextMethod = ImmutableMethod(
            stubClass,
            "attachBaseContext",
            listOf(
                ImmutableMethodParameter(
                    contextClass,
                    emptySet(),
                    null
                )
            ),
            "V",
            AccessFlags.PROTECTED.value,
            emptySet(),
            null,
            ImmutableMethodImplementation(
                2,
                listOf(
                    ImmutableInstruction35c(
                        Opcode.INVOKE_SUPER,
                        2,
                        0,
                        1,
                        0,
                        0,
                        0,
                        ImmutableMethodReference(
                            applicationClass,
                            "attachBaseContext",
                            listOf(contextClass),
                            "V"
                        )
                    ),
                    ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        ImmutableMethodReference(
                            stubClass,
                            "DtcLoader",
                            listOf(contextClass),
                            "V"
                        )
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                emptyList(),
                emptyList()
            )
        )

        val classDef = ImmutableClassDef(
            stubClass,
            AccessFlags.PUBLIC.value,
            applicationClass,
            emptyList<String>(),
            "StubApp.java",
            emptySet(),
            emptyList(),
            listOf(
                clinitMethod,
                initMethod,
                dtcLoaderMethod,
                attachBaseContextMethod
            )
        )

        dexPool.internClass(classDef)
        dexPool.writeTo(FileDataStore(outputDex))

        return outputDex
    }

    /**
     * 在指定目录生成壳 classes.dex 此版本是attach直接调用
     */
    private fun generateShellDex(outputDir: File): File {
        if (outputDir == null) {
            throw IllegalArgumentException("输出目录为空")
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw RuntimeException("创建输出目录失败：" + outputDir.absolutePath)
        }

        val outputDex = File(outputDir, "classes.dex")

        val customStubClassName = getValidStubClassNameFromSettings()

        val stubClass = "L" + customStubClassName.replace('.', '/') + ";"
        val applicationClass = "Landroid/app/Application;"
        val contextClass = "Landroid/content/Context;"

        val dexPool = DexPool(Opcodes.getDefault())

        val clinitMethod = ImmutableMethod(
            stubClass,
            "<clinit>",
            emptyList<ImmutableMethodParameter>(),
            "V",
            AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(),
            null,
            ImmutableMethodImplementation(
                2,
                listOf(
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        0,
                        ImmutableStringReference("ark")
                    ),
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        1,
                        ImmutableStringReference(customStubClassName)
                    ),
                    ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC,
                        2,
                        0,
                        1,
                        0,
                        0,
                        0,
                        ImmutableMethodReference(
                            "Ljava/lang/System;",
                            "setProperty",
                            listOf(
                                "Ljava/lang/String;",
                                "Ljava/lang/String;"
                            ),
                            "Ljava/lang/String;"
                        )
                    ),
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        0,
                        ImmutableStringReference(getValidSoNameFromSettings())
                    ),
                    ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        ImmutableMethodReference(
                            "Ljava/lang/System;",
                            "loadLibrary",
                            listOf("Ljava/lang/String;"),
                            "V"
                        )
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                emptyList(),
                emptyList()
            )
        )

        val initMethod = ImmutableMethod(
            stubClass,
            "<init>",
            emptyList<ImmutableMethodParameter>(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(),
            null,
            ImmutableMethodImplementation(
                1,
                listOf(
                    ImmutableInstruction35c(
                        Opcode.INVOKE_DIRECT,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        ImmutableMethodReference(
                            applicationClass,
                            "<init>",
                            emptyList<String>(),
                            "V"
                        )
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                emptyList(),
                emptyList()
            )
        )

        val attachBaseContextMethod = ImmutableMethod(
            stubClass,
            "attachBaseContext",
            listOf(
                ImmutableMethodParameter(
                    contextClass,
                    emptySet(),
                    null
                )
            ),
            "V",
            AccessFlags.PROTECTED.value or AccessFlags.NATIVE.value,
            emptySet(),
            null,
            null
        )

        val classDef = ImmutableClassDef(
            stubClass,
            AccessFlags.PUBLIC.value,
            applicationClass,
            emptyList<String>(),
            "StubApp.java",
            emptySet(),
            emptyList(),
            listOf(
                clinitMethod,
                initMethod,
                attachBaseContextMethod
            )
        )

        dexPool.internClass(classDef)
        dexPool.writeTo(FileDataStore(outputDex))

        return outputDex
    }

    private fun openApkSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/vnd.android.package-archive"
        startActivityForResult(intent, REQ_SELECT_APK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_SELECT_APK && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (uri == null) {
                appendLog("选择文件失败：Uri为空")
                return
            }

            handleSelectedApk(uri)
        }
    }

    private fun handleSelectedApk(uri: Uri) {
        btnSelectApk.isEnabled = false

        Thread {
            val workDir = getWorkDir()
            try {
                appendLogOnUi("开始处理 APK")


                if (!workDir.exists() && !workDir.mkdirs()) {
                    throw RuntimeException("创建临时目录失败：" + workDir.absolutePath)
                }

                appendLogOnUi("临时目录：" + workDir.absolutePath)
                var originalApkName = getFileNameFromUri(uri)
                if (originalApkName == null || originalApkName.trim().isEmpty()) {
                    originalApkName = "未知APK.apk"
                }
                val copiedApk = File(workDir, "待加固.apk")
                copyUriToFile(uri, copiedApk)
                //appendLogOnUi("APK 已复制：" + copiedApk.getAbsolutePath());

                val appName = readApplicationName(copiedApk)
                appendLogOnUi("原始入口：" + appName)

                var shellDex = generateShellDex(workDir)
                //appendLogOnUi("壳已生成：" + shellDex.getAbsolutePath());

                val signHash64 = getSignHash64ForShell()//读取64位证书hash


                buildEncryptedShellDex(copiedApk, shellDex, appName, signHash64)
                appendLogOnUi("加密完成：" + shellDex.absolutePath)

                extractStubSoByTargetAbi(copiedApk, workDir)
                //appendLogOnUi("壳 so 解压完成");

                val newManifest = modifyAndroidManifest(copiedApk, workDir)
                //appendLogOnUi("修改后的 Manifest：" + newManifest.getAbsolutePath());

                //File protectedApk = rebuildProtectedApk(copiedApk, workDir);
                var protectedApk = rebuildProtectedApk(copiedApk, workDir, originalApkName)

                appendLogOnUi("开始进行 ZIPALIGN")
                protectedApk = zipAlignApk(protectedApk)
                appendLogOnUi("ZIPALIGN 完成")

                val settings = readArkSettings()
                val autoSign = settings != null && settings.autoSign
                if (autoSign) {
                    appendLogOnUi("检测到已开启自动签名")

                    if (settings.useCustomJks) {
                        protectedApk = ApkSignUtil.signApk(
                            this@MainActivity,
                            protectedApk,
                            File(settings.jksPath),
                            settings.jksStorePass,
                            settings.jksAlias,
                            settings.jksKeyPass
                        )
                    } else {
                        protectedApk = ApkSignUtil.signApk(this@MainActivity, protectedApk)
                    }

                    appendLogOnUi("APK 签名完成")
                } else {
                    appendLogOnUi("未开启自动签名，跳过签名")
                }

                appendLogOnUi("加固包输出：" + protectedApk.absolutePath)

                //cleanTempFiles(workDir);
                appendLogOnUi("----------->>>加固完成<<<-----------")
                val finalProtectedApk = protectedApk
                runOnUiThread {
                    val dialog = android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("加固完成")
                        .setMessage("APK 已加固完成，是否立即安装？")
                        .setPositiveButton("安装", null)
                        .setNegativeButton("取消") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .create()

                    dialog.show()

                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {
                            // TODO: 这里执行安装方法
                            installApk(finalProtectedApk)

                            // 不关闭弹窗
                        }
                }

            } catch (e: Exception) {
                appendLogOnUi("处理失败：" + e.message)
            } finally {
                cleanTempFiles(workDir)
                runOnUiThread { btnSelectApk.isEnabled = true }
            }
        }.start()
    }

    private fun getSignHash64ForShell(): ByteArray? {
        //由于已移除签名校验的c代码，因此这里不能再返回证书信息，否则so层无法解密
        return null

        /*try {
            ArkSettings settings = readArkSettings();

            if (settings == null || !settings.autoSign) {
                appendLogOnUi("未开启自动签名，签名证书绑定值为空");
                return null;
            }

            String sha256;

            if (settings.useCustomJks) {
                appendLogOnUi("使用自定义证书获取指纹");

                if (!isValidJksSettings(
                        settings.jksPath,
                        settings.jksStorePass,
                        settings.jksAlias,
                        settings.jksKeyPass
                )) {
                    return null;
                }

                sha256 = JksSha256Util.getJksSha256FromFile(
                        new File(settings.jksPath),
                        settings.jksStorePass,
                        settings.jksAlias,
                        settings.jksKeyPass,
                        getCacheDir()
                );
            } else {
                appendLogOnUi("使用内置 Ark.jks 获取 指纹");

                sha256 = JksSha256Util.getArkJksSha256(this);
            }

            if (sha256 == null) {
                appendLogOnUi("证书指纹获取失败：结果为空");
                return null;
            }

            sha256 = sha256.trim();
            sha256 = sha256.toLowerCase(java.util.Locale.ROOT);

            if (sha256.length() != 64) {
                appendLogOnUi("证书指纹长度异常：" + sha256.length());
                return null;
            }

            appendLogOnUi("证书指纹获取成功");
            appendLogOnUi("证书指纹：" + sha256);

            return sha256.getBytes("UTF-8");

        } catch (Exception e) {
            appendLogOnUi("证书指纹获取失败：" + e.getMessage());
            return null;
        }*/
    }

    private fun installApk(apkFile: File) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, "APK文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        // Android 8.0+ 需要检查"安装未知应用"权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("需要安装权限")
                    .setMessage("请先允许本应用安装未知来源应用")
                    .setPositiveButton("去授权") { dialog, _ ->
                        dialog.dismiss()

                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                        )
                        intent.data = Uri.parse("package:" + getPackageName())
                        startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
        }

        doInstallApk(apkFile)
    }

    private fun doInstallApk(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val apkUri: android.net.Uri

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                apkFile
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            apkUri = Uri.fromFile(apkFile)
        }

        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        startActivity(intent)
    }

    private fun zipAlignApk(inputApk: File): File {
        if (inputApk == null || !inputApk.exists()) {
            throw RuntimeException("待对齐 APK 不存在")
        }

        val parentDir = inputApk.parentFile
        if (parentDir == null || !parentDir.exists()) {
            throw RuntimeException("APK 所在目录不存在")
        }

        val alignedApk = File(parentDir, inputApk.name + ".aligning")

        deleteFileQuietly(alignedApk)

        val success = ZipAlign.doZipAlign(
            inputApk.absolutePath,
            alignedApk.absolutePath,
            4,
            true,
            true
        )

        if (!success || !alignedApk.exists()) {
            throw RuntimeException("zipalign 对齐失败")
        }

        val verified = ZipAlign.isZipAligned(
            alignedApk.absolutePath,
            4,
            true
        )

        if (!verified) {
            deleteFileQuietly(alignedApk)
            throw RuntimeException("zipalign 校验失败")
        }

        if (!inputApk.delete()) {
            deleteFileQuietly(alignedApk)
            throw RuntimeException("删除原 APK 失败")
        }

        if (!alignedApk.renameTo(inputApk)) {
            deleteFileQuietly(alignedApk)
            throw RuntimeException("重命名对齐 APK 失败")
        }

        return inputApk
    }

    private fun appendLogOnUi(text: String) {
        System.out.println("[log] " + text)
        runOnUiThread { appendLog(text) }
    }

    private fun copyUriToFile(uri: Uri, outFile: File) {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw RuntimeException("无法打开输入文件")

        val out = FileOutputStream(outFile)

        val buffer = ByteArray(8192)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            out.write(buffer, 0, len)
        }

        out.flush()
        out.close()
        inputStream.close()
    }

    private fun readApplicationName(apkFile: File): String {
        val pm = packageManager

        val info = pm.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
        )

        if (info == null || info.applicationInfo == null) {
            return "android.app.Application"
        }

        val className = info.applicationInfo.className
        if (className == null || className.trim().isEmpty()) {
            return "android.app.Application"
        }

        return className
    }

    /*private void buildEncryptedShellDex(File apkFile, File shellDexFile, String realApplicationName) throws Exception {
        byte[] shellDex = readAllBytes(shellDexFile);

        if (!isValidDex(shellDex)) {
            throw new RuntimeException("壳 dex 非法");
        }

        ByteArrayOutputStream payload = new ByteArrayOutputStream();

        ZipFile zipFile = new ZipFile(apkFile);
        int dexCount = 0;

        for (int i = 1; ; i++) {
            String dexName = i == 1 ? "classes.dex" : "classes" + i + ".dex";
            ZipEntry entry = zipFile.getEntry(dexName);

            if (entry == null) {
                //appendLogOnUi("未找到 " + dexName + "，停止提取");
                break;
            }

            byte[] dexData = readAllBytes(zipFile.getInputStream(entry));

            if (!isValidDex(dexData)) {
                appendLogOnUi("发现非法 dex，停止处理：" + dexName);
                break;
            }

            payload.write(buildEncryptedBlock(dexData));
            dexCount++;

            appendLogOnUi("已加密：" + dexName + "，大小：" + dexData.length);
        }

        zipFile.close();

        if (dexCount <= 0) {
            throw new RuntimeException("APK 中没有找到合法 dex");
        }

        payload.write(buildEncryptedBlock(realApplicationName.getBytes("UTF-8")));
        appendLogOnUi("已加密入口：" + realApplicationName);

        payload.write(intToLe4(dexCount));

        ByteArrayOutputStream finalDex = new ByteArrayOutputStream();
        finalDex.write(shellDex);
        finalDex.write(payload.toByteArray());

        byte[] fixedDex = fixDexHeader(finalDex.toByteArray());

        FileOutputStream fos = new FileOutputStream(shellDexFile);
        fos.write(fixedDex);
        fos.flush();
        fos.close();

        //appendLogOnUi("最终壳 dex 大小：" + fixedDex.length);
    }*/

    /*private byte[] buildEncryptedBlock(byte[] plainData) throws Exception {
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);

        byte[] encryptedData = xorBytes(plainData, key);
        byte[] encryptedLen = xorBytes(intToLe4(plainData.length), key);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(encryptedData);
        out.write(encryptedLen);
        out.write(key);

        return out.toByteArray();
    }*/

    /*private byte[] xorBytes(byte[] data, byte[] key) {
        byte[] out = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }

        return out;
    }*/

    /*private boolean isValidDex(byte[] data) {
        return data != null
                && data.length >= 0x70
                && data[0] == 'd'
                && data[1] == 'e'
                && data[2] == 'x'
                && data[3] == '\n';
    }*/

    /*private byte[] intToLe4(int value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }*/

    /*private byte[] fixDexHeader(byte[] dexData) throws Exception {
        int fileSize = dexData.length;

        byte[] fileSizeBytes = intToLe4(fileSize);
        System.arraycopy(fileSizeBytes, 0, dexData, 32, 4);

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(dexData, 32, dexData.length - 32);
        byte[] signature = sha1.digest();
        System.arraycopy(signature, 0, dexData, 12, 20);

        Adler32 adler32 = new Adler32();
        adler32.update(dexData, 12, dexData.length - 12);
        int checksum = (int) adler32.getValue();

        byte[] checksumBytes = intToLe4(checksum);
        System.arraycopy(checksumBytes, 0, dexData, 8, 4);

        return dexData;
    }*/

    private fun rebuildProtectedApk(apkFile: File, workDir: File, originalApkName: String): File {
        appendLogOnUi("开始重打包 APK")

        val newClassesDex = File(workDir, "classes.dex")
        val newManifest = File(workDir, "AndroidManifest.xml")
        val libDir = File(workDir, "lib")

        if (!newClassesDex.exists()) {
            throw RuntimeException("未找到新的 classes.dex")
        }

        if (!newManifest.exists()) {
            throw RuntimeException("未找到修改后的 AndroidManifest.xml")
        }

        val skipNames = HashSet<String>()

        var checkZip: ZipFile? = null
        try {
            checkZip = ZipFile(apkFile)
            var dexIndex = 1
            while (true) {
                val dexName = if (dexIndex == 1) "classes.dex" else "classes$dexIndex.dex"
                val entry = checkZip.getEntry(dexName)
                if (entry == null) {
                    break
                }
                skipNames.add(dexName)
                dexIndex++
            }
        } finally {
            checkZip?.close()
        }

        skipNames.add("AndroidManifest.xml")

        if (libDir.exists() && libDir.isDirectory()) {
            collectLibSkipNames(libDir, libDir, skipNames)
        }

        val outApk = File(
            getFinalOutputDir(workDir),
            buildProtectedApkName(originalApkName)
        )

        val zipFile = ZipFile(apkFile)
        var zos: ZipOutputStream? = null
        try {
            zos = ZipOutputStream(FileOutputStream(outApk))
            zos.setLevel(9)

            val entries = zipFile.entries()

            while (entries.hasMoreElements()) {
                val oldEntry = entries.nextElement()
                val name = oldEntry.name

                if (skipNames.contains(name)) {
                    continue
                }

                if (oldEntry.isDirectory()) {
                    addDirectoryZipEntry(zos, name, oldEntry)
                    continue
                }

                val inputStream = zipFile.getInputStream(oldEntry)
                addZipEntryStream(zos, name, inputStream, oldEntry)
            }

            addZipEntryStream(zos, "classes.dex", FileInputStream(newClassesDex), null)
            appendLogOnUi("已写入新 classes.dex")

            addZipEntryStream(zos, "AndroidManifest.xml", FileInputStream(newManifest), null)
            appendLogOnUi("已写入新 AndroidManifest.xml")

            if (libDir.exists() && libDir.isDirectory()) {
                addLibDirToZipStream(zos, libDir, libDir)
            }

            zos.finish()
        } finally {
            try {
                zos?.close()
            } catch (_: Exception) {
            }

            try {
                zipFile.close()
            } catch (_: Exception) {
            }
        }

        appendLogOnUi("重打包完成：" + outApk.absolutePath)
        return outApk
    }

    private fun addZipEntryStream(zos: ZipOutputStream, name: String, inputStream: InputStream, oldEntry: ZipEntry?) {
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
                var size: Long = 0

                val tempOut = FileOutputStream(tempFile)
                val buffer = ByteArray(8192)
                var len: Int

                while (inputStream.read(buffer).also { len = it } != -1) {
                    tempOut.write(buffer, 0, len)
                    crc32.update(buffer, 0, len)
                    size += len
                }

                tempOut.flush()
                tempOut.close()

                newEntry.method = ZipEntry.STORED
                newEntry.size = size
                newEntry.compressedSize = size
                newEntry.crc = crc32.value

                zos.putNextEntry(newEntry)

                val tempIn = FileInputStream(tempFile)
                while (tempIn.read(buffer).also { len = it } != -1) {
                    zos.write(buffer, 0, len)
                }
                tempIn.close()

                zos.closeEntry()
            } else {
                newEntry.method = ZipEntry.DEFLATED

                zos.putNextEntry(newEntry)

                val buffer = ByteArray(8192)
                var len: Int

                while (inputStream.read(buffer).also { len = it } != -1) {
                    zos.write(buffer, 0, len)
                }

                zos.closeEntry()
            }
        } finally {
            try {
                inputStream.close()
            } catch (_: Exception) {
            }

            if (tempFile != null && tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun addDirectoryZipEntry(zos: ZipOutputStream, name: String, oldEntry: ZipEntry?) {
        var entryName = name
        if (!entryName.endsWith("/")) {
            entryName = entryName + "/"
        }

        val newEntry = ZipEntry(entryName)

        if (oldEntry != null) {
            newEntry.time = oldEntry.time
            newEntry.comment = oldEntry.comment
            newEntry.extra = oldEntry.extra
        }

        zos.putNextEntry(newEntry)
        zos.closeEntry()
    }

    private fun addLibDirToZipStream(zos: ZipOutputStream, rootDir: File, currentDir: File) {
        val files = currentDir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory()) {
                addLibDirToZipStream(zos, rootDir, file)
                continue
            }

            val relativePath = rootDir.toURI().relativize(file.toURI()).path
            val zipName = "lib/" + relativePath

            addZipEntryStream(zos, zipName, FileInputStream(file), null)
        }
    }

    private fun readAllBytes(file: File): ByteArray {
        val fis = FileInputStream(file)
        val data = readAllBytes(fis)
        fis.close()
        return data
    }

    private fun readAllBytes(inputStream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()

        val buffer = ByteArray(8192)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            out.write(buffer, 0, len)
        }

        inputStream.close()
        return out.toByteArray()
    }

    private fun getAssetAbiList2(): ArrayList<String> {
        val abiList = ArrayList<String>()

        val dirs = getAssets().list("lib") ?: return abiList

        for (abi in dirs) {
            if ("armeabi" == abi) {
                continue
            }

            val soPath = "lib/" + abi + "/libArkStub.so"

            try {
                val inputStream = getAssets().open(soPath)
                inputStream.close()
                abiList.add(abi)
            } catch (_: Exception) {
            }
        }

        return abiList
    }

    private fun extractStubSoByTargetAbi(apkFile: File, workDir: File) {
        appendLogOnUi("开始读取目标 APK ABI")

        //ArrayList<String> assetAbiList = getAssetAbiList();
        val selfAbiList = getSelfApkStubAbiList()
        if (selfAbiList.isEmpty()) {
            throw RuntimeException("assets/lib 下没有可用 ABI")
        }

        //appendLogOnUi("壳内可用 ABI：" + assetAbiList.toString());

        val targetAbiList = readApkAbiList(apkFile)
        val finalAbiList = ArrayList<String>()

        if (targetAbiList.isEmpty()) {
            appendLogOnUi("目标 APK 没有 lib 目录，使用 assets/lib 下全部 ABI")

            for (abi in selfAbiList) {
                if ("armeabi" == abi) {
                    appendLogOnUi("跳过 armeabi")
                    continue
                }
                finalAbiList.add(abi)
            }
        } else {
            appendLogOnUi("目标 APK ABI：" + targetAbiList.toString())

            for (abi in targetAbiList) {
                if ("armeabi" == abi) {
                    appendLogOnUi("跳过目标 armeabi")
                    continue
                }

                if (!selfAbiList.contains(abi)) {
                    appendLogOnUi("不支持该 ABI，跳过：" + abi)
                    continue
                }

                finalAbiList.add(abi)
            }
        }

        if (finalAbiList.isEmpty()) {
            throw RuntimeException("没有匹配到可解压的 ABI")
        }

        for (abi in finalAbiList) {
            //String assetPath = "lib/" + abi + "/libArkStub.so";
            //File outFile = new File(workDir, "lib/" + abi + "/libArkStub.so");

            val soFileName = getValidSoFileNameFromSettings()
            val outFile = File(workDir, "lib/" + abi + "/" + soFileName)

            val parent = outFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw RuntimeException("创建 so 输出目录失败：" + parent.absolutePath)
            }

            //copyAssetToFile(assetPath, outFile);
            copySelfApkStubSoToFile(abi, outFile)
            //appendLogOnUi("已解压 so：" + outFile.getAbsolutePath());
        }
    }

    private fun readApkAbiList(apkFile: File): ArrayList<String> {
        val abiList = ArrayList<String>()

        val zipFile = ZipFile(apkFile)

        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name

            if (!name.startsWith("lib/")) {
                continue
            }

            val parts = name.split("/")
            if (parts.size < 3) {
                continue
            }

            val abi = parts[1]

            if (!abiList.contains(abi)) {
                abiList.add(abi)
            }
        }

        zipFile.close()
        return abiList
    }

    private fun copyAssetToFile2(assetPath: String, outFile: File) {
        val inputStream = getAssets().open(assetPath)
        val out = FileOutputStream(outFile)

        val buffer = ByteArray(8192)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            out.write(buffer, 0, len)
        }

        out.flush()
        out.close()
        inputStream.close()
    }

    private fun getSelfApkStubAbiList(): ArrayList<String> {
        val abiList = ArrayList<String>()

        val selfApkPath = getApplicationInfo().sourceDir

        val zipFile = ZipFile(selfApkPath)
        val entries = zipFile.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name

            if (!name.startsWith("lib/")) {
                continue
            }

            val parts = name.split("/")
            if (parts.size != 3) {
                continue
            }

            val abi = parts[1]
            val fileName = parts[2]

            if ("armeabi" == abi) {
                continue
            }

            if ("libArkStub.so" != fileName) {
                continue
            }

            if (!abiList.contains(abi)) {
                abiList.add(abi)
            }
        }

        zipFile.close()
        return abiList
    }

    private fun copySelfApkStubSoToFile(abi: String, outFile: File) {
        val selfApkPath = getApplicationInfo().sourceDir
        val zipPath = "lib/" + abi + "/libArkStub.so"

        val zipFile = ZipFile(selfApkPath)
        val entry = zipFile.getEntry(zipPath)

        if (entry == null) {
            zipFile.close()
            throw RuntimeException("自身 APK 中未找到：" + zipPath)
        }

        val inputStream = zipFile.getInputStream(entry)

        val parent = outFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            inputStream.close()
            zipFile.close()
            throw RuntimeException("创建 so 输出目录失败：" + parent.absolutePath)
        }

        val out = FileOutputStream(outFile)

        val buffer = ByteArray(8192)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            out.write(buffer, 0, len)
        }

        out.flush()
        out.close()
        inputStream.close()
        zipFile.close()
    }

    private fun modifyAndroidManifest(apkFile: File, workDir: File): File {
        appendLogOnUi("开始处理 AndroidManifest.xml")

        val manifestAxml = File(workDir, "AndroidManifest_origin.xml")
        val manifestXml = File(workDir, "AndroidManifest_decode.xml")
        val manifestNewXml = File(workDir, "AndroidManifest_modify.xml")
        val manifestNewAxml = File(workDir, "AndroidManifest.xml")

        try {
            val zipFile = ZipFile(apkFile)
            val entry = zipFile.getEntry("AndroidManifest.xml")

            if (entry == null) {
                zipFile.close()
                throw RuntimeException("APK 中未找到 AndroidManifest.xml")
            }

            val inputStream = zipFile.getInputStream(entry)
            val out = FileOutputStream(manifestAxml)

            val buffer = ByteArray(8192)
            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
            }

            out.flush()
            out.close()
            inputStream.close()
            zipFile.close()

            appendLogOnUi("已提取 AndroidManifest.xml")

            Xml2AxmlTool.decode(
                manifestAxml.absolutePath,
                manifestXml.absolutePath
            )

            //appendLogOnUi("Manifest 反编译完成");

            val factory = DocumentBuilderFactory.newInstance()
            factory.setNamespaceAware(true)

            val builder = factory.newDocumentBuilder()
            val document = builder.parse(manifestXml)

            var manifest = document.documentElement
            if (manifest == null || "manifest" != manifest.nodeName) {
                throw RuntimeException("Manifest XML 结构异常")
            }

            var application: Element? = null
            for (i in 0 until manifest.childNodes.length) {
                val item = manifest.childNodes.item(i)
                if (item is Element) {
                    if ("application" == item.nodeName) {
                        application = item
                        break
                    }
                }
            }

            if (application == null) {
                throw RuntimeException("Manifest 中未找到 application 标签")
            }
            //rewriteManifestRootAttributes(manifest);
            rewriteApplicationAttributes(application)

            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()

            transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")

            transformer.transform(
                DOMSource(document),
                StreamResult(manifestNewXml)
            )

            //appendLogOnUi("Manifest XML 修改完成");

            Xml2AxmlTool.encode2(
                this@MainActivity,
                manifestNewXml.absolutePath,
                manifestNewAxml.absolutePath
            )

            /*Xml2AxmlTool.dumpAxmlForDebug(
                    manifestNewAxml.getAbsolutePath(),
                    msg -> appendLogOnUi(msg)
            );*/

            //appendLogOnUi("Manifest 编译完成：" + manifestNewAxml.getAbsolutePath());

            return manifestNewAxml

        } finally {
            deleteFileQuietly(manifestAxml)
            deleteFileQuietly(manifestXml)
            deleteFileQuietly(manifestNewXml)
        }
    }

    private fun rewriteManifestRootAttributes(manifest: Element) {
        val androidNs = "http://schemas.android.com/apk/res/android"

        val packageName = manifest.getAttribute("package")

        var versionCode = manifest.getAttributeNS(androidNs, "versionCode")
        var versionName = manifest.getAttributeNS(androidNs, "versionName")

        if (versionCode == null || versionCode.isEmpty()) {
            versionCode = manifest.getAttribute("android:versionCode")
        }

        if (versionName == null || versionName.isEmpty()) {
            versionName = manifest.getAttribute("android:versionName")
        }

        val platformBuildVersionCode = manifest.getAttribute("platformBuildVersionCode")
        val platformBuildVersionName = manifest.getAttribute("platformBuildVersionName")

        val compileSdkVersion = manifest.getAttributeNS(androidNs, "compileSdkVersion")
        val compileSdkVersionCodename = manifest.getAttributeNS(androidNs, "compileSdkVersionCodename")

        val attrMap = manifest.attributes
        val oldAttrs = ArrayList<Attr>()

        for (i in 0 until attrMap.length) {
            val node = attrMap.item(i)
            if (node is Attr) {
                val attr = node as Attr
                oldAttrs.add(attr)
            }
        }

        while (manifest.attributes.length > 0) {
            val node = manifest.attributes.item(0)
            manifest.removeAttributeNode(node as Attr)
        }

        if (packageName != null && packageName.isNotEmpty()) {
            manifest.setAttribute("package", packageName)
        }

        if (versionCode != null && versionCode.isNotEmpty()) {
            manifest.setAttributeNS(androidNs, "android:versionCode", versionCode)
        }

        if (versionName != null && versionName.isNotEmpty()) {
            manifest.setAttributeNS(androidNs, "android:versionName", versionName)
        }

        if (platformBuildVersionCode != null && platformBuildVersionCode.isNotEmpty()) {
            manifest.setAttribute("platformBuildVersionCode", platformBuildVersionCode)
        }

        if (platformBuildVersionName != null && platformBuildVersionName.isNotEmpty()) {
            manifest.setAttribute("platformBuildVersionName", platformBuildVersionName)
        }

        if (compileSdkVersion != null && compileSdkVersion.isNotEmpty()) {
            manifest.setAttributeNS(androidNs, "android:compileSdkVersion", compileSdkVersion)
        }

        if (compileSdkVersionCodename != null && compileSdkVersionCodename.isNotEmpty()) {
            manifest.setAttributeNS(androidNs, "android:compileSdkVersionCodename", compileSdkVersionCodename)
        }

        for (attr in oldAttrs) {
            val name = attr.name

            if ("package" == name
                || "platformBuildVersionCode" == name
                || "platformBuildVersionName" == name
                || "android:versionCode" == name
                || "android:versionName" == name
                || "versionCode" == name
                || "versionName" == name
                || "android:compileSdkVersion" == name
                || "android:compileSdkVersionCodename" == name
                || "compileSdkVersion" == name
                || "compileSdkVersionCodename" == name) {
                continue
            }

            if (attr.namespaceURI != null && attr.namespaceURI.isNotEmpty()) {
                manifest.setAttributeNS(attr.namespaceURI, attr.name, attr.value)
            } else {
                manifest.setAttribute(attr.name, attr.value)
            }
        }
    }

    private fun rewriteApplicationAttributes(application: Element) {
        val androidNs = "http://schemas.android.com/apk/res/android"

        val oldAttrs = ArrayList<Attr>()

        val attrMap = application.attributes
        for (i in 0 until attrMap.length) {
            val node = attrMap.item(i)
            if (node is Attr) {
                val attr = node as Attr
                val name = attr.name

                if ("android:name" == name
                    || "android:extractNativeLibs" == name
                    || "name" == name
                    || "extractNativeLibs" == name) {
                    continue
                }

                oldAttrs.add(attr)
            }
        }

        while (application.attributes.length > 0) {
            val node = application.attributes.item(0)
            application.removeAttributeNode(node as Attr)
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

            if ("android:label" == attrName) {
                hasLabelWritten = true
            }

            if ("android:icon" == attrName) {
                hasIconWritten = true
            }

            if (!inserted && hasLabelWritten && hasIconWritten) {
                application.setAttributeNS(
                    androidNs,
                    "android:name",
                    getValidStubClassNameFromSettings()
                )

                application.setAttributeNS(
                    androidNs,
                    "android:extractNativeLibs",
                    "true"
                )

                inserted = true
            }
        }

        if (!inserted) {
            application.setAttributeNS(
                androidNs,
                "android:name",
                getValidStubClassNameFromSettings()
            )

            application.setAttributeNS(
                androidNs,
                "android:extractNativeLibs",
                "true"
            )
        }

        //appendLogOnUi("application 属性已按顺序重写");
    }

    private fun deleteFileQuietly(file: File?) {
        if (file == null) {
            return
        }

        try {
            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    //appendLogOnUi("已删除临时文件：" + file.getName());
                }
            }
        } catch (_: Exception) {
        }
    }

    /*private File removeOriginalDexFromApk(File apkFile, File workDir) throws Exception {
        appendLogOnUi("开始删除原 APK 中已加密 dex");

        Set<String> removeDexNames = new HashSet<>();

        ZipFile checkZip = new ZipFile(apkFile);
        for (int i = 1; ; i++) {
            String dexName = i == 1 ? "classes.dex" : "classes" + i + ".dex";
            ZipEntry entry = checkZip.getEntry(dexName);

            if (entry == null) {
                //appendLogOnUi("未找到 " + dexName + "，停止记录 dex 删除列表");
                break;
            }

            removeDexNames.add(dexName);
            appendLogOnUi("加入删除列表：" + dexName);
        }
        checkZip.close();

        if (removeDexNames.isEmpty()) {
            throw new RuntimeException("原 APK 中没有找到需要删除的 dex");
        }

        File outApk = new File(workDir, "已删除原dex.apk");

        ZipFile zipFile = new ZipFile(apkFile);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outApk));
        zos.setLevel(6);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        byte[] buffer = new byte[8192];

        while (entries.hasMoreElements()) {
            ZipEntry oldEntry = entries.nextElement();
            String name = oldEntry.getName();

            if (removeDexNames.contains(name)) {
                //appendLogOnUi("已删除原 dex：" + name);
                continue;
            }

            InputStream in = zipFile.getInputStream(oldEntry);

            byte[] data = readAllBytes(in);

            ZipEntry newEntry = new ZipEntry(name);
            newEntry.setTime(oldEntry.getTime());

            if (oldEntry.getComment() != null) {
                newEntry.setComment(oldEntry.getComment());
            }

            if (shouldStoreEntry(name, oldEntry)) {
                newEntry.setMethod(ZipEntry.STORED);
                newEntry.setSize(data.length);
                newEntry.setCompressedSize(data.length);

                CRC32 crc32 = new CRC32();
                crc32.update(data);
                newEntry.setCrc(crc32.getValue());
            } else {
                newEntry.setMethod(ZipEntry.DEFLATED);
            }

            zos.putNextEntry(newEntry);
            zos.write(data);
            zos.closeEntry();
        }

        zos.finish();
        zos.close();
        zipFile.close();

        //appendLogOnUi("删除原 dex 完成：" + outApk.getAbsolutePath());
        return outApk;
    }*/

    private fun shouldStoreEntry(name: String, oldEntry: ZipEntry): Boolean {
        if (oldEntry.method == ZipEntry.STORED) {
            return true
        }

        val lower = name.lowercase()

        return lower.endsWith(".arsc")
                || lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp")
                || lower.endsWith(".mp3")
                || lower.endsWith(".mp4")
                || lower.endsWith(".ogg")
                || lower.endsWith(".wav")
    }

    private fun rebuildProtectedApk2(apkFile: File, workDir: File, originalApkName: String): File {
        appendLogOnUi("开始重打包 APK")

        val newClassesDex = File(workDir, "classes.dex")
        val newManifest = File(workDir, "AndroidManifest.xml")
        val libDir = File(workDir, "lib")

        if (!newClassesDex.exists()) {
            throw RuntimeException("未找到新的 classes.dex")
        }

        if (!newManifest.exists()) {
            throw RuntimeException("未找到修改后的 AndroidManifest.xml")
        }

        val skipNames = HashSet<String>()

        val checkZip = ZipFile(apkFile)
        var dexIndex = 1
        while (true) {
            val dexName = if (dexIndex == 1) "classes.dex" else "classes$dexIndex.dex"
            val entry = checkZip.getEntry(dexName)

            if (entry == null) {
                //appendLogOnUi("未找到 " + dexName + "，停止记录原 dex");
                break
            }

            skipNames.add(dexName)
            //appendLogOnUi("将删除原 dex：" + dexName);
            dexIndex++
        }
        checkZip.close()

        skipNames.add("AndroidManifest.xml")

        if (libDir.exists() && libDir.isDirectory()) {
            collectLibSkipNames(libDir, libDir, skipNames)
        }

        //File outApk = new File(workDir, "已加固.apk");
        //File outApk = new File(getFinalOutputDir(workDir), "已加固.apk");
        val outApk = File(
            getFinalOutputDir(workDir),
            buildProtectedApkName(originalApkName)
        )

        val zipFile = ZipFile(apkFile)
        val zos = ZipOutputStream(FileOutputStream(outApk))
        zos.setLevel(9)

        val entries = zipFile.entries()

        while (entries.hasMoreElements()) {
            val oldEntry = entries.nextElement()
            val name = oldEntry.name

            if (skipNames.contains(name)) {
                //appendLogOnUi("跳过原文件：" + name);
                continue
            }

            val inputStream = zipFile.getInputStream(oldEntry)
            val data = readAllBytes(inputStream)

            addZipEntry(zos, name, data, oldEntry)
        }

        zipFile.close()

        //addZipEntryStored(zos, "classes.dex", readAllBytes(newClassesDex));
        addZipEntry(zos, "classes.dex", readAllBytes(newClassesDex), null)
        appendLogOnUi("已写入新 classes.dex")

        addZipEntry(zos, "AndroidManifest.xml", readAllBytes(newManifest), null)
        appendLogOnUi("已写入新 AndroidManifest.xml")

        if (libDir.exists() && libDir.isDirectory()) {
            addLibDirToZip(zos, libDir, libDir)
        }

        zos.finish()
        zos.close()

        appendLogOnUi("重打包完成：" + outApk.absolutePath)
        return outApk
    }

    private fun getFileNameFromUri(uri: Uri?): String? {
        if (uri == null) {
            return null
        }

        var result: String? = null

        var cursor: android.database.Cursor? = null
        try {
            cursor = contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }

        if (result == null || result.trim().isEmpty()) {
            val path = uri.path
            if (path != null) {
                val index = path.lastIndexOf('/')
                if (index >= 0 && index < path.length - 1) {
                    result = path.substring(index + 1)
                }
            }
        }

        return result
    }

    private fun buildProtectedApkName(originalName: String?): String {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "已加固.apk"
        }

        val name = originalName.trim()

        if (name.lowercase().endsWith(".apk")) {
            return name.substring(0, name.length - 4) + "(已加固).apk"
        }

        return name + "(已加固).apk"
    }

    private fun getFinalOutputDir(fallbackDir: File): File {
        try {
            val settings = readArkSettings()

            if (settings != null && settings.savePath != null) {
                val saveDir = File(settings.savePath.trim())

                if (!saveDir.exists()) {
                    saveDir.mkdirs()
                }

                if (saveDir.exists() && saveDir.isDirectory() && saveDir.canWrite()) {
                    return saveDir
                }
            }
        } catch (e: Exception) {
            appendLogOnUi("读取输出目录设置失败，使用默认目录：" + e.message)
        }

        return fallbackDir
    }

    private fun addLibDirToZip(zos: ZipOutputStream, rootLibDir: File, current: File) {
        val files = current.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory()) {
                addLibDirToZip(zos, rootLibDir, file)
            } else {
                val relative = getRelativePath(rootLibDir, file)
                val zipName = "lib/" + relative

                //addZipEntryStored(zos, zipName, readAllBytes(file));//写入不压缩的so
                addZipEntry(zos, zipName, readAllBytes(file), null)
                appendLogOnUi("已写入 so：" + zipName)
            }
        }
    }

    private fun getRelativePath(root: File, file: File): String {
        val rootPath = root.absolutePath
        val filePath = file.absolutePath

        var relative = filePath.substring(rootPath.length)

        if (relative.startsWith("/") || relative.startsWith("\\")) {
            relative = relative.substring(1)
        }

        return relative.replace("\\", "/")
    }

    private fun addZipEntry(zos: ZipOutputStream, name: String, data: ByteArray, oldEntry: ZipEntry?) {
        val newEntry = ZipEntry(name)

        if (oldEntry != null) {
            newEntry.time = oldEntry.time

            if (oldEntry.comment != null) {
                newEntry.comment = oldEntry.comment
            }
        }

        if (oldEntry != null && shouldStoreEntry(name, oldEntry)) {
            newEntry.method = ZipEntry.STORED
            newEntry.size = data.size.toLong()
            newEntry.compressedSize = data.size.toLong()

            val crc32 = CRC32()
            crc32.update(data)
            newEntry.crc = crc32.value
        } else {
            newEntry.method = ZipEntry.DEFLATED
        }

        zos.putNextEntry(newEntry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun collectLibSkipNames(rootLibDir: File, current: File, skipNames: MutableSet<String>) {
        val files = current.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory()) {
                collectLibSkipNames(rootLibDir, file, skipNames)
            } else {
                val relative = getRelativePath(rootLibDir, file)
                skipNames.add("lib/" + relative)
            }
        }
    }

    private fun addZipEntryStored(zos: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        entry.method = ZipEntry.STORED
        entry.size = data.size.toLong()
        entry.compressedSize = data.size.toLong()

        val crc32 = CRC32()
        crc32.update(data)
        entry.crc = crc32.value

        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun cleanTempFiles(workDir: File) {
        if (workDir == null || !workDir.exists()) {
            return
        }

        deleteFileQuietly(File(workDir, "待加固.apk"))
        deleteFileQuietly(File(workDir, "AndroidManifest.xml"))
        deleteFileQuietly(File(workDir, "AndroidManifest_origin.xml"))
        deleteFileQuietly(File(workDir, "AndroidManifest_decode.xml"))
        deleteFileQuietly(File(workDir, "AndroidManifest_modify.xml"))
        deleteFileQuietly(File(workDir, "classes.dex"))
        deleteDirQuietly(File(workDir, "lib"))

        appendLogOnUi("临时文件清理完成")
    }

    private fun deleteDirQuietly(dir: File?) {
        if (dir == null || !dir.exists()) {
            return
        }

        try {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory()) {
                        deleteDirQuietly(file)
                    } else {
                        deleteFileQuietly(file)
                    }
                }
            }

            if (dir.delete()) {
                //appendLogOnUi("已删除目录：" + dir.getName());
            }
        } catch (_: Exception) {
        }
    }
}
