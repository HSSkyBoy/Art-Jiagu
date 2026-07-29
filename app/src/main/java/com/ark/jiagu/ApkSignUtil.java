package com.ark.jiagu;

import android.content.Context;
import android.util.Log;

import com.mcal.apksigner.ApkSigner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * APK 签名工具类
 */
public class ApkSignUtil {

    private static final String TAG = "ApkSignUtil";

    private static final String DEFAULT_KEYSTORE_NAME = "Ark.jks";

    private static final String DEFAULT_STORE_PASS = "123456";
    private static final String DEFAULT_KEY_PASS = "123456";
    private static final String DEFAULT_ALIAS = "123456";

    /**
     * 使用默认 assets/Ark.jks 签名
     */
    public static File signApk(Context context, File inputApk) throws Exception {
        return signApk(
                context,
                inputApk,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 对 APK 进行签名
     *
     * 注意：
     * 这里会在签名前再次执行 zipalign。
     * 因为你给的原始可用代码就是这个流程，先保持一致，避免签名后 APK 结构异常。
     */
    public static File signApk(
            Context context,
            File inputApk,
            File keystoreFile,
            String storePassword,
            String keyAlias,
            String keyPassword
    ) throws Exception {

        Log.d(TAG, "========== 开始 APK 签名 ==========");

        if (context == null) {
            Log.d(TAG, "Context 为空");
            throw new IllegalArgumentException("Context 为空");
        }

        if (inputApk == null) {
            Log.d(TAG, "inputApk 为空");
            throw new IllegalArgumentException("APK 文件为空");
        }

        Log.d(TAG, "输入 APK 路径：" + inputApk.getAbsolutePath());
        Log.d(TAG, "输入 APK 是否存在：" + inputApk.exists());
        Log.d(TAG, "输入 APK 是否文件：" + inputApk.isFile());
        Log.d(TAG, "输入 APK 大小：" + inputApk.length());

        if (!inputApk.exists() || !inputApk.isFile()) {
            throw new IllegalArgumentException("APK 文件不存在");
        }

        File parentDir = inputApk.getParentFile();
        if (parentDir == null || !parentDir.exists()) {
            Log.d(TAG, "APK 父目录无效");
            throw new IllegalArgumentException("APK 所在目录不存在");
        }

        Log.d(TAG, "APK 父目录：" + parentDir.getAbsolutePath());

        Log.d(TAG, "步骤①：开始 zipalign");
        optimizeApk(inputApk);
        Log.d(TAG, "步骤①：zipalign 完成");
        Log.d(TAG, "zipalign 后 APK 大小：" + inputApk.length());

        File outputApk = new File(
                parentDir,
                buildSignedName(inputApk.getName())
        );

        Log.d(TAG, "输出 APK 路径：" + outputApk.getAbsolutePath());

        if (outputApk.exists()) {
            Log.d(TAG, "发现旧输出 APK，准备删除：" + outputApk.getAbsolutePath());
            boolean deleted = outputApk.delete();
            Log.d(TAG, "旧输出 APK 删除结果：" + deleted);

            if (!deleted) {
                throw new IOException("无法删除旧签名 APK：" + outputApk.getAbsolutePath());
            }
        }

        boolean useAssetKeystore = keystoreFile == null || !keystoreFile.exists();

        File realKeystore;
        File tempKeystore = null;

        String realStorePass;
        String realAlias;
        String realKeyPass;

        boolean signSuccess = false;

        try {
            if (useAssetKeystore) {
                Log.d(TAG, "使用默认 assets 证书：" + DEFAULT_KEYSTORE_NAME);

                tempKeystore = extractAssetKeystore(context);
                realKeystore = tempKeystore;

                realStorePass = DEFAULT_STORE_PASS;
                realAlias = DEFAULT_ALIAS;
                realKeyPass = DEFAULT_KEY_PASS;
            } else {
                Log.d(TAG, "使用外部证书：" + keystoreFile.getAbsolutePath());

                realKeystore = keystoreFile;
                realStorePass = storePassword;
                realAlias = keyAlias;
                realKeyPass = keyPassword;
            }

            Log.d(TAG, "证书路径：" + realKeystore.getAbsolutePath());
            Log.d(TAG, "证书是否存在：" + realKeystore.exists());
            Log.d(TAG, "证书大小：" + realKeystore.length());
            Log.d(TAG, "证书别名：" + realAlias);
            Log.d(TAG, "证书密码长度：" + (realStorePass == null ? -1 : realStorePass.length()));
            Log.d(TAG, "密钥密码长度：" + (realKeyPass == null ? -1 : realKeyPass.length()));

            checkSignParams(realKeystore, realStorePass, realAlias, realKeyPass);

            Log.d(TAG, "步骤②：创建 ApkSigner");

            ApkSigner signer = new ApkSigner(inputApk, outputApk);

            signer.setUseDefaultSignatureVersion(false);
            signer.setV1SigningEnabled(false);
            signer.setV2SigningEnabled(true);
            signer.setV3SigningEnabled(true);
            signer.setV4SigningEnabled(false);

            Log.d(TAG, "签名版本：V1=false, V2=true, V3=true, V4=false");
            Log.d(TAG, "步骤③：开始执行 signRelease");

            signer.signRelease(
                    realKeystore,
                    realStorePass,
                    realAlias,
                    realKeyPass
            );

            Log.d(TAG, "步骤③：signRelease 执行完成");
            Log.d(TAG, "输出 APK 是否存在：" + outputApk.exists());
            Log.d(TAG, "输出 APK 大小：" + outputApk.length());

            if (!outputApk.exists() || outputApk.length() <= 0) {
                throw new IOException("签名失败：输出 APK 文件异常");
            }

            signSuccess = true;

            Log.d(TAG, "APK 签名成功：" + outputApk.getAbsolutePath());
            return outputApk;

        } catch (Exception e) {
            Log.e(TAG, "APK 签名异常：" + e.getMessage(), e);
            throw e;

        } finally {
            Log.d(TAG, "进入 finally，signSuccess=" + signSuccess);

            if (tempKeystore != null && tempKeystore.exists()) {
                boolean deleted = tempKeystore.delete();
                Log.d(TAG, "删除临时证书：" + deleted);
            }

            if (signSuccess && inputApk.exists()) {
                boolean deleted = inputApk.delete();
                Log.d(TAG, "签名成功，删除原 APK：" + deleted);
            }

            Log.d(TAG, "========== APK 签名流程结束 ==========");
        }
    }

    /**
     * 对 APK 进行 zipalign，对齐成功后替换原文件
     */
    public static void optimizeApk(File apkFile) throws IOException {
        Log.d(TAG, "---------- 开始 zipalign ----------");

        if (apkFile == null || !apkFile.isFile()) {
            Log.d(TAG, "APK 文件无效");
            throw new IllegalArgumentException("APK 文件无效");
        }

        File parent = apkFile.getParentFile();
        if (parent == null) {
            throw new IOException("无法获取 APK 目录");
        }

        Log.d(TAG, "待对齐 APK：" + apkFile.getAbsolutePath());
        Log.d(TAG, "待对齐 APK 大小：" + apkFile.length());

        File alignedTmp = File.createTempFile(
                apkFile.getName() + ".zipalign.",
                ".tmp",
                parent
        );

        Log.d(TAG, "zipalign 临时文件：" + alignedTmp.getAbsolutePath());

        boolean success = false;

        try {
            success = ZipAlign.doZipAlign(
                    apkFile.getAbsolutePath(),
                    alignedTmp.getAbsolutePath(),
                    4,
                    true,
                    true
            );

            Log.d(TAG, "ZipAlign.doZipAlign 返回：" + success);
            Log.d(TAG, "对齐临时文件是否存在：" + alignedTmp.exists());
            Log.d(TAG, "对齐临时文件大小：" + alignedTmp.length());

            if (!success) {
                throw new IOException("zipalign 执行失败");
            }

            boolean verified = ZipAlign.isZipAligned(
                    alignedTmp.getAbsolutePath(),
                    4,
                    true
            );

            Log.d(TAG, "ZipAlign.isZipAligned 返回：" + verified);

            if (!verified) {
                throw new IOException("zipalign 校验失败");
            }

            if (!apkFile.delete()) {
                throw new IOException("无法删除原 APK 文件");
            }

            Log.d(TAG, "原 APK 删除成功");

            if (!alignedTmp.renameTo(apkFile)) {
                throw new IOException("对齐 APK 替换原文件失败");
            }

            Log.d(TAG, "对齐 APK 替换成功");
            Log.d(TAG, "替换后 APK 是否存在：" + apkFile.exists());
            Log.d(TAG, "替换后 APK 大小：" + apkFile.length());

        } catch (IOException e) {
            Log.e(TAG, "zipalign 异常：" + e.getMessage(), e);
            throw e;

        } finally {
            if (!success && alignedTmp.exists()) {
                boolean deleted = alignedTmp.delete();
                Log.d(TAG, "zipalign 失败，删除临时文件：" + deleted);
            }

            Log.d(TAG, "---------- zipalign 结束 ----------");
        }
    }

    /**
     * 构建签名后的 APK 名称
     */
    private static String buildSignedName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "signed.apk";
        }

        String lower = name.toLowerCase(Locale.ROOT);

        if (lower.endsWith("_sign.apk")) {
            return name;
        }

        if (lower.endsWith(".apk")) {
            return name.substring(0, name.length() - 4) + "_sign.apk";
        }

        return name + "_sign.apk";
    }

    /**
     * 将 assets 中的 cainiao.jks 解压到缓存目录
     */
    private static File extractAssetKeystore(Context context) throws Exception {
        Log.d(TAG, "开始解压 assets 证书：" + DEFAULT_KEYSTORE_NAME);

        File outFile = new File(context.getCacheDir(), DEFAULT_KEYSTORE_NAME);

        Log.d(TAG, "证书解压目标路径：" + outFile.getAbsolutePath());

        if (outFile.exists()) {
            boolean deleted = outFile.delete();
            Log.d(TAG, "删除旧临时证书：" + deleted);

            if (!deleted) {
                throw new IOException("无法删除旧临时证书：" + outFile.getAbsolutePath());
            }
        }

        try (
                InputStream is = context.getAssets().open(DEFAULT_KEYSTORE_NAME);
                FileOutputStream fos = new FileOutputStream(outFile)
        ) {
            byte[] buffer = new byte[8192];
            int len;

            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }

            fos.flush();
        }

        Log.d(TAG, "证书解压完成");
        Log.d(TAG, "证书是否存在：" + outFile.exists());
        Log.d(TAG, "证书大小：" + outFile.length());

        if (!outFile.exists() || outFile.length() <= 0) {
            throw new IOException("默认签名证书解压失败");
        }

        return outFile;
    }

    /**
     * 检查签名参数
     */
    private static void checkSignParams(
            File keystoreFile,
            String storePassword,
            String keyAlias,
            String keyPassword
    ) {
        if (keystoreFile == null || !keystoreFile.exists() || !keystoreFile.isFile()) {
            throw new IllegalArgumentException("签名证书不存在");
        }

        if (storePassword == null || storePassword.length() == 0) {
            throw new IllegalArgumentException("证书密码为空");
        }

        if (keyAlias == null || keyAlias.length() == 0) {
            throw new IllegalArgumentException("证书别名为空");
        }

        if (keyPassword == null || keyPassword.length() == 0) {
            throw new IllegalArgumentException("密钥密码为空");
        }
    }
}
