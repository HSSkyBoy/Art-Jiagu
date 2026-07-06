package com.ark.jar.xml2axml.test

import android.content.Context
import com.ark.jar.xml2axml.Encoder
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintStream
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class Xml2AxmlTool {
    companion object {
        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun encode(context: Context, `in`: String, out: String) {
            val encoder = Encoder()
            val data = encoder.encodeFile(context, `in`)
            val fos = FileOutputStream(File(out))
            fos.write(data)
            fos.close()
        }

        @JvmStatic
        @Throws(FileNotFoundException::class)
        fun decode(`in`: String, out: String) {
            val ps = PrintStream(File(out))
            AXMLPrinter.out = ps
            AXMLPrinter.main(arrayOf(`in`))
            ps.close()
        }

        @JvmStatic
        @Throws(IOException::class, XmlPullParserException::class)
        fun encode2(context: Context, `in`: String, out: String) {
            var fixedXml: File? = null
            try {
                fixedXml = reorderManifestRootAttributesIfNeeded(normalizeFilePath(`in`))
                val encoder = Encoder()
                val data = encoder.encodeFile(context, fixedXml!!.absolutePath)
                val fos = FileOutputStream(File(out))
                fos.write(data)
                fos.close()
            } catch (e: Exception) {
                if (e is IOException) throw e
                if (e is XmlPullParserException) throw e
                throw IOException("Failed to reorder Manifest root attributes", e)
            } finally {
                if (fixedXml != null && fixedXml.exists()) fixedXml.delete()
            }
        }

        private fun normalizeFilePath(path: String?): String? {
            if (path == null) return null
            try {
                if (path.contains("%")) return java.net.URLDecoder.decode(path, "UTF-8")
            } catch (_: Exception) {
            }
            return path
        }

        @Throws(Exception::class)
        private fun reorderManifestRootAttributesIfNeeded(xmlPath: String): File {
            val input = File(xmlPath)
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(input)
            val manifest = document.documentElement
            if (manifest == null || "manifest" != manifest.nodeName) return input
            reorderManifestRootAttributes(manifest)
            val fixed = File(input.parentFile, input.name + ".manifest_root_fixed.xml")
            val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "utf-8")
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no")
            val parent = fixed.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(fixed)
                transformer.transform(DOMSource(document), StreamResult(fos))
            } finally {
                fos?.close()
            }
            return fixed
        }

        private fun reorderManifestRootAttributes(manifest: org.w3c.dom.Element) {
            val androidNs = "http://schemas.android.com/apk/res/android"
            val packageName = manifest.getAttribute("package")
            var versionCode = manifest.getAttributeNS(androidNs, "versionCode")
            var versionName = manifest.getAttributeNS(androidNs, "versionName")
            if (versionCode == null || versionCode.length == 0) versionCode = manifest.getAttribute("android:versionCode")
            if (versionName == null || versionName.length == 0) versionName = manifest.getAttribute("android:versionName")
            val platformBuildVersionCode = manifest.getAttribute("platformBuildVersionCode")
            val platformBuildVersionName = manifest.getAttribute("platformBuildVersionName")
            var compileSdkVersion = manifest.getAttributeNS(androidNs, "compileSdkVersion")
            var compileSdkVersionCodename = manifest.getAttributeNS(androidNs, "compileSdkVersionCodename")
            if (compileSdkVersion == null || compileSdkVersion.length == 0) compileSdkVersion = manifest.getAttribute("android:compileSdkVersion")
            if (compileSdkVersionCodename == null || compileSdkVersionCodename.length == 0) compileSdkVersionCodename = manifest.getAttribute("android:compileSdkVersionCodename")
            val attrMap = manifest.attributes
            val oldAttrs = java.util.ArrayList<org.w3c.dom.Attr>()
            for (i in 0 until attrMap.length) {
                val node = attrMap.item(i)
                if (node is org.w3c.dom.Attr) oldAttrs.add(node)
            }
            while (manifest.attributes.length > 0) {
                val node = manifest.attributes.item(0)
                manifest.removeAttributeNode(node as org.w3c.dom.Attr)
            }
            if (packageName != null && packageName.length > 0) manifest.setAttribute("package", packageName)
            if (versionCode != null && versionCode.length > 0) manifest.setAttributeNS(androidNs, "android:versionCode", versionCode)
            if (versionName != null && versionName.length > 0) manifest.setAttributeNS(androidNs, "android:versionName", versionName)
            if (platformBuildVersionCode != null && platformBuildVersionCode.length > 0) manifest.setAttribute("platformBuildVersionCode", platformBuildVersionCode)
            if (platformBuildVersionName != null && platformBuildVersionName.length > 0) manifest.setAttribute("platformBuildVersionName", platformBuildVersionName)
            if (compileSdkVersion != null && compileSdkVersion.length > 0) manifest.setAttributeNS(androidNs, "android:compileSdkVersion", compileSdkVersion)
            if (compileSdkVersionCodename != null && compileSdkVersionCodename.length > 0) manifest.setAttributeNS(androidNs, "android:compileSdkVersionCodename", compileSdkVersionCodename)
            for (attr in oldAttrs) {
                val name = attr.name
                if ("package" == name || "platformBuildVersionCode" == name || "platformBuildVersionName" == name ||
                    "android:versionCode" == name || "android:versionName" == name || "versionCode" == name || "versionName" == name ||
                    "android:compileSdkVersion" == name || "android:compileSdkVersionCodename" == name ||
                    "compileSdkVersion" == name || "compileSdkVersionCodename" == name
                ) continue
                if (attr.namespaceURI != null && attr.namespaceURI.length > 0) manifest.setAttributeNS(attr.namespaceURI, attr.name, attr.value)
                else manifest.setAttribute(attr.name, attr.value)
            }
        }

        interface DumpLogger {
            fun log(msg: String)
        }

        @Throws(IOException::class)
        fun dumpAxmlForDebug(axmlPath: String, logger: DumpLogger) {
            val data = java.nio.file.Files.readAllBytes(File(axmlPath).toPath())
            logger.log("========== AXML DUMP START ==========")
            logger.log("File: $axmlPath")
            logger.log("Size: " + data.size + " bytes")
            val xmlType = u16(data, 0)
            val xmlHeaderSize = u16(data, 2)
            val xmlSize = u32(data, 4)
            logger.log("XML header type=0x" + hex4(xmlType) + " headerSize=" + xmlHeaderSize + " size=" + xmlSize)
            val strings = java.util.ArrayList<String>()
            val resIds = java.util.ArrayList<Int>()
            var off = xmlHeaderSize
            while (off + 8 <= data.size) {
                val type = u16(data, off)
                val headerSize = u16(data, off + 2)
                val size = u32(data, off + 4)
                logger.log("")
                logger.log("Chunk offset=0x" + hex8(off) + " type=0x" + hex4(type) + " headerSize=" + headerSize + " size=" + size)
                if (size <= 0 || off + size > data.size) {
                    logger.log("Chunk size invalid, stopping")
                    break
                }
                when (type) {
                    0x0001 -> parseStringPool(data, off, strings, logger)
                    0x0180 -> parseResourceMap(data, off, size, resIds, strings, logger)
                    0x0102 -> parseStartElement(data, off, strings, resIds, logger)
                }
                off += size
            }
            logger.log("========== AXML DUMP END ==========")
        }

        private fun parseStringPool(data: ByteArray, off: Int, strings: java.util.ArrayList<String>, logger: DumpLogger) {
            val stringCount = u32(data, off + 8)
            val styleCount = u32(data, off + 12)
            val flags = u32(data, off + 16)
            val stringsStart = u32(data, off + 20)
            val stylesStart = u32(data, off + 24)
            val utf8 = (flags.and(0x00000100)) != 0
            logger.log("StringPool: count=$stringCount styleCount=$styleCount flags=0x" + hex8(flags) + " encoding=" + if (utf8) "UTF-8" else "UTF-16")
            strings.clear()
            for (i in 0 until stringCount) {
                val strOff = u32(data, off + 28 + i * 4)
                val abs = off + stringsStart + strOff
                val s = if (utf8) readUtf8String(data, abs) else readUtf16String(data, abs)
                strings.add(s)
                logger.log("String[$i] = $s")
            }
        }

        private fun parseResourceMap(data: ByteArray, off: Int, size: Int, resIds: java.util.ArrayList<Int>, strings: java.util.ArrayList<String>, logger: DumpLogger) {
            resIds.clear()
            val count = (size - 8) / 4
            logger.log("ResourceMap count=$count")
            for (i in 0 until count) {
                val id = u32(data, off + 8 + i * 4)
                resIds.add(id)
                val name = if (i < strings.size) strings[i] else "<no string>"
                logger.log("ResMap[$i] string=$name id=0x" + hex8(id) + " known=" + knownAttrName(id))
            }
        }

        private fun parseStartElement(data: ByteArray, off: Int, strings: java.util.ArrayList<String>, resIds: java.util.ArrayList<Int>, logger: DumpLogger) {
            val lineNo = u32(data, off + 8)
            val comment = u32(data, off + 12)
            val nsIdx = s32(data, off + 16)
            val nameIdx = s32(data, off + 20)
            val attrStart = u16(data, off + 24)
            val attrSize = u16(data, off + 26)
            val attrCount = u16(data, off + 28)
            val idIndex = u16(data, off + 30)
            val classIndex = u16(data, off + 32)
            val styleIndex = u16(data, off + 34)
            val tagName = getString(strings, nameIdx)
            val tagNs = getString(strings, nsIdx)
            logger.log("StartElement tag=$tagName ns=$tagNs line=$lineNo attrStart=$attrStart attrSize=$attrSize attrCount=$attrCount idIndex=$idIndex classIndex=$classIndex styleIndex=$styleIndex")
            val attrBase = off + 16 + attrStart
            val isManifest = "manifest" == tagName
            for (i in 0 until attrCount) {
                val p = attrBase + i * attrSize
                val attrNsIdx = s32(data, p)
                val attrNameIdx = s32(data, p + 4)
                val rawValueIdx = s32(data, p + 8)
                val valueSize = u16(data, p + 12)
                val valueRes0 = data[p + 14].toInt().and(0xff)
                val valueType = data[p + 15].toInt().and(0xff)
                val valueData = u32(data, p + 16)
                val attrNs = getString(strings, attrNsIdx)
                val attrName = getString(strings, attrNameIdx)
                val rawValue = getString(strings, rawValueIdx)
                val nameResId = if (attrNameIdx >= 0 && attrNameIdx < resIds.size) resIds[attrNameIdx] else 0
                val decodedValue = decodeTypedValue(valueType, valueData, strings)
                logger.log("  Attr[$i] nsIdx=$attrNsIdx nameIdx=$attrNameIdx rawIdx=$rawValueIdx ns=$attrNs name=$attrName nameResId=0x" + hex8(nameResId) + " known=" + knownAttrName(nameResId) + " raw=$rawValue valueSize=$valueSize type=0x" + hex2(valueType) + " data=0x" + hex8(valueData) + " decoded=$decodedValue")
                if (isManifest) {
                    if (nameResId == 0x0101021b || "versionCode" == attrName) logger.log("  >>> hit versionCode: nameResId=0x" + hex8(nameResId) + " type=0x" + hex2(valueType) + " data=$valueData raw=$rawValue decoded=$decodedValue")
                    if (nameResId == 0x0101021c || "versionName" == attrName) logger.log("  >>> hit versionName: nameResId=0x" + hex8(nameResId) + " type=0x" + hex2(valueType) + " data=0x" + hex8(valueData) + " raw=$rawValue decoded=$decodedValue")
                }
            }
        }

        private fun decodeTypedValue(type: Int, data: Int, strings: java.util.ArrayList<String>): String {
            return when (type) {
                0x00 -> "null"
                0x01 -> "@" + hex8(data)
                0x03 -> getString(strings, data)
                0x10 -> data.toString()
                0x11 -> "0x" + hex8(data)
                0x12 -> if (data != 0) "true" else "false"
                else -> "type=0x" + hex2(type) + ", data=0x" + hex8(data)
            }
        }

        private fun knownAttrName(id: Int): String {
            return when (id) {
                0x01010000 -> "theme"
                0x01010001 -> "label"
                0x01010002 -> "icon"
                0x01010003 -> "name"
                0x0101021b -> "versionCode"
                0x0101021c -> "versionName"
                0x01010270 -> "minSdkVersion"
                0x01010271 -> "targetSdkVersion"
                0x010104ea -> "extractNativeLibs"
                0x01010572 -> "compileSdkVersion"
                0x01010573 -> "compileSdkVersionCodename"
                else -> ""
            }
        }

        private fun getString(strings: java.util.ArrayList<String>, index: Int): String {
            if (index < 0) return "null"
            if (index >= strings.size) return "<out of bounds:$index>"
            return strings[index]
        }

        private fun readUtf16String(data: ByteArray, off: Int): String {
            val lenInfo = readUtf16Length(data, off)
            val len = lenInfo[0]
            val p = off + lenInfo[1]
            val sb = StringBuilder()
            for (i in 0 until len) {
                val ch = u16(data, p + i * 2)
                sb.append(ch.toChar())
            }
            return sb.toString()
        }

        private fun readUtf16Length(data: ByteArray, off: Int): IntArray {
            val first = u16(data, off)
            return if ((first.and(0x8000)) != 0) {
                val second = u16(data, off + 2)
                val len = (first.and(0x7fff) shl 16) or second
                intArrayOf(len, 4)
            } else {
                intArrayOf(first, 2)
            }
        }

        private fun readUtf8String(data: ByteArray, off: Int): String {
            val utf16Len = readUtf8Length(data, off)
            var p = off + utf16Len[1]
            val byteLen = readUtf8Length(data, p)
            p += byteLen[1]
            return try {
                String(data, p, byteLen[0], "UTF-8")
            } catch (e: Exception) {
                "<UTF8 parse error>"
            }
        }

        private fun readUtf8Length(data: ByteArray, off: Int): IntArray {
            val first = data[off].toInt().and(0xff)
            return if ((first.and(0x80)) != 0) {
                val second = data[off + 1].toInt().and(0xff)
                val len = (first.and(0x7f) shl 8) or second
                intArrayOf(len, 2)
            } else {
                intArrayOf(first, 1)
            }
        }

        private fun u16(data: ByteArray, off: Int): Int {
            return (data[off].toInt().and(0xff)) or ((data[off + 1].toInt().and(0xff)) shl 8)
        }

        private fun s32(data: ByteArray, off: Int): Int = u32(data, off)

        private fun u32(data: ByteArray, off: Int): Int {
            return (data[off].toInt().and(0xff)) or
                    ((data[off + 1].toInt().and(0xff)) shl 8) or
                    ((data[off + 2].toInt().and(0xff)) shl 16) or
                    ((data[off + 3].toInt().and(0xff)) shl 24)
        }

        private fun hex2(v: Int): String = String.format("%02X", v.and(0xff))

        private fun hex4(v: Int): String = String.format("%04X", v.and(0xffff))

        private fun hex8(v: Int): String = String.format("%08X", v)
    }
}
