package com.ark.jiagu;

import static com.ark.jiagu.vm.VmpJiaguEntry.extractOnCreateToBin;
import static com.ark.jiagu.vm.VmpJiaguEntry.parseVmpBinByClassId;
import static com.ark.jiagu.vm.VmpJiaguEntry.printDexlib2Opcodes;
import static com.ark.jiagu.vm.VmpJiaguEntry.printOnCreateExtractInfo;
import static com.ark.jiagu.vm.VmpJiaguEntry.rewriteExtractedMethodsToNativeDex;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.ark.jar.xml2axml.test.Xml2AxmlTool;
import com.ark.jiagu.vm.VmpJiaguEntry;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class MainActivity extends ComponentActivity {
    private NeoArtUiController uiController;
    private static final int REQ_SELECT_APK = 1001;
    private static final String TEMP_DIR_NAME = "ArkJiagu";
    private boolean isPermissionDialogShowing = false;
    private boolean hasInitMain = false;
    private static final String SP_SETTINGS = "ark_settings";
    private static final String KEY_SO_NAME = "so_name";
    private static final String KEY_SAVE_PATH = "save_path";
    private static final String KEY_AUTO_SIGN = "auto_sign";
    private static final String DEFAULT_SO_NAME = "ArkStub";
    private static final String KEY_USE_CUSTOM_JKS = "use_custom_jks";
    private static final String KEY_JKS_PATH = "jks_path";
    private static final String KEY_JKS_STORE_PASS = "jks_store_pass";
    private static final String KEY_JKS_ALIAS = "jks_alias";
    private static final String KEY_JKS_KEY_PASS = "jks_key_pass";
    private static final String KEY_STUB_CLASS_NAME = "stub_class_name";
    private static final String DEFAULT_STUB_CLASS_NAME = "com.ark.safe.StubApp";
    private static final int REQ_STORAGE_PERMISSION = 10086;


    static {
        System.loadLibrary("ArkTool");
    }

    private native byte[] buildEncryptedBlock(byte[] plainData);

    private native byte[] fixDexHeader(byte[] dexData);

    private native boolean isValidDex(byte[] data);

    private native byte[] intToLe4(int value);
    private SoNamePreset[] SO_NAME_PRESETS;
    private native void buildEncryptedShellDex(
            File apkFile,
            File shellDexFile,
            String realApplicationName,
            byte[] signHash64
    ) throws Exception;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        uiController = NeoArtUi.install(
                this,
                this::openApkSelector,
                this::showSettingsDialog
        );


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










        checkPermissionOrShowDialog();
        SO_NAME_PRESETS = loadSoNamePresets();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (hasInitMain) {
            return;
        }

        if (hasAllFilePermission()) {
            initMainPage();
        } else {
            showPermissionDialog();
        }
    }

    private void checkPermissionOrShowDialog() {
        if (hasAllFilePermission()) {
            initMainPage();
            return;
        }

        showPermissionDialog();
    }




    private void showPermissionDialog() {
        if (hasAllFilePermission()) {
            initMainPage();
            return;
        }

        if (isPermissionDialogShowing) {
            return;
        }

        isPermissionDialogShowing = true;

        new android.app.AlertDialog.Builder(this)
                .setTitle("需要文件访问权限")
                .setMessage("本工具需要文件访问权限，才能读取和处理 APK 文件。请点击去授权。")
                .setCancelable(false)
                .setPositiveButton("去授权", (dialog, which) -> {
                    isPermissionDialogShowing = false;
                    dialog.dismiss();
                    openAllFilePermissionPage();
                })
                .show();
    }

    private SoNamePreset[] loadSoNamePresets() {
        java.util.ArrayList<SoNamePreset> list = new java.util.ArrayList<>();

        try {
            java.io.InputStream is = getAssets().open("so_name_presets.json");
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }

            is.close();

            String json = baos.toString("UTF-8");
            org.json.JSONArray array = new org.json.JSONArray(json);

            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);

                String title = obj.optString("title", "").trim();
                String name = obj.optString("name", "").trim();

                if (title.length() == 0 || name.length() == 0) {
                    continue;
                }

                list.add(new SoNamePreset(title, name));
            }

        } catch (Exception e) {
            e.printStackTrace();

            list.add(new SoNamePreset("Ark默认", "ArkStub"));
        }

        return list.toArray(new SoNamePreset[0]);
    }

    private String getValidSoNameFromSettings() {
        try {
            ArkSettings settings = readArkSettings();

            if (settings != null && isValidSoName(settings.soName)) {
                return settings.soName.trim();
            }
        } catch (Exception e) {
            appendLogOnUi("读取so名称设置失败，使用默认名称：" + e.getMessage());
        }

        return DEFAULT_SO_NAME;
    }

    private String getValidSoFileNameFromSettings() {
        return "lib" + getValidSoNameFromSettings() + ".so";
    }

    private void initMainPage() {
        if (hasInitMain) {
            return;
        }

        hasInitMain = true;

        File workDir = getWorkDir();
        cleanWorkDirOnStart(workDir);

        appendLog("加固器初始化完成");
        appendLog("等待选择 APK 文件");
    }
    private void cleanWorkDirOnStart(File workDir) {
        if (workDir == null || !workDir.exists()) {
            return;
        }

        cleanTempFiles(workDir);
    }
    private static class ArkSettings {
        String soName;
        String stubClassName;
        String savePath;
        boolean autoSign;

        boolean useCustomJks;
        String jksPath;
        String jksStorePass;
        String jksAlias;
        String jksKeyPass;

        ArkSettings(
                String soName,
                String stubClassName,
                String savePath,
                boolean autoSign,
                boolean useCustomJks,
                String jksPath,
                String jksStorePass,
                String jksAlias,
                String jksKeyPass
        ) {
            this.soName = soName;
            this.stubClassName = stubClassName;
            this.savePath = savePath;
            this.autoSign = autoSign;
            this.useCustomJks = useCustomJks;
            this.jksPath = jksPath;
            this.jksStorePass = jksStorePass;
            this.jksAlias = jksAlias;
            this.jksKeyPass = jksKeyPass;
        }
    }
    private ArkSettings readArkSettings() {
        android.content.SharedPreferences sp = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE);

        String defaultSavePath = getWorkDir().getAbsolutePath();

        String soName = sp.getString(KEY_SO_NAME, DEFAULT_SO_NAME);
        String stubClassName = sp.getString(KEY_STUB_CLASS_NAME, DEFAULT_STUB_CLASS_NAME);
        String savePath = sp.getString(KEY_SAVE_PATH, defaultSavePath);
        boolean autoSign = sp.getBoolean(KEY_AUTO_SIGN, false);

        boolean useCustomJks = sp.getBoolean(KEY_USE_CUSTOM_JKS, false);
        String jksPath = sp.getString(KEY_JKS_PATH, "");
        String jksStorePass = sp.getString(KEY_JKS_STORE_PASS, "");
        String jksAlias = sp.getString(KEY_JKS_ALIAS, "");
        String jksKeyPass = sp.getString(KEY_JKS_KEY_PASS, "");

        if (soName == null || soName.trim().isEmpty()) {
            soName = DEFAULT_SO_NAME;
        }

        if (stubClassName == null || stubClassName.trim().isEmpty()) {
            stubClassName = DEFAULT_STUB_CLASS_NAME;
        }

        if (savePath == null || savePath.trim().isEmpty()) {
            savePath = defaultSavePath;
        }

        return new ArkSettings(
                soName,
                stubClassName,
                savePath,
                autoSign,
                useCustomJks,
                jksPath == null ? "" : jksPath,
                jksStorePass == null ? "" : jksStorePass,
                jksAlias == null ? "" : jksAlias,
                jksKeyPass == null ? "" : jksKeyPass
        );
    }
    private void saveArkSettings(
            String soName,
            String stubClassName,
            String savePath,
            boolean autoSign,
            boolean useCustomJks,
            String jksPath,
            String jksStorePass,
            String jksAlias,
            String jksKeyPass
    ) {
        android.content.SharedPreferences sp = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE);

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
                .apply();
    }

    private String getValidStubClassNameFromSettings() {
        try {
            ArkSettings settings = readArkSettings();

            if (settings != null
                    && settings.stubClassName != null
                    && !settings.stubClassName.trim().isEmpty()) {
                return settings.stubClassName.trim();
            }
        } catch (Exception ignored) {
        }

        return DEFAULT_STUB_CLASS_NAME;
    }

    private boolean isValidJksSettings(String jksPath, String storePass, String alias, String keyPass) {
        if (jksPath == null || jksPath.trim().isEmpty()) {
            Toast.makeText(this, "JKS证书路径不能为空", Toast.LENGTH_LONG).show();
            return false;
        }

        File jksFile = new File(jksPath.trim());
        if (!jksFile.exists() || !jksFile.isFile()) {
            Toast.makeText(this, "JKS证书文件不存在", Toast.LENGTH_LONG).show();
            return false;
        }

        if (storePass == null || storePass.trim().isEmpty()) {
            Toast.makeText(this, "证书密码不能为空", Toast.LENGTH_LONG).show();
            return false;
        }

        if (alias == null || alias.trim().isEmpty()) {
            Toast.makeText(this, "别名不能为空", Toast.LENGTH_LONG).show();
            return false;
        }

        if (keyPass == null || keyPass.trim().isEmpty()) {
            Toast.makeText(this, "别名密码不能为空", Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }
    private boolean isValidSoName(String soName) {
        if (soName == null) {
            return false;
        }

        soName = soName.trim();

        if (soName.isEmpty()) {
            return false;
        }

        if (soName.startsWith("lib")) {
            return false;
        }

        if (soName.endsWith(".so")) {
            return false;
        }

        return soName.matches("[A-Za-z0-9_]+");
    }

    private boolean isValidSavePath(String savePath) {
        if (savePath == null || savePath.trim().isEmpty()) {
            return false;
        }

        File dir = new File(savePath.trim());

        if (dir.exists()) {
            return dir.isDirectory() && dir.canWrite();
        }

        return dir.mkdirs() && dir.isDirectory() && dir.canWrite();
    }

    private boolean isValidStubClassName(String className) {
        if (className == null) {
            return false;
        }

        className = className.trim();

        if (className.isEmpty()) {
            return false;
        }

        if (className.startsWith(".")) {
            return false;
        }

        if (className.endsWith(".")) {
            return false;
        }

        if (className.contains("..")) {
            return false;
        }

        String[] parts = className.split("\\.");

        if (parts.length < 2) {
            return false;
        }

        for (String part : parts) {

            if (part == null || part.isEmpty()) {
                return false;
            }

            char first = part.charAt(0);

            // 首字符不能数字
            if (Character.isDigit(first)) {
                return false;
            }

            // 首字符必须是合法Java标识符开始
            if (!Character.isJavaIdentifierStart(first)) {
                return false;
            }

            for (int i = 1; i < part.length(); i++) {
                if (!Character.isJavaIdentifierPart(part.charAt(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        android.widget.EditText etSoName = dialogView.findViewById(R.id.etSoName);
        android.widget.EditText etSavePath = dialogView.findViewById(R.id.etSavePath);
        android.widget.Switch swAutoSign = dialogView.findViewById(R.id.swAutoSign);

        android.widget.Switch swUseCustomJks = dialogView.findViewById(R.id.swUseCustomJks);
        android.widget.LinearLayout llCustomJksForm = dialogView.findViewById(R.id.llCustomJksForm);
        android.widget.EditText etJksPath = dialogView.findViewById(R.id.etJksPath);
        android.widget.EditText etJksStorePass = dialogView.findViewById(R.id.etJksStorePass);
        android.widget.EditText etJksAlias = dialogView.findViewById(R.id.etJksAlias);
        android.widget.EditText etJksKeyPass = dialogView.findViewById(R.id.etJksKeyPass);
        android.widget.ImageButton btnSoNamePreset = dialogView.findViewById(R.id.btnSoNamePreset);
        Button btnSaveSettings = dialogView.findViewById(R.id.btnSaveSettings);
        android.widget.EditText etStubClassName = dialogView.findViewById(R.id.etStubClassName);
        android.widget.ImageButton btnClearStubClassName = dialogView.findViewById(R.id.btnClearStubClassName);
        android.widget.ImageButton btnClearSavePath = dialogView.findViewById(R.id.btnClearSavePath);


        ArkSettings settings = readArkSettings();
        etStubClassName.setText(settings.stubClassName);
        etSoName.setText(settings.soName);
        etSavePath.setText(settings.savePath);
        swAutoSign.setChecked(settings.autoSign);

        swUseCustomJks.setChecked(settings.useCustomJks);
        etJksPath.setText(settings.jksPath);
        etJksStorePass.setText(settings.jksStorePass);
        etJksAlias.setText(settings.jksAlias);
        etJksKeyPass.setText(settings.jksKeyPass);

        llCustomJksForm.setVisibility(settings.useCustomJks ? View.VISIBLE : View.GONE);

        swUseCustomJks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            llCustomJksForm.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        btnSoNamePreset.setOnClickListener(v -> showSoNamePresetDialog(etSoName));
        btnClearStubClassName.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("确认清空")
                    .setMessage("是否清空自定义壳类名？")
                    .setPositiveButton("确定", (d, w) -> etStubClassName.setText(""))
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnClearSavePath.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("确认清空")
                    .setMessage("是否清空文件保存路径？")
                    .setPositiveButton("确定", (d, w) -> etSavePath.setText(""))
                    .setNegativeButton("取消", null)
                    .show();
        });
        btnSaveSettings.setOnClickListener(v -> {
            String soName = etSoName.getText().toString().trim();
            String stubClassName = etStubClassName.getText().toString().trim();
            String savePath = etSavePath.getText().toString().trim();
            boolean autoSign = swAutoSign.isChecked();

            boolean useCustomJks = swUseCustomJks.isChecked();
            String jksPath = etJksPath.getText().toString().trim();
            String jksStorePass = etJksStorePass.getText().toString();
            String jksAlias = etJksAlias.getText().toString().trim();
            String jksKeyPass = etJksKeyPass.getText().toString();

            if (!isValidSoName(soName)) {
                Toast.makeText(this, "so名称不合法，只能使用字母、数字、下划线，不要带lib和.so", Toast.LENGTH_LONG).show();
                return;
            }
            if (!isValidStubClassName(stubClassName)) {
                new android.app.AlertDialog.Builder(this)
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
                        .show();
                return;
            }
            if (!isValidSavePath(savePath)) {
                Toast.makeText(this, "文件保存路径无效或不可写", Toast.LENGTH_LONG).show();
                return;
            }

            if (useCustomJks && !isValidJksSettings(jksPath, jksStorePass, jksAlias, jksKeyPass)) {
                return;
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
            );

            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
    private static class SoNamePreset {
        String feature;
        String soName;

        SoNamePreset(String feature, String soName) {
            this.feature = feature;
            this.soName = soName;
        }
    }


    private void showSoNamePresetDialog(android.widget.EditText etSoName) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_so_name_preset, null);

        android.widget.RadioGroup rgSoNamePreset = dialogView.findViewById(R.id.rgSoNamePreset);
        Button btnConfirmSoNamePreset = dialogView.findViewById(R.id.btnConfirmSoNamePreset);

        for (int i = 0; i < SO_NAME_PRESETS.length; i++) {
            SoNamePreset item = SO_NAME_PRESETS[i];

            android.widget.RadioButton radioButton = new android.widget.RadioButton(this);
            radioButton.setId(10000 + i);
            radioButton.setText("特征：" + item.feature + "\nso名称：" + item.soName);
            radioButton.setTextColor(android.graphics.Color.parseColor("#374151"));
            radioButton.setTextSize(14);
            radioButton.setPadding(8, 10, 8, 10);
            radioButton.setSingleLine(false);

            rgSoNamePreset.addView(radioButton);
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnConfirmSoNamePreset.setOnClickListener(v -> {
            int checkedId = rgSoNamePreset.getCheckedRadioButtonId();

            if (checkedId == -1) {
                Toast.makeText(this, "请选择一个预设so名称", Toast.LENGTH_SHORT).show();
                return;
            }

            int index = checkedId - 10000;

            if (index < 0 || index >= SO_NAME_PRESETS.length) {
                Toast.makeText(this, "选择项无效", Toast.LENGTH_SHORT).show();
                return;
            }

            etSoName.setText(SO_NAME_PRESETS[index].soName);
            etSoName.setSelection(etSoName.getText().length());

            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }




    private File getWorkDir() {
        return new File(Environment.getExternalStorageDirectory(), TEMP_DIR_NAME);
    }
    private boolean hasAllFilePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }
    private void openAllFilePermissionPage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    REQ_STORAGE_PERMISSION
            );
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_STORAGE_PERMISSION) {
            if (hasAllFilePermission()) {
                initMainPage();
            } else {
                Toast.makeText(this, "未授予文件访问权限", Toast.LENGTH_LONG).show();
                showPermissionDialog();
            }
        }
    }
    private void appendLog(String text) {
        if (uiController == null) {
            return;
        }
        uiController.appendLog(text);
    }

    /**
     * 在指定目录生成壳 classes.dex 此版本是attach间接调用
     */
    private File generateShellDex2(File outputDir) throws Exception {
        if (outputDir == null) {
            throw new IllegalArgumentException("输出目录为空");
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new RuntimeException("创建输出目录失败：" + outputDir.getAbsolutePath());
        }

        File outputDex = new File(outputDir, "classes.dex");

        String customStubClassName = getValidStubClassNameFromSettings();

        String stubClass = "L" + customStubClassName.replace('.', '/') + ";";
        String applicationClass = "Landroid/app/Application;";
        String contextClass = "Landroid/content/Context;";

        DexPool dexPool = new DexPool(Opcodes.getDefault());

        ImmutableMethod clinitMethod = new ImmutableMethod(
                stubClass,
                "<clinit>",
                Collections.<ImmutableMethodParameter>emptyList(),
                "V",
                AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                Collections.emptySet(),
                null,
                new ImmutableMethodImplementation(
                        2,
                        Arrays.asList(
                                new ImmutableInstruction21c(
                                        Opcode.CONST_STRING,
                                        0,
                                        new ImmutableStringReference("ark")
                                ),
                                new ImmutableInstruction21c(
                                        Opcode.CONST_STRING,
                                        1,
                                        new ImmutableStringReference(customStubClassName)
                                ),
                                new ImmutableInstruction35c(
                                        Opcode.INVOKE_STATIC,
                                        2,
                                        0,
                                        1,
                                        0,
                                        0,
                                        0,
                                        new ImmutableMethodReference(
                                                "Ljava/lang/System;",
                                                "setProperty",
                                                Arrays.asList(
                                                        "Ljava/lang/String;",
                                                        "Ljava/lang/String;"
                                                ),
                                                "Ljava/lang/String;"
                                        )
                                ),
                                new ImmutableInstruction11x(
                                        Opcode.MOVE_RESULT_OBJECT,
                                        0
                                ),
                                new ImmutableInstruction21c(
                                        Opcode.CONST_STRING,
                                        0,
                                        new ImmutableStringReference(getValidSoNameFromSettings())
                                ),
                                new ImmutableInstruction35c(
                                        Opcode.INVOKE_STATIC,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        new ImmutableMethodReference(
                                                "Ljava/lang/System;",
                                                "loadLibrary",
                                                Collections.singletonList("Ljava/lang/String;"),
                                                "V"
                                        )
                                ),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        Collections.emptyList(),
                        Collections.emptyList()
                )
        );

        ImmutableMethod initMethod = new ImmutableMethod(
                stubClass,
                "<init>",
                Collections.<ImmutableMethodParameter>emptyList(),
                "V",
                AccessFlags.PUBLIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                Collections.emptySet(),
                null,
                new ImmutableMethodImplementation(
                        1,
                        Arrays.asList(
                                new ImmutableInstruction35c(
                                        Opcode.INVOKE_DIRECT,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        new ImmutableMethodReference(
                                                applicationClass,
                                                "<init>",
                                                Collections.<String>emptyList(),
                                                "V"
                                        )
                                ),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        Collections.emptyList(),
                        Collections.emptyList()
                )
        );

        ImmutableMethod dtcLoaderMethod = new ImmutableMethod(
                stubClass,
                "DtcLoader",
                Collections.singletonList(
                        new ImmutableMethodParameter(
                                contextClass,
                                Collections.emptySet(),
                                null
                        )
                ),
                "V",
                AccessFlags.PRIVATE.getValue()
                        | AccessFlags.STATIC.getValue()
                        | AccessFlags.NATIVE.getValue(),
                Collections.emptySet(),
                null,
                null
        );

        ImmutableMethod attachBaseContextMethod = new ImmutableMethod(
                stubClass,
                "attachBaseContext",
                Collections.singletonList(
                        new ImmutableMethodParameter(
                                contextClass,
                                Collections.emptySet(),
                                null
                        )
                ),
                "V",
                AccessFlags.PROTECTED.getValue(),
                Collections.emptySet(),
                null,
                new ImmutableMethodImplementation(
                        2,
                        Arrays.asList(
                                new ImmutableInstruction35c(
                                        Opcode.INVOKE_SUPER,
                                        2,
                                        0,
                                        1,
                                        0,
                                        0,
                                        0,
                                        new ImmutableMethodReference(
                                                applicationClass,
                                                "attachBaseContext",
                                                Collections.singletonList(contextClass),
                                                "V"
                                        )
                                ),
                                new ImmutableInstruction35c(
                                        Opcode.INVOKE_STATIC,
                                        1,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        new ImmutableMethodReference(
                                                stubClass,
                                                "DtcLoader",
                                                Collections.singletonList(contextClass),
                                                "V"
                                        )
                                ),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        Collections.emptyList(),
                        Collections.emptyList()
                )
        );

        ImmutableClassDef classDef = new ImmutableClassDef(
                stubClass,
                AccessFlags.PUBLIC.getValue(),
                applicationClass,
                Collections.<String>emptyList(),
                "StubApp.java",
                Collections.emptySet(),
                Collections.emptyList(),
                Arrays.asList(
                        clinitMethod,
                        initMethod,
                        dtcLoaderMethod,
                        attachBaseContextMethod
                )
        );

        dexPool.internClass(classDef);
        dexPool.writeTo(new FileDataStore(outputDex));

        return outputDex;
    }

    /**
     * 在指定目录生成壳 classes.dex 此版本是attach直接调用
     */
    private File generateShellDex(File outputDir) throws Exception {
        if (outputDir == null) {
            throw new IllegalArgumentException("输出目录为空");
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new RuntimeException("创建输出目录失败：" + outputDir.getAbsolutePath());
        }

        File outputDex = new File(outputDir, "classes.dex");

        String customStubClassName = getValidStubClassNameFromSettings();

        String stubClass = "L" + customStubClassName.replace('.', '/') + ";";
        String applicationClass = "Landroid/app/Application;";
        String contextClass = "Landroid/content/Context;";

        DexPool dexPool = new DexPool(Opcodes.getDefault());

        ImmutableMethod clinitMethod = new ImmutableMethod(
                stubClass,
                "<clinit>",
                Collections.<ImmutableMethodParameter>emptyList(),
                "V",
                AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                Collections.emptySet(),
                null,
                new ImmutableMethodImplementation(
                        2,
                        Arrays.asList(
                                new ImmutableInstruction21c(
                                        Opcode.CONST_STRING,
                                        0,
                                        new ImmutableStringReference("ark")
                                ),
                                new ImmutableInstruction21c(
                                        Opcode.CONST_STRING,
                                        1,
                                        new ImmutableStringReference(customStubClassName)
                                ),
                                new ImmutableInstruction35c(
                                        Opcode.INVOKE_STATIC,
                                        2,
                                        0,
                                        1,
                                        0,
                                        0,
                                        0,
                                        new ImmutableMethodReference(
                                                "Ljava/lang/System;",
                                                "setProperty",
                                                Arrays.asList(
                                                        "Ljava/lang/String;",
                                                        "Ljava/lang/String;"
                                                ),
                                                "Ljava/lang/String;"
                                        )
                                ),
                                new ImmutableInstruction21c(
                                        Opcode.CONST_STRING,
                                        0,
                                        new ImmutableStringReference(getValidSoNameFromSettings())
                                ),
                                new ImmutableInstruction35c(
                                        Opcode.INVOKE_STATIC,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        new ImmutableMethodReference(
                                                "Ljava/lang/System;",
                                                "loadLibrary",
                                                Collections.singletonList("Ljava/lang/String;"),
                                                "V"
                                        )
                                ),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        Collections.emptyList(),
                        Collections.emptyList()
                )
        );

        ImmutableMethod initMethod = new ImmutableMethod(
                stubClass,
                "<init>",
                Collections.<ImmutableMethodParameter>emptyList(),
                "V",
                AccessFlags.PUBLIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                Collections.emptySet(),
                null,
                new ImmutableMethodImplementation(
                        1,
                        Arrays.asList(
                                new ImmutableInstruction35c(
                                        Opcode.INVOKE_DIRECT,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        new ImmutableMethodReference(
                                                applicationClass,
                                                "<init>",
                                                Collections.<String>emptyList(),
                                                "V"
                                        )
                                ),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        Collections.emptyList(),
                        Collections.emptyList()
                )
        );

        ImmutableMethod attachBaseContextMethod = new ImmutableMethod(
                stubClass,
                "attachBaseContext",
                Collections.singletonList(
                        new ImmutableMethodParameter(
                                contextClass,
                                Collections.emptySet(),
                                null
                        )
                ),
                "V",
                AccessFlags.PROTECTED.getValue() | AccessFlags.NATIVE.getValue(),
                Collections.emptySet(),
                null,
                null
        );

        ImmutableClassDef classDef = new ImmutableClassDef(
                stubClass,
                AccessFlags.PUBLIC.getValue(),
                applicationClass,
                Collections.<String>emptyList(),
                "StubApp.java",
                Collections.emptySet(),
                Collections.emptyList(),
                Arrays.asList(
                        clinitMethod,
                        initMethod,
                        attachBaseContextMethod
                )
        );

        dexPool.internClass(classDef);
        dexPool.writeTo(new FileDataStore(outputDex));

        return outputDex;
    }

    private void openApkSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        startActivityForResult(intent, REQ_SELECT_APK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SELECT_APK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                appendLog("选择文件失败：Uri为空");
                return;
            }

            handleSelectedApk(uri);
        }
    }

    private void handleSelectedApk(Uri uri) {
        uiController.setSelectEnabled(false);

        new Thread(() -> {
            File workDir = getWorkDir();
            try {
                appendLogOnUi("开始处理 APK");


                if (!workDir.exists() && !workDir.mkdirs()) {
                    throw new RuntimeException("创建临时目录失败：" + workDir.getAbsolutePath());
                }

                appendLogOnUi("临时目录：" + workDir.getAbsolutePath());
                String originalApkName = getFileNameFromUri(uri);
                originalApkName = ApkValidator.sanitizeApkFileName(originalApkName);
                File copiedApk = new File(workDir, "待加固.apk");
                copyUriToFile(uri, copiedApk);
                ApkValidator.validate(copiedApk);
                appendLogOnUi("APK 结构校验通过");

                String appName = readApplicationName(copiedApk);
                appendLogOnUi("原始入口：" + appName);

                File shellDex = generateShellDex(workDir);
                //appendLogOnUi("壳已生成：" + shellDex.getAbsolutePath());

                byte[] signHash64 = getSignHash64ForShell();//读取64位证书hash


                buildEncryptedShellDex(copiedApk, shellDex, appName, signHash64);
                appendLogOnUi("加密完成：" + shellDex.getAbsolutePath());

                extractStubSoByTargetAbi(copiedApk, workDir);
                //appendLogOnUi("壳 so 解压完成");

                File newManifest = modifyAndroidManifest(copiedApk, workDir);
                //appendLogOnUi("修改后的 Manifest：" + newManifest.getAbsolutePath());

                //File protectedApk = rebuildProtectedApk(copiedApk, workDir);
                File protectedApk = rebuildProtectedApk(copiedApk, workDir, originalApkName);

                appendLogOnUi("开始进行 ZIPALIGN");
                protectedApk = zipAlignApk(protectedApk);
                appendLogOnUi("ZIPALIGN 完成");

                ArkSettings settings = readArkSettings();
                boolean autoSign = settings != null && settings.autoSign;
                if (autoSign) {
                    appendLogOnUi("检测到已开启自动签名");

                    if (settings.useCustomJks) {
                        protectedApk = ApkSignUtil.signApk(
                                MainActivity.this,
                                protectedApk,
                                new File(settings.jksPath),
                                settings.jksStorePass,
                                settings.jksAlias,
                                settings.jksKeyPass
                        );
                    } else {
                        protectedApk = ApkSignUtil.signApk(MainActivity.this, protectedApk);
                    }

                    appendLogOnUi("APK 签名完成");
                } else {
                    appendLogOnUi("未开启自动签名，跳过签名");
                }

                appendLogOnUi("加固包输出：" + protectedApk.getAbsolutePath());

                //cleanTempFiles(workDir);
                appendLogOnUi("----------->>>加固完成<<<-----------");
                File finalProtectedApk = protectedApk;
                runOnUiThread(() -> {
                    android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("加固完成")
                            .setMessage("APK 已加固完成，是否立即安装？")
                            .setPositiveButton("安装", null)
                            .setNegativeButton("取消", (d, w) -> d.dismiss())
                            .setCancelable(false)
                            .create();

                    dialog.show();

                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener(v -> {
                                installApk(finalProtectedApk);

                                // 不关闭弹窗
                            });
                });

            } catch (Exception e) {
                appendLogOnUi("处理失败：" + e.getMessage());
            } finally {
                cleanTempFiles(workDir);
                runOnUiThread(() -> uiController.setSelectEnabled(true));
            }
        }).start();
    }

    private byte[] getSignHash64ForShell() {
        //由于已移除签名校验的c代码，因此这里不能再返回证书信息，否则so层无法解密
        return null;

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

    private void installApk(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, "APK文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 8.0+ 需要检查“安装未知应用”权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("需要安装权限")
                        .setMessage("请先允许本应用安装未知来源应用")
                        .setPositiveButton("去授权", (dialog, which) -> {
                            dialog.dismiss();

                            Intent intent = new Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                            );
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return;
            }
        }

        doInstallApk(apkFile);
    }

    private void doInstallApk(File apkFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        android.net.Uri apkUri;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            apkUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    apkFile
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = android.net.Uri.fromFile(apkFile);
        }

        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        startActivity(intent);
    }

    private File zipAlignApk(File inputApk) throws Exception {
        if (inputApk == null || !inputApk.exists()) {
            throw new RuntimeException("待对齐 APK 不存在");
        }

        File parentDir = inputApk.getParentFile();
        if (parentDir == null || !parentDir.exists()) {
            throw new RuntimeException("APK 所在目录不存在");
        }

        File alignedApk = new File(parentDir, inputApk.getName() + ".aligning");

        deleteFileQuietly(alignedApk);

        boolean success = ZipAlign.doZipAlign(
                inputApk.getAbsolutePath(),
                alignedApk.getAbsolutePath(),
                4,
                true,
                true
        );

        if (!success || !alignedApk.exists()) {
            throw new RuntimeException("zipalign 对齐失败");
        }

        boolean verified = ZipAlign.isZipAligned(
                alignedApk.getAbsolutePath(),
                4,
                true
        );

        if (!verified) {
            deleteFileQuietly(alignedApk);
            throw new RuntimeException("zipalign 校验失败");
        }

        if (!inputApk.delete()) {
            deleteFileQuietly(alignedApk);
            throw new RuntimeException("删除原 APK 失败");
        }

        if (!alignedApk.renameTo(inputApk)) {
            deleteFileQuietly(alignedApk);
            throw new RuntimeException("重命名对齐 APK 失败");
        }

        return inputApk;
    }

    private void appendLogOnUi(String text) {
        System.out.println("[log] " + text);
        runOnUiThread(() -> appendLog(text));
    }

    private void copyUriToFile(Uri uri, File outFile) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new RuntimeException("无法打开输入文件");
            }
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
        }
    }

    private String readApplicationName(File apkFile) {
        PackageManager pm = getPackageManager();

        PackageInfo info = pm.getPackageArchiveInfo(
                apkFile.getAbsolutePath(),
                PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA
        );

        if (info == null || info.applicationInfo == null) {
            return "android.app.Application";
        }

        String className = info.applicationInfo.className;
        if (className == null || className.trim().isEmpty()) {
            return "android.app.Application";
        }

        return className;
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
    private File rebuildProtectedApk(File apkFile, File workDir, String originalApkName) throws Exception {
        appendLogOnUi("开始重打包 APK");

        File newClassesDex = new File(workDir, "classes.dex");
        File newManifest = new File(workDir, "AndroidManifest.xml");
        File libDir = new File(workDir, "lib");

        if (!newClassesDex.exists()) {
            throw new RuntimeException("未找到新的 classes.dex");
        }

        if (!newManifest.exists()) {
            throw new RuntimeException("未找到修改后的 AndroidManifest.xml");
        }

        Set<String> skipNames = new HashSet<>();

        ZipFile checkZip = new ZipFile(apkFile);
        try {
            for (int i = 1; ; i++) {
                String dexName = i == 1 ? "classes.dex" : "classes" + i + ".dex";
                ZipEntry entry = checkZip.getEntry(dexName);

                if (entry == null) {
                    break;
                }

                skipNames.add(dexName);
            }
        } finally {
            checkZip.close();
        }

        skipNames.add("AndroidManifest.xml");

        if (libDir.exists() && libDir.isDirectory()) {
            collectLibSkipNames(libDir, libDir, skipNames);
        }

        File outApk = new File(
                getFinalOutputDir(workDir),
                buildProtectedApkName(originalApkName)
        );

        ZipFile zipFile = new ZipFile(apkFile);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outApk));

        try {
            zos.setLevel(9);

            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry oldEntry = entries.nextElement();
                String name = oldEntry.getName();

                if (skipNames.contains(name)) {
                    continue;
                }

                if (oldEntry.isDirectory()) {
                    addDirectoryZipEntry(zos, name, oldEntry);
                    continue;
                }

                InputStream in = zipFile.getInputStream(oldEntry);
                addZipEntryStream(zos, name, in, oldEntry);
            }

            addZipEntryStream(zos, "classes.dex", new FileInputStream(newClassesDex), null);
            appendLogOnUi("已写入新 classes.dex");

            addZipEntryStream(zos, "AndroidManifest.xml", new FileInputStream(newManifest), null);
            appendLogOnUi("已写入新 AndroidManifest.xml");

            if (libDir.exists() && libDir.isDirectory()) {
                addLibDirToZipStream(zos, libDir, libDir);
            }

            zos.finish();
        } finally {
            try {
                zos.close();
            } catch (Exception ignored) {
            }

            try {
                zipFile.close();
            } catch (Exception ignored) {
            }
        }

        appendLogOnUi("重打包完成：" + outApk.getAbsolutePath());
        return outApk;
    }

    private void addZipEntryStream(ZipOutputStream zos, String name, InputStream in, ZipEntry oldEntry) throws Exception {
        File tempFile = null;

        try {
            ZipEntry newEntry = new ZipEntry(name);

            if (oldEntry != null) {
                newEntry.setTime(oldEntry.getTime());
                newEntry.setComment(oldEntry.getComment());
                newEntry.setExtra(oldEntry.getExtra());
            }

            if (oldEntry != null && shouldStoreEntry(name, oldEntry)) {
                tempFile = File.createTempFile("ark_zip_", ".tmp", getCacheDir());

                CRC32 crc32 = new CRC32();
                long size = 0;

                FileOutputStream tempOut = new FileOutputStream(tempFile);
                byte[] buffer = new byte[8192];
                int len;

                while ((len = in.read(buffer)) != -1) {
                    tempOut.write(buffer, 0, len);
                    crc32.update(buffer, 0, len);
                    size += len;
                }

                tempOut.flush();
                tempOut.close();

                newEntry.setMethod(ZipEntry.STORED);
                newEntry.setSize(size);
                newEntry.setCompressedSize(size);
                newEntry.setCrc(crc32.getValue());

                zos.putNextEntry(newEntry);

                FileInputStream tempIn = new FileInputStream(tempFile);
                while ((len = tempIn.read(buffer)) != -1) {
                    zos.write(buffer, 0, len);
                }
                tempIn.close();

                zos.closeEntry();
            } else {
                newEntry.setMethod(ZipEntry.DEFLATED);

                zos.putNextEntry(newEntry);

                byte[] buffer = new byte[8192];
                int len;

                while ((len = in.read(buffer)) != -1) {
                    zos.write(buffer, 0, len);
                }

                zos.closeEntry();
            }
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }

            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private void addDirectoryZipEntry(ZipOutputStream zos, String name, ZipEntry oldEntry) throws Exception {
        if (!name.endsWith("/")) {
            name = name + "/";
        }

        ZipEntry newEntry = new ZipEntry(name);

        if (oldEntry != null) {
            newEntry.setTime(oldEntry.getTime());
            newEntry.setComment(oldEntry.getComment());
            newEntry.setExtra(oldEntry.getExtra());
        }

        zos.putNextEntry(newEntry);
        zos.closeEntry();
    }

    private void addLibDirToZipStream(ZipOutputStream zos, File rootDir, File currentDir) throws Exception {
        File[] files = currentDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                addLibDirToZipStream(zos, rootDir, file);
                continue;
            }

            String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
            String zipName = "lib/" + relativePath;

            addZipEntryStream(zos, zipName, new FileInputStream(file), null);
        }
    }

    private byte[] readAllBytes(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = readAllBytes(fis);
        fis.close();
        return data;
    }

    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }

        inputStream.close();
        return out.toByteArray();
    }
    private ArrayList<String> getAssetAbiList2() throws Exception {
        ArrayList<String> abiList = new ArrayList<>();

        String[] dirs = getAssets().list("lib");
        if (dirs == null) {
            return abiList;
        }

        for (String abi : dirs) {
            if ("armeabi".equals(abi)) {
                continue;
            }

            String soPath = "lib/" + abi + "/libArkStub.so";

            try {
                InputStream in = getAssets().open(soPath);
                in.close();
                abiList.add(abi);
            } catch (Exception ignored) {
            }
        }

        return abiList;
    }
    private void extractStubSoByTargetAbi(File apkFile, File workDir) throws Exception {
        appendLogOnUi("开始读取目标 APK ABI");

        //ArrayList<String> assetAbiList = getAssetAbiList();
        ArrayList<String> selfAbiList = getSelfApkStubAbiList();
        if (selfAbiList.isEmpty()) {
            throw new RuntimeException("assets/lib 下没有可用 ABI");
        }

        //appendLogOnUi("壳内可用 ABI：" + assetAbiList.toString());

        ArrayList<String> targetAbiList = readApkAbiList(apkFile);
        ArrayList<String> finalAbiList = new ArrayList<>();

        if (targetAbiList.isEmpty()) {
            appendLogOnUi("目标 APK 没有 lib 目录，使用 assets/lib 下全部 ABI");

            for (String abi : selfAbiList) {
                if ("armeabi".equals(abi)) {
                    appendLogOnUi("跳过 armeabi");
                    continue;
                }
                finalAbiList.add(abi);
            }
        } else {
            appendLogOnUi("目标 APK ABI：" + targetAbiList.toString());

            for (String abi : targetAbiList) {
                if ("armeabi".equals(abi)) {
                    appendLogOnUi("跳过目标 armeabi");
                    continue;
                }

                if (!selfAbiList.contains(abi)) {
                    appendLogOnUi("不支持该 ABI，跳过：" + abi);
                    continue;
                }

                finalAbiList.add(abi);
            }
        }

        if (finalAbiList.isEmpty()) {
            throw new RuntimeException("没有匹配到可解压的 ABI");
        }

        for (String abi : finalAbiList) {
            //String assetPath = "lib/" + abi + "/libArkStub.so";
            //File outFile = new File(workDir, "lib/" + abi + "/libArkStub.so");

            String soFileName = getValidSoFileNameFromSettings();
            File outFile = new File(workDir, "lib/" + abi + "/" + soFileName);

            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new RuntimeException("创建 so 输出目录失败：" + parent.getAbsolutePath());
            }

            //copyAssetToFile(assetPath, outFile);
            copySelfApkStubSoToFile(abi, outFile);
            //appendLogOnUi("已解压 so：" + outFile.getAbsolutePath());
        }
    }
    private ArrayList<String> readApkAbiList(File apkFile) throws Exception {
        ArrayList<String> abiList = new ArrayList<>();

        ZipFile zipFile = new ZipFile(apkFile);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();

            if (!name.startsWith("lib/")) {
                continue;
            }

            String[] parts = name.split("/");
            if (parts.length < 3) {
                continue;
            }

            String abi = parts[1];

            if (!abiList.contains(abi)) {
                abiList.add(abi);
            }
        }

        zipFile.close();
        return abiList;
    }
    private void copyAssetToFile2(String assetPath, File outFile) throws Exception {
        InputStream in = getAssets().open(assetPath);
        FileOutputStream out = new FileOutputStream(outFile);

        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }

        out.flush();
        out.close();
        in.close();
    }

    private ArrayList<String> getSelfApkStubAbiList() throws Exception {
        ArrayList<String> abiList = new ArrayList<>();

        String selfApkPath = getApplicationInfo().sourceDir;

        ZipFile zipFile = new ZipFile(selfApkPath);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();

            if (!name.startsWith("lib/")) {
                continue;
            }

            String[] parts = name.split("/");
            if (parts.length != 3) {
                continue;
            }

            String abi = parts[1];
            String fileName = parts[2];

            if ("armeabi".equals(abi)) {
                continue;
            }

            if (!"libArkStub.so".equals(fileName)) {
                continue;
            }

            if (!abiList.contains(abi)) {
                abiList.add(abi);
            }
        }

        zipFile.close();
        return abiList;
    }
    private void copySelfApkStubSoToFile(String abi, File outFile) throws Exception {
        String selfApkPath = getApplicationInfo().sourceDir;
        String zipPath = "lib/" + abi + "/libArkStub.so";

        ZipFile zipFile = new ZipFile(selfApkPath);
        ZipEntry entry = zipFile.getEntry(zipPath);

        if (entry == null) {
            zipFile.close();
            throw new RuntimeException("自身 APK 中未找到：" + zipPath);
        }

        InputStream in = zipFile.getInputStream(entry);

        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            in.close();
            zipFile.close();
            throw new RuntimeException("创建 so 输出目录失败：" + parent.getAbsolutePath());
        }

        FileOutputStream out = new FileOutputStream(outFile);

        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }

        out.flush();
        out.close();
        in.close();
        zipFile.close();
    }

    private File modifyAndroidManifest(File apkFile, File workDir) throws Exception {
        appendLogOnUi("开始处理 AndroidManifest.xml");

        File manifestAxml = new File(workDir, "AndroidManifest_origin.xml");
        File manifestXml = new File(workDir, "AndroidManifest_decode.xml");
        File manifestNewXml = new File(workDir, "AndroidManifest_modify.xml");
        File manifestNewAxml = new File(workDir, "AndroidManifest.xml");

        try {
            ZipFile zipFile = new ZipFile(apkFile);
            ZipEntry entry = zipFile.getEntry("AndroidManifest.xml");

            if (entry == null) {
                zipFile.close();
                throw new RuntimeException("APK 中未找到 AndroidManifest.xml");
            }

            InputStream in = zipFile.getInputStream(entry);
            FileOutputStream out = new FileOutputStream(manifestAxml);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            out.flush();
            out.close();
            in.close();
            zipFile.close();

            appendLogOnUi("已提取 AndroidManifest.xml");

            Xml2AxmlTool.decode(
                    manifestAxml.getAbsolutePath(),
                    manifestXml.getAbsolutePath()
            );

            //appendLogOnUi("Manifest 反编译完成");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(manifestXml);

            Element manifest = document.getDocumentElement();
            if (manifest == null || !"manifest".equals(manifest.getNodeName())) {
                throw new RuntimeException("Manifest XML 结构异常");
            }

            Element application = null;
            for (int i = 0; i < manifest.getChildNodes().getLength(); i++) {
                if (manifest.getChildNodes().item(i) instanceof Element) {
                    Element item = (Element) manifest.getChildNodes().item(i);
                    if ("application".equals(item.getNodeName())) {
                        application = item;
                        break;
                    }
                }
            }

            if (application == null) {
                throw new RuntimeException("Manifest 中未找到 application 标签");
            }
            //rewriteManifestRootAttributes(manifest);
            rewriteApplicationAttributes(application);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(manifestNewXml)
            );

            //appendLogOnUi("Manifest XML 修改完成");

            Xml2AxmlTool.encode2(
                    MainActivity.this,
                    manifestNewXml.getAbsolutePath(),
                    manifestNewAxml.getAbsolutePath()
            );

            /*Xml2AxmlTool.dumpAxmlForDebug(
                    manifestNewAxml.getAbsolutePath(),
                    msg -> appendLogOnUi(msg)
            );*/

            //appendLogOnUi("Manifest 编译完成：" + manifestNewAxml.getAbsolutePath());

            return manifestNewAxml;

        } finally {
            deleteFileQuietly(manifestAxml);
            deleteFileQuietly(manifestXml);
            deleteFileQuietly(manifestNewXml);
        }
    }

    private void rewriteManifestRootAttributes(Element manifest) {
        final String androidNs = "http://schemas.android.com/apk/res/android";

        String packageName = manifest.getAttribute("package");

        String versionCode = manifest.getAttributeNS(androidNs, "versionCode");
        String versionName = manifest.getAttributeNS(androidNs, "versionName");

        if (versionCode == null || versionCode.length() == 0) {
            versionCode = manifest.getAttribute("android:versionCode");
        }

        if (versionName == null || versionName.length() == 0) {
            versionName = manifest.getAttribute("android:versionName");
        }

        String platformBuildVersionCode = manifest.getAttribute("platformBuildVersionCode");
        String platformBuildVersionName = manifest.getAttribute("platformBuildVersionName");

        String compileSdkVersion = manifest.getAttributeNS(androidNs, "compileSdkVersion");
        String compileSdkVersionCodename = manifest.getAttributeNS(androidNs, "compileSdkVersionCodename");

        NamedNodeMap attrMap = manifest.getAttributes();
        ArrayList<Attr> oldAttrs = new ArrayList<>();

        for (int i = 0; i < attrMap.getLength(); i++) {
            Node node = attrMap.item(i);
            if (node instanceof Attr) {
                Attr attr = (Attr) node;
                oldAttrs.add(attr);
            }
        }

        while (manifest.getAttributes().getLength() > 0) {
            Node node = manifest.getAttributes().item(0);
            manifest.removeAttributeNode((Attr) node);
        }

        if (packageName != null && packageName.length() > 0) {
            manifest.setAttribute("package", packageName);
        }

        if (versionCode != null && versionCode.length() > 0) {
            manifest.setAttributeNS(androidNs, "android:versionCode", versionCode);
        }

        if (versionName != null && versionName.length() > 0) {
            manifest.setAttributeNS(androidNs, "android:versionName", versionName);
        }

        if (platformBuildVersionCode != null && platformBuildVersionCode.length() > 0) {
            manifest.setAttribute("platformBuildVersionCode", platformBuildVersionCode);
        }

        if (platformBuildVersionName != null && platformBuildVersionName.length() > 0) {
            manifest.setAttribute("platformBuildVersionName", platformBuildVersionName);
        }

        if (compileSdkVersion != null && compileSdkVersion.length() > 0) {
            manifest.setAttributeNS(androidNs, "android:compileSdkVersion", compileSdkVersion);
        }

        if (compileSdkVersionCodename != null && compileSdkVersionCodename.length() > 0) {
            manifest.setAttributeNS(androidNs, "android:compileSdkVersionCodename", compileSdkVersionCodename);
        }

        for (Attr attr : oldAttrs) {
            String name = attr.getName();

            if ("package".equals(name)
                    || "platformBuildVersionCode".equals(name)
                    || "platformBuildVersionName".equals(name)
                    || "android:versionCode".equals(name)
                    || "android:versionName".equals(name)
                    || "versionCode".equals(name)
                    || "versionName".equals(name)
                    || "android:compileSdkVersion".equals(name)
                    || "android:compileSdkVersionCodename".equals(name)
                    || "compileSdkVersion".equals(name)
                    || "compileSdkVersionCodename".equals(name)) {
                continue;
            }

            if (attr.getNamespaceURI() != null && attr.getNamespaceURI().length() > 0) {
                manifest.setAttributeNS(attr.getNamespaceURI(), attr.getName(), attr.getValue());
            } else {
                manifest.setAttribute(attr.getName(), attr.getValue());
            }
        }
    }
    private void rewriteApplicationAttributes(Element application) {
        final String androidNs = "http://schemas.android.com/apk/res/android";

        ArrayList<Attr> oldAttrs = new ArrayList<>();

        NamedNodeMap attrMap = application.getAttributes();
        for (int i = 0; i < attrMap.getLength(); i++) {
            Node node = attrMap.item(i);
            if (node instanceof Attr) {
                Attr attr = (Attr) node;
                String name = attr.getName();

                if ("android:name".equals(name)
                        || "android:extractNativeLibs".equals(name)
                        || "name".equals(name)
                        || "extractNativeLibs".equals(name)) {
                    continue;
                }

                oldAttrs.add(attr);
            }
        }

        while (application.getAttributes().getLength() > 0) {
            Node node = application.getAttributes().item(0);
            application.removeAttributeNode((Attr) node);
        }

        boolean hasLabelWritten = false;
        boolean hasIconWritten = false;
        boolean inserted = false;

        for (Attr attr : oldAttrs) {
            String attrName = attr.getName();
            String attrValue = attr.getValue();

            if (attr.getNamespaceURI() != null && attr.getNamespaceURI().length() > 0) {
                application.setAttributeNS(attr.getNamespaceURI(), attrName, attrValue);
            } else {
                application.setAttribute(attrName, attrValue);
            }

            if ("android:label".equals(attrName)) {
                hasLabelWritten = true;
            }

            if ("android:icon".equals(attrName)) {
                hasIconWritten = true;
            }

            if (!inserted && hasLabelWritten && hasIconWritten) {
                application.setAttributeNS(
                        androidNs,
                        "android:name",
                        getValidStubClassNameFromSettings()
                );

                application.setAttributeNS(
                        androidNs,
                        "android:extractNativeLibs",
                        "true"
                );

                inserted = true;
            }
        }

        if (!inserted) {
            application.setAttributeNS(
                    androidNs,
                    "android:name",
                    getValidStubClassNameFromSettings()
            );

            application.setAttributeNS(
                    androidNs,
                    "android:extractNativeLibs",
                    "true"
            );
        }

        //appendLogOnUi("application 属性已按顺序重写");
    }
    private void deleteFileQuietly(File file) {
        if (file == null) {
            return;
        }

        try {
            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    //appendLogOnUi("已删除临时文件：" + file.getName());
                }
            }
        } catch (Exception ignored) {
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
    private boolean shouldStoreEntry(String name, ZipEntry oldEntry) {
        if (oldEntry.getMethod() == ZipEntry.STORED) {
            return true;
        }

        String lower = name.toLowerCase(Locale.ROOT);

        return lower.endsWith(".arsc")
                || lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp")
                || lower.endsWith(".mp3")
                || lower.endsWith(".mp4")
                || lower.endsWith(".ogg")
                || lower.endsWith(".wav");
    }
    private File rebuildProtectedApk2(File apkFile, File workDir, String originalApkName) throws Exception {
        appendLogOnUi("开始重打包 APK");

        File newClassesDex = new File(workDir, "classes.dex");
        File newManifest = new File(workDir, "AndroidManifest.xml");
        File libDir = new File(workDir, "lib");

        if (!newClassesDex.exists()) {
            throw new RuntimeException("未找到新的 classes.dex");
        }

        if (!newManifest.exists()) {
            throw new RuntimeException("未找到修改后的 AndroidManifest.xml");
        }

        Set<String> skipNames = new HashSet<>();

        ZipFile checkZip = new ZipFile(apkFile);
        for (int i = 1; ; i++) {
            String dexName = i == 1 ? "classes.dex" : "classes" + i + ".dex";
            ZipEntry entry = checkZip.getEntry(dexName);

            if (entry == null) {
                //appendLogOnUi("未找到 " + dexName + "，停止记录原 dex");
                break;
            }

            skipNames.add(dexName);
            //appendLogOnUi("将删除原 dex：" + dexName);
        }
        checkZip.close();

        skipNames.add("AndroidManifest.xml");

        if (libDir.exists() && libDir.isDirectory()) {
            collectLibSkipNames(libDir, libDir, skipNames);
        }

        //File outApk = new File(workDir, "已加固.apk");
        //File outApk = new File(getFinalOutputDir(workDir), "已加固.apk");
        File outApk = new File(
                getFinalOutputDir(workDir),
                buildProtectedApkName(originalApkName)
        );

        ZipFile zipFile = new ZipFile(apkFile);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outApk));
        zos.setLevel(9);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry oldEntry = entries.nextElement();
            String name = oldEntry.getName();

            if (skipNames.contains(name)) {
                //appendLogOnUi("跳过原文件：" + name);
                continue;
            }

            InputStream in = zipFile.getInputStream(oldEntry);
            byte[] data = readAllBytes(in);

            addZipEntry(zos, name, data, oldEntry);
        }

        zipFile.close();

        //addZipEntryStored(zos, "classes.dex", readAllBytes(newClassesDex));
        addZipEntry(zos, "classes.dex", readAllBytes(newClassesDex), null);
        appendLogOnUi("已写入新 classes.dex");

        addZipEntry(zos, "AndroidManifest.xml", readAllBytes(newManifest), null);
        appendLogOnUi("已写入新 AndroidManifest.xml");

        if (libDir.exists() && libDir.isDirectory()) {
            addLibDirToZip(zos, libDir, libDir);
        }

        zos.finish();
        zos.close();

        appendLogOnUi("重打包完成：" + outApk.getAbsolutePath());
        return outApk;
    }
    private String getFileNameFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }

        String result = null;

        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    result = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (result == null || result.trim().isEmpty()) {
            String path = uri.getPath();
            if (path != null) {
                int index = path.lastIndexOf('/');
                if (index >= 0 && index < path.length() - 1) {
                    result = path.substring(index + 1);
                }
            }
        }

        return result;
    }
    private String buildProtectedApkName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "已加固.apk";
        }

        String name = originalName.trim();

        if (name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            return name.substring(0, name.length() - 4) + "(已加固).apk";
        }

        return name + "(已加固).apk";
    }
    private File getFinalOutputDir(File fallbackDir) {
        try {
            ArkSettings settings = readArkSettings();

            if (settings != null && settings.savePath != null) {
                File saveDir = new File(settings.savePath.trim());

                if (!saveDir.exists()) {
                    saveDir.mkdirs();
                }

                if (saveDir.exists() && saveDir.isDirectory() && saveDir.canWrite()) {
                    return saveDir;
                }
            }
        } catch (Exception e) {
            appendLogOnUi("读取输出目录设置失败，使用默认目录：" + e.getMessage());
        }

        return fallbackDir;
    }
    private void addLibDirToZip(ZipOutputStream zos, File rootLibDir, File current) throws Exception {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                addLibDirToZip(zos, rootLibDir, file);
            } else {
                String relative = getRelativePath(rootLibDir, file);
                String zipName = "lib/" + relative;

                //addZipEntryStored(zos, zipName, readAllBytes(file));//写入不压缩的so
                addZipEntry(zos, zipName, readAllBytes(file), null);
                appendLogOnUi("已写入 so：" + zipName);
            }
        }
    }
    private String getRelativePath(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();

        String relative = filePath.substring(rootPath.length());

        if (relative.startsWith("/") || relative.startsWith("\\")) {
            relative = relative.substring(1);
        }

        return relative.replace("\\", "/");
    }
    private void addZipEntry(ZipOutputStream zos, String name, byte[] data, ZipEntry oldEntry) throws Exception {
        ZipEntry newEntry = new ZipEntry(name);

        if (oldEntry != null) {
            newEntry.setTime(oldEntry.getTime());

            if (oldEntry.getComment() != null) {
                newEntry.setComment(oldEntry.getComment());
            }
        }

        if (oldEntry != null && shouldStoreEntry(name, oldEntry)) {
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
    private void collectLibSkipNames(File rootLibDir, File current, Set<String> skipNames) {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectLibSkipNames(rootLibDir, file, skipNames);
            } else {
                String relative = getRelativePath(rootLibDir, file);
                skipNames.add("lib/" + relative);
            }
        }
    }
    private void addZipEntryStored(ZipOutputStream zos, String name, byte[] data) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);

        CRC32 crc32 = new CRC32();
        crc32.update(data);
        entry.setCrc(crc32.getValue());

        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
    private void cleanTempFiles(File workDir) {
        if (workDir == null || !workDir.exists()) {
            return;
        }

        deleteFileQuietly(new File(workDir, "待加固.apk"));
        deleteFileQuietly(new File(workDir, "AndroidManifest.xml"));
        deleteFileQuietly(new File(workDir, "AndroidManifest_origin.xml"));
        deleteFileQuietly(new File(workDir, "AndroidManifest_decode.xml"));
        deleteFileQuietly(new File(workDir, "AndroidManifest_modify.xml"));
        deleteFileQuietly(new File(workDir, "classes.dex"));
        deleteDirQuietly(new File(workDir, "lib"));

        appendLogOnUi("临时文件清理完成");
    }
    private void deleteDirQuietly(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }

        try {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirQuietly(file);
                    } else {
                        deleteFileQuietly(file);
                    }
                }
            }

            if (dir.delete()) {
                //appendLogOnUi("已删除目录：" + dir.getName());
            }
        } catch (Exception ignored) {
        }
    }
}
