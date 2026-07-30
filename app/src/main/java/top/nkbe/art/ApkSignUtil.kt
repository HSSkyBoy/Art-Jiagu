package top.nkbe.art

import android.content.Context
import android.util.Log
import com.mcal.apksigner.ApkSigner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * APK signing utilities.
 */
object ApkSignUtil {
    private const val TAG = "ApkSignUtil"
    private const val DEFAULT_KEYSTORE_NAME = "npatch.key"
    private const val DEFAULT_STORE_PASS = "123456"
    private const val DEFAULT_KEY_PASS = "123456"
    private const val DEFAULT_ALIAS = "key0"

    @JvmStatic
    @Throws(Exception::class)
    fun signApk(context: Context, inputApk: File): File =
        signApk(context, inputApk, null, null, null, null, null)

    @JvmStatic
    @Throws(Exception::class)
    fun signApk(
        context: Context,
        inputApk: File,
        keystoreFile: File?,
        storePassword: String?,
        keyAlias: String?,
        keyPassword: String?,
        logger: ((String) -> Unit)?,
    ): File {
        fun uiLog(message: String) {
            Log.d(TAG, message)
            logger?.invoke(message)
        }

        uiLog("========== 开始 APK 签名 ==========")
        uiLog("输入 APK 路径：${inputApk.absolutePath}")
        uiLog("输入 APK 是否存在：${inputApk.exists()}")
        uiLog("输入 APK 是否文件：${inputApk.isFile}")
        uiLog("输入 APK 大小：${inputApk.length()} 字节")

        require(inputApk.isFile) { "APK 文件不存在" }
        val parentDir = requireNotNull(inputApk.parentFile?.takeIf(File::exists)) {
            "APK 所在目录不存在"
        }

        uiLog("APK 父目录：${parentDir.absolutePath}")
        uiLog("步骤①：开始 zipalign")
        optimizeApk(inputApk, logger)
        uiLog("步骤①：zipalign 完成")
        uiLog("zipalign 后 APK 大小：${inputApk.length()} 字节")

        val outputApk = File(parentDir, buildSignedName(inputApk.name))
        uiLog("输出 APK 路径：${outputApk.absolutePath}")
        if (outputApk.exists() && !outputApk.delete()) {
            throw IOException("无法删除旧签名 APK：${outputApk.absolutePath}")
        }

        val useAssetKeystore = keystoreFile?.exists() != true
        var tempKeystore: File? = null
        var signSuccess = false

        try {
            val realKeystore: File
            val realStorePass: String?
            val realAlias: String?
            val realKeyPass: String?

            if (useAssetKeystore) {
                uiLog("使用默认 assets 证书：$DEFAULT_KEYSTORE_NAME")
                tempKeystore = extractAssetKeystore(context)
                realKeystore = tempKeystore
                realStorePass = DEFAULT_STORE_PASS
                realAlias = DEFAULT_ALIAS
                realKeyPass = DEFAULT_KEY_PASS
            } else {
                realKeystore = requireNotNull(keystoreFile)
                realStorePass = storePassword
                realAlias = keyAlias
                realKeyPass = keyPassword
                uiLog("使用外部证书：${realKeystore.absolutePath}")
            }

            uiLog("证书路径：${realKeystore.absolutePath}")
            uiLog("证书是否存在：${realKeystore.exists()}")
            uiLog("证书大小：${realKeystore.length()} 字节")
            uiLog("证书别名：$realAlias")
            uiLog("证书密码长度：${realStorePass?.length ?: -1}")
            uiLog("密钥密码长度：${realKeyPass?.length ?: -1}")

            checkSignParams(realKeystore, realStorePass, realAlias, realKeyPass)
            uiLog("签名参数校验通过")
            uiLog("步骤②：开始执行 APK 签名")

            ApkSigner(inputApk, outputApk).apply {
                useDefaultSignatureVersion = false
                v1SigningEnabled = false
                v2SigningEnabled = true
                v3SigningEnabled = true
                v4SigningEnabled = false
                signRelease(
                    realKeystore,
                    requireNotNull(realStorePass),
                    requireNotNull(realAlias),
                    requireNotNull(realKeyPass),
                )
            }

            if (!outputApk.exists() || outputApk.length() <= 0) {
                throw IOException("签名失败：输出 APK 文件异常")
            }
            signSuccess = true
            uiLog("APK 签名成功：${outputApk.absolutePath}")
            uiLog("签名后 APK 大小：${outputApk.length()} 字节")
            return outputApk
        } catch (error: Exception) {
            Log.e(TAG, "APK 签名异常：${error.message}", error)
            throw error
        } finally {
            tempKeystore?.takeIf(File::exists)?.let {
                uiLog("删除临时证书：${it.delete()}")
            }
            if (signSuccess && inputApk.exists()) {
                uiLog("签名成功，删除原 APK：${inputApk.delete()}")
            }
            uiLog("========== APK 签名流程结束 ==========")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun optimizeApk(apkFile: File, logger: ((String) -> Unit)? = null) {
        fun uiLog(message: String) {
            Log.d(TAG, message)
            logger?.invoke(message)
        }

        require(apkFile.isFile) { "APK 文件无效" }
        val parent = apkFile.parentFile ?: throw IOException("无法获取 APK 目录")
        val alignedTmp = File.createTempFile("${apkFile.name}.zipalign.", ".tmp", parent)
        var success = false
        uiLog("zipalign 输入文件：${apkFile.absolutePath}")
        uiLog("zipalign 临时输出：${alignedTmp.absolutePath}")

        try {
            success = ZipAlign.doZipAlign(
                apkFile.absolutePath,
                alignedTmp.absolutePath,
                4,
                true,
                true,
            )
            if (!success) throw IOException("zipalign 执行失败")
            if (!ZipAlign.isZipAligned(alignedTmp.absolutePath, 4, true)) {
                throw IOException("zipalign 校验失败")
            }
            uiLog("zipalign 校验通过")
            if (!apkFile.delete()) throw IOException("无法删除原 APK 文件")
            uiLog("已删除原 APK，准备替换为对齐版本")
            if (!alignedTmp.renameTo(apkFile)) {
                throw IOException("对齐 APK 替换原文件失败")
            }
            uiLog("对齐 APK 替换完成：${apkFile.absolutePath}")
        } catch (error: IOException) {
            Log.e(TAG, "zipalign 异常：${error.message}", error)
            throw error
        } finally {
            if (!success && alignedTmp.exists()) {
                uiLog("zipalign 失败，删除临时文件：${alignedTmp.delete()}")
            }
        }
    }

    private fun buildSignedName(name: String): String {
        if (name.isBlank()) return "signed.apk"
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.endsWith("_sign.apk") -> name
            lower.endsWith(".apk") -> "${name.dropLast(4)}_sign.apk"
            else -> "${name}_sign.apk"
        }
    }

    @Throws(Exception::class)
    private fun extractAssetKeystore(context: Context): File {
        val outFile = File(context.cacheDir, DEFAULT_KEYSTORE_NAME)
        if (outFile.exists() && !outFile.delete()) {
            throw IOException("无法删除旧临时证书：${outFile.absolutePath}")
        }
        context.assets.open(DEFAULT_KEYSTORE_NAME).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        if (!outFile.exists() || outFile.length() <= 0) {
            throw IOException("默认签名证书解压失败")
        }
        return outFile
    }

    private fun checkSignParams(
        keystoreFile: File,
        storePassword: String?,
        keyAlias: String?,
        keyPassword: String?,
    ) {
        require(keystoreFile.isFile) { "签名证书不存在" }
        require(!storePassword.isNullOrEmpty()) { "证书密码为空" }
        require(!keyAlias.isNullOrEmpty()) { "证书别名为空" }
        require(!keyPassword.isNullOrEmpty()) { "密钥密码为空" }
    }
}
