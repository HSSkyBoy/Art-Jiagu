package com.ark.jar.xml2axml;

import com.ark.jar.xml2axml.test.AXMLPrinter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * Created by Roy on 16-4-27.
 * Android 兼容修复版
 */
public class AxmlUtils {

    /**
     * 从二进制 AXML 数据解码成 XML 字符串
     */
    public static String decode(byte[] data) {
        try (InputStream is = new ByteArrayInputStream(data);
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {

            AXMLPrinter.out = new PrintStream(os);
            AXMLPrinter.decode(is);
            AXMLPrinter.out.close();

            return new String(os.toByteArray(), "UTF-8");

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从文件解码
     */
    public static String decode(File file) throws IOException {
        return decode(readFileToByteArray(file));
    }

    /**
     * 编码 XML 字符串为二进制 AXML
     */
    public static byte[] encode(String xml) {
        try {
            Encoder encoder = new Encoder();
            // 必须由外部传 Context，这里保持与你前面修复一致
            return encoder.encodeString(null, xml);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 编码 XML 文件为二进制 AXML
     */
    public static byte[] encode(File file) {
        try {
            Encoder encoder = new Encoder();
            return encoder.encodeFile(null, file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 原生方式读取文件为 byte[]
     */
    private static byte[] readFileToByteArray(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }
}
