package com.ark.jiagu

import android.content.Context
import android.util.Base64
import com.mcal.apksigner.CertConverter
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

object JksSha256Util {
    private const val DEFAULT_KEYSTORE = "Ark.jks"
    private const val DEFAULT_PASSWORD = "123456"
    private const val DEFAULT_ALIAS = "123456"
    private val hex = "0123456789ABCDEF".toCharArray()

    @JvmStatic
    fun getArkJksSha256(context: Context): String =
        getJksSha256FromAssets(
            context = context,
            assetsName = DEFAULT_KEYSTORE,
            storePassword = DEFAULT_PASSWORD,
            alias = DEFAULT_ALIAS,
            keyPassword = DEFAULT_PASSWORD,
        )

    @JvmStatic
    fun getJksSha256FromAssets(
        context: Context,
        assetsName: String,
        storePassword: String,
        alias: String,
        keyPassword: String,
    ): String {
        val tempDir = File(context.cacheDir, "jks_sha256").also(::ensureDirectory)
        val jksFile = File(tempDir, assetsName)
        val pk8File = File(tempDir, "temp_key.pk8")
        val certFile = File(tempDir, "temp_cert.x509.pem")

        return try {
            context.assets.open(assetsName).use { input ->
                jksFile.outputStream().use(input::copyTo)
            }
            convertAndHash(jksFile, storePassword, alias, keyPassword, pk8File, certFile)
        } finally {
            jksFile.delete()
            pk8File.delete()
            certFile.delete()
        }
    }

    @JvmStatic
    fun getJksSha256FromFile(
        jksFile: File,
        storePassword: String,
        alias: String,
        keyPassword: String,
        cacheDir: File,
    ): String {
        val tempDir = File(cacheDir, "jks_sha256_custom").also(::ensureDirectory)
        val pk8File = File(tempDir, "custom_key.pk8")
        val certFile = File(tempDir, "custom_cert.x509.pem")

        return try {
            convertAndHash(jksFile, storePassword, alias, keyPassword, pk8File, certFile)
        } finally {
            pk8File.delete()
            certFile.delete()
        }
    }

    private fun convertAndHash(
        jksFile: File,
        storePassword: String,
        alias: String,
        keyPassword: String,
        pk8File: File,
        certFile: File,
    ): String {
        CertConverter.convert(
            jksFile,
            storePassword,
            alias,
            keyPassword,
            pk8File,
            certFile,
        )
        val certificate = readX509Certificate(certFile)
        return bytesToHex(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))
    }

    private fun readX509Certificate(certFile: File): Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return try {
            certFile.inputStream().use(factory::generateCertificate)
        } catch (exception: Exception) {
            val pem = certFile.readText()
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim()
            val derBytes = Base64.decode(pem, Base64.DEFAULT)
            ByteArrayInputStream(derBytes).use(factory::generateCertificate)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        CharArray(bytes.size * 2).also { output ->
            bytes.forEachIndexed { index, byte ->
                val value = byte.toInt() and 0xff
                output[index * 2] = hex[value ushr 4]
                output[index * 2 + 1] = hex[value and 0x0f]
            }
        }.concatToString()

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IllegalStateException("无法创建临时目录：${directory.absolutePath}")
        }
    }
}
