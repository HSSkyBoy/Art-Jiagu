package com.ark.jar.xml2axml

import com.ark.jar.xml2axml.test.AXMLPrinter
import java.io.*

object AxmlUtils {

    @JvmStatic
    fun decode(data: ByteArray): String? {
        try {
            ByteArrayInputStream(data).use { `is` ->
                ByteArrayOutputStream().use { os ->
                    AXMLPrinter.out = PrintStream(os)
                    AXMLPrinter.decode(`is`)
                    AXMLPrinter.out.close()
                    return String(os.toByteArray(), "UTF-8")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(file: File): String? = decode(readFileToByteArray(file))

    @JvmStatic
    fun encode(xml: String): ByteArray? {
        return try {
            val encoder = Encoder()
            encoder.encodeString(null, xml)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun encode(file: File): ByteArray? {
        return try {
            val encoder = Encoder()
            encoder.encodeFile(null, file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Throws(IOException::class)
    private fun readFileToByteArray(file: File): ByteArray {
        FileInputStream(file).use { fis ->
            ByteArrayOutputStream().use { bos ->
                val buffer = ByteArray(4096)
                var len: Int
                while (fis.read(buffer).also { len = it } != -1) {
                    bos.write(buffer, 0, len)
                }
                return bos.toByteArray()
            }
        }
    }
}
