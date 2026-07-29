package com.ark.jar.xml2axml.test;

import android.content.Context;

import com.ark.jar.xml2axml.Encoder;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class Xml2AxmlTool {

    /**
     * 编码 XML -> 二进制 AXML
     */
    public static void encode(Context context, String in, String out)
            throws IOException, XmlPullParserException {

        Encoder encoder = new Encoder();
        byte[] data = encoder.encodeFile(context, in);

        FileOutputStream fos = new FileOutputStream(new File(out));
        fos.write(data);
        fos.close();
    }



    /**
     * 解码 二进制 AXML -> XML
     */
    public static void decode(String in, String out)
            throws FileNotFoundException {

        PrintStream ps = new PrintStream(new File(out));
        AXMLPrinter.out = ps;
        AXMLPrinter.main(new String[]{in});
        ps.close();
    }





    public static void encode2(Context context, String in, String out)
            throws IOException, XmlPullParserException {

        File fixedXml = null;

        try {
            fixedXml = reorderManifestRootAttributesIfNeeded(normalizeFilePath(in));

            Encoder encoder = new Encoder();
            byte[] data = encoder.encodeFile(context, fixedXml.getAbsolutePath());

            FileOutputStream fos = new FileOutputStream(new File(out));
            fos.write(data);
            fos.close();

        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            if (e instanceof XmlPullParserException) throw (XmlPullParserException) e;
            throw new IOException("重排 Manifest 根节点属性失败", e);
        } finally {
            if (fixedXml != null && fixedXml.exists()) {
                fixedXml.delete();
            }
        }
    }
    private static String normalizeFilePath(String path) {
        if (path == null) return null;

        try {
            if (path.contains("%")) {
                return java.net.URLDecoder.decode(path, "UTF-8");
            }
        } catch (Exception ignored) {
        }

        return path;
    }


    private static File reorderManifestRootAttributesIfNeeded(String xmlPath) throws Exception {
        File input = new File(xmlPath);

        javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document document = builder.parse(input);

        org.w3c.dom.Element manifest = document.getDocumentElement();

        if (manifest == null || !"manifest".equals(manifest.getNodeName())) {
            return input;
        }

        reorderManifestRootAttributes(manifest);

        File fixed = new File(
                input.getParentFile(),
                input.getName() + ".manifest_root_fixed.xml"
        );

        javax.xml.transform.TransformerFactory transformerFactory =
                javax.xml.transform.TransformerFactory.newInstance();
        javax.xml.transform.Transformer transformer =
                transformerFactory.newTransformer();

        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "utf-8");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
        File parent = fixed.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fixed);
            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(fos)
            );
        } finally {
            if (fos != null) {
                fos.close();
            }
        }

        return fixed;
    }

    private static void reorderManifestRootAttributes(org.w3c.dom.Element manifest) {
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

        if (compileSdkVersion == null || compileSdkVersion.length() == 0) {
            compileSdkVersion = manifest.getAttribute("android:compileSdkVersion");
        }

        if (compileSdkVersionCodename == null || compileSdkVersionCodename.length() == 0) {
            compileSdkVersionCodename = manifest.getAttribute("android:compileSdkVersionCodename");
        }

        org.w3c.dom.NamedNodeMap attrMap = manifest.getAttributes();
        java.util.ArrayList<org.w3c.dom.Attr> oldAttrs = new java.util.ArrayList<>();

        for (int i = 0; i < attrMap.getLength(); i++) {
            org.w3c.dom.Node node = attrMap.item(i);
            if (node instanceof org.w3c.dom.Attr) {
                oldAttrs.add((org.w3c.dom.Attr) node);
            }
        }

        while (manifest.getAttributes().getLength() > 0) {
            org.w3c.dom.Node node = manifest.getAttributes().item(0);
            manifest.removeAttributeNode((org.w3c.dom.Attr) node);
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

        for (org.w3c.dom.Attr attr : oldAttrs) {
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
                manifest.setAttributeNS(
                        attr.getNamespaceURI(),
                        attr.getName(),
                        attr.getValue()
                );
            } else {
                manifest.setAttribute(attr.getName(), attr.getValue());
            }
        }
    }




    public interface DumpLogger {
        void log(String msg);
    }

    public static void dumpAxmlForDebug(String axmlPath, DumpLogger logger) throws IOException {
        byte[] data = java.nio.file.Files.readAllBytes(new File(axmlPath).toPath());

        logger.log("========== AXML DUMP 开始 ==========");
        logger.log("文件: " + axmlPath);
        logger.log("大小: " + data.length + " 字节");

        int xmlType = u16(data, 0);
        int xmlHeaderSize = u16(data, 2);
        int xmlSize = u32(data, 4);

        logger.log("XML头 type=0x" + hex4(xmlType)
                + " headerSize=" + xmlHeaderSize
                + " size=" + xmlSize);

        java.util.ArrayList<String> strings = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> resIds = new java.util.ArrayList<>();

        int off = xmlHeaderSize;

        while (off + 8 <= data.length) {
            int type = u16(data, off);
            int headerSize = u16(data, off + 2);
            int size = u32(data, off + 4);

            logger.log("");
            logger.log("Chunk offset=0x" + hex8(off)
                    + " type=0x" + hex4(type)
                    + " headerSize=" + headerSize
                    + " size=" + size);

            if (size <= 0 || off + size > data.length) {
                logger.log("Chunk大小异常，停止解析");
                break;
            }

            if (type == 0x0001) {
                parseStringPool(data, off, strings, logger);
            } else if (type == 0x0180) {
                parseResourceMap(data, off, size, resIds, strings, logger);
            } else if (type == 0x0102) {
                parseStartElement(data, off, strings, resIds, logger);
            }

            off += size;
        }

        logger.log("========== AXML DUMP 结束 ==========");
    }

    private static void parseStringPool(byte[] data, int off,
                                        java.util.ArrayList<String> strings,
                                        DumpLogger logger) {
        int stringCount = u32(data, off + 8);
        int styleCount = u32(data, off + 12);
        int flags = u32(data, off + 16);
        int stringsStart = u32(data, off + 20);
        int stylesStart = u32(data, off + 24);

        boolean utf8 = (flags & 0x00000100) != 0;

        logger.log("字符串池 stringCount=" + stringCount
                + " styleCount=" + styleCount
                + " flags=0x" + hex8(flags)
                + " encoding=" + (utf8 ? "UTF-8" : "UTF-16"));

        strings.clear();

        for (int i = 0; i < stringCount; i++) {
            int strOff = u32(data, off + 28 + i * 4);
            int abs = off + stringsStart + strOff;

            String s;
            if (utf8) {
                s = readUtf8String(data, abs);
            } else {
                s = readUtf16String(data, abs);
            }

            strings.add(s);
            logger.log("String[" + i + "] = " + s);
        }
    }

    private static void parseResourceMap(byte[] data, int off, int size,
                                         java.util.ArrayList<Integer> resIds,
                                         java.util.ArrayList<String> strings,
                                         DumpLogger logger) {
        resIds.clear();

        int count = (size - 8) / 4;
        logger.log("ResourceMap count=" + count);

        for (int i = 0; i < count; i++) {
            int id = u32(data, off + 8 + i * 4);
            resIds.add(id);

            String name = i < strings.size() ? strings.get(i) : "<无字符串>";
            logger.log("ResMap[" + i + "] string=" + name
                    + " id=0x" + hex8(id)
                    + " known=" + knownAttrName(id));
        }
    }

    private static void parseStartElement(byte[] data, int off,
                                          java.util.ArrayList<String> strings,
                                          java.util.ArrayList<Integer> resIds,
                                          DumpLogger logger) {
        int lineNo = u32(data, off + 8);
        int comment = u32(data, off + 12);
        int nsIdx = s32(data, off + 16);
        int nameIdx = s32(data, off + 20);

        int attrStart = u16(data, off + 24);
        int attrSize = u16(data, off + 26);
        int attrCount = u16(data, off + 28);
        int idIndex = u16(data, off + 30);
        int classIndex = u16(data, off + 32);
        int styleIndex = u16(data, off + 34);

        String tagName = getString(strings, nameIdx);
        String tagNs = getString(strings, nsIdx);

        logger.log("StartElement tag=" + tagName
                + " ns=" + tagNs
                + " line=" + lineNo
                + " attrStart=" + attrStart
                + " attrSize=" + attrSize
                + " attrCount=" + attrCount
                + " idIndex=" + idIndex
                + " classIndex=" + classIndex
                + " styleIndex=" + styleIndex);

        int attrBase = off + 16 + attrStart;

        boolean isManifest = "manifest".equals(tagName);

        for (int i = 0; i < attrCount; i++) {
            int p = attrBase + i * attrSize;

            int attrNsIdx = s32(data, p);
            int attrNameIdx = s32(data, p + 4);
            int rawValueIdx = s32(data, p + 8);

            int valueSize = u16(data, p + 12);
            int valueRes0 = data[p + 14] & 0xff;
            int valueType = data[p + 15] & 0xff;
            int valueData = u32(data, p + 16);

            String attrNs = getString(strings, attrNsIdx);
            String attrName = getString(strings, attrNameIdx);
            String rawValue = getString(strings, rawValueIdx);

            int nameResId = 0;
            if (attrNameIdx >= 0 && attrNameIdx < resIds.size()) {
                nameResId = resIds.get(attrNameIdx);
            }

            String decodedValue = decodeTypedValue(valueType, valueData, strings);

            logger.log("  Attr[" + i + "]"
                    + " nsIdx=" + attrNsIdx
                    + " nameIdx=" + attrNameIdx
                    + " rawIdx=" + rawValueIdx
                    + " ns=" + attrNs
                    + " name=" + attrName
                    + " nameResId=0x" + hex8(nameResId)
                    + " known=" + knownAttrName(nameResId)
                    + " raw=" + rawValue
                    + " valueSize=" + valueSize
                    + " type=0x" + hex2(valueType)
                    + " data=0x" + hex8(valueData)
                    + " decoded=" + decodedValue);

            if (isManifest) {
                if (nameResId == 0x0101021b || "versionCode".equals(attrName)) {
                    logger.log("  >>> 命中 versionCode：nameResId=0x"
                            + hex8(nameResId)
                            + " type=0x" + hex2(valueType)
                            + " data=" + valueData
                            + " raw=" + rawValue
                            + " decoded=" + decodedValue);
                }

                if (nameResId == 0x0101021c || "versionName".equals(attrName)) {
                    logger.log("  >>> 命中 versionName：nameResId=0x"
                            + hex8(nameResId)
                            + " type=0x" + hex2(valueType)
                            + " data=0x" + hex8(valueData)
                            + " raw=" + rawValue
                            + " decoded=" + decodedValue);
                }
            }
        }
    }

    private static String decodeTypedValue(int type, int data,
                                           java.util.ArrayList<String> strings) {
        switch (type) {
            case 0x00:
                return "null";
            case 0x01:
                return "@" + hex8(data);
            case 0x03:
                return getString(strings, data);
            case 0x10:
                return String.valueOf(data);
            case 0x11:
                return "0x" + hex8(data);
            case 0x12:
                return data != 0 ? "true" : "false";
            default:
                return "type=0x" + hex2(type) + ", data=0x" + hex8(data);
        }
    }

    private static String knownAttrName(int id) {
        switch (id) {
            case 0x01010000:
                return "theme";
            case 0x01010001:
                return "label";
            case 0x01010002:
                return "icon";
            case 0x01010003:
                return "name";
            case 0x0101021b:
                return "versionCode";
            case 0x0101021c:
                return "versionName";
            case 0x01010270:
                return "minSdkVersion";
            case 0x01010271:
                return "targetSdkVersion";
            case 0x010104ea:
                return "extractNativeLibs";
            case 0x01010572:
                return "compileSdkVersion";
            case 0x01010573:
                return "compileSdkVersionCodename";
            default:
                return "";
        }
    }

    private static String getString(java.util.ArrayList<String> strings, int index) {
        if (index < 0) return "null";
        if (index >= strings.size()) return "<越界:" + index + ">";
        return strings.get(index);
    }

    private static String readUtf16String(byte[] data, int off) {
        int[] lenInfo = readUtf16Length(data, off);
        int len = lenInfo[0];
        int p = off + lenInfo[1];

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < len; i++) {
            int ch = u16(data, p + i * 2);
            sb.append((char) ch);
        }

        return sb.toString();
    }

    private static int[] readUtf16Length(byte[] data, int off) {
        int first = u16(data, off);

        if ((first & 0x8000) != 0) {
            int second = u16(data, off + 2);
            int len = ((first & 0x7fff) << 16) | second;
            return new int[]{len, 4};
        } else {
            return new int[]{first, 2};
        }
    }

    private static String readUtf8String(byte[] data, int off) {
        int[] utf16Len = readUtf8Length(data, off);
        int p = off + utf16Len[1];

        int[] byteLen = readUtf8Length(data, p);
        p += byteLen[1];

        try {
            return new String(data, p, byteLen[0], "UTF-8");
        } catch (Exception e) {
            return "<UTF8解析失败>";
        }
    }

    private static int[] readUtf8Length(byte[] data, int off) {
        int first = data[off] & 0xff;

        if ((first & 0x80) != 0) {
            int second = data[off + 1] & 0xff;
            int len = ((first & 0x7f) << 8) | second;
            return new int[]{len, 2};
        } else {
            return new int[]{first, 1};
        }
    }

    private static int u16(byte[] data, int off) {
        return (data[off] & 0xff)
                | ((data[off + 1] & 0xff) << 8);
    }

    private static int s32(byte[] data, int off) {
        return (int) u32(data, off);
    }

    private static int u32(byte[] data, int off) {
        return (data[off] & 0xff)
                | ((data[off + 1] & 0xff) << 8)
                | ((data[off + 2] & 0xff) << 16)
                | ((data[off + 3] & 0xff) << 24);
    }

    private static String hex2(int v) {
        return String.format("%02X", v & 0xff);
    }

    private static String hex4(int v) {
        return String.format("%04X", v & 0xffff);
    }

    private static String hex8(int v) {
        return String.format("%08X", v);
    }
}
