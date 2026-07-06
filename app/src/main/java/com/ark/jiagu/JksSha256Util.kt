package com.ark.jiagu

import android.content.Context
import android.util.Base64
import com.mcal.apksigner.CertConverter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

object JksSha256Util {
    @JvmStatic
    @Throws(Exception::class)
    fun getArkJksSha256(context: Context): String {
        return getJksSha256FromAssets(context, "Ark.jks", "123456", "123456", "123456")
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getJksSha256FromAssets(
        context: Context,
        assetsName: String,
        storePassword: String,
        alias: String,
        keyPassword: String
    ): String {
        val tempDir = File(context.cacheDir, "jks_sha256").also { if (!it.exists()) it.mkdirs() }
        val jksFile = File(tempDir, assetsName)
        val pk8File = File(tempDir, "temp_key.pk8")
        val certFile = File(tempDir, "temp_cert.x509.pem")
        copyAssetsToFile(context, assetsName, jksFile)
        CertConverter.convert(jksFile, storePassword, alias, keyPassword, pk8File, certFile)
        val certificate = readX509Certificate(certFile)
        val certBytes = certificate.encoded
        val digest = MessageDigest.getInstance("SHA-256")
        val sha256 = digest.digest(certBytes)
        return bytesToHex(sha256)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getJksSha256FromFile(
        jksFile: File,
        storePassword: String,
        alias: String,
        keyPassword: String,
        cacheDir: File
    ): String {
        val tempDir = File(cacheDir, "jks_sha256_custom").also { if (!it.exists()) it.mkdirs() }
        val pk8File = File(tempDir, "custom_key.pk8")
        val certFile = File(tempDir, "custom_cert.x509.pem")
        CertConverter.convert(jksFile, storePassword, alias, keyPassword, pk8File, certFile)
        val certificate = readX509Certificate(certFile)
        val certBytes = certificate.encoded
        val digest = MessageDigest.getInstance("SHA-256")
        val sha256 = digest.digest(certBytes)
        return bytesToHex(sha256)
    }

    @Throws(Exception::class)
    private fun copyAssetsToFile(context: Context, assetsName: String, outFile: File) {
        context.assets.open(assetsName).use { inputStream ->
            FileOutputStream(outFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var len: Int
                while (inputStream.read(buffer).also { len = it } != -1) {
                    outputStream.write(buffer, 0, len)
                }
                outputStream.flush()
            }
        }
    }

    @Throws(Exception::class)
    private fun readX509Certificate(certFile: File): Certificate {
        return try {
            java.io.FileInputStream(certFile).use { inputStream ->
                val factory = CertificateFactory.getInstance("X.509")
                factory.generateCertificate(inputStream)
            }
        } catch (e: Exception) {
            val pem = readText(certFile)
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim()
            val derBytes = Base64.decode(pem, Base64.DEFAULT)
            val factory = CertificateFactory.getInstance("X.509")
            factory.generateCertificate(ByteArrayInputStream(derBytes))
        }
    }

    @Throws(Exception::class)
    private fun readText(file: File): String {
        return java.io.FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(file.length().toInt())
            val len = inputStream.read(buffer)
            String(buffer, 0, len)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val builder = StringBuilder()
        for (b in bytes) {
            builder.append(String.format("%02X", b))
        }
        return builder.toString()
    }
}
