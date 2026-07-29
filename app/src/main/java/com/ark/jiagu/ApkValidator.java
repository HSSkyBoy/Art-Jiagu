package com.ark.jiagu;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ApkValidator {
    private static final int MAX_ENTRY_COUNT = 100_000;
    private static final int MAX_DEX_COUNT = 128;
    private static final Pattern DEX_NAME = Pattern.compile("^classes(\\d*)\\.dex$");

    private ApkValidator() {
    }

    static void validate(File apkFile) throws IOException {
        if (apkFile == null || !apkFile.isFile() || apkFile.length() == 0) {
            throw new IOException("APK 文件不存在或为空");
        }

        Set<String> names = new HashSet<>();
        Set<Integer> dexIndexes = new HashSet<>();
        boolean hasManifest = false;
        int entryCount = 0;

        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                entryCount++;
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new IOException("APK 条目数量异常");
                }
                if (!isSafeEntryName(name)) {
                    throw new IOException("APK 包含不安全路径：" + name);
                }
                if (!names.add(name)) {
                    throw new IOException("APK 包含重复条目：" + name);
                }
                if ("AndroidManifest.xml".equals(name) && !entry.isDirectory()) {
                    hasManifest = true;
                }

                Matcher matcher = DEX_NAME.matcher(name);
                if (matcher.matches() && !entry.isDirectory()) {
                    String suffix = matcher.group(1);
                    int index;
                    try {
                        index = suffix.isEmpty() ? 1 : Integer.parseInt(suffix);
                    } catch (NumberFormatException exception) {
                        throw new IOException("DEX 编号无效：" + name, exception);
                    }
                    if (index < 1 || index > MAX_DEX_COUNT) {
                        throw new IOException("DEX 编号超出支持范围：" + name);
                    }
                    dexIndexes.add(index);
                }
            }
        }

        if (!hasManifest) {
            throw new IOException("APK 中缺少 AndroidManifest.xml");
        }
        if (!dexIndexes.contains(1)) {
            throw new IOException("APK 中缺少 classes.dex");
        }
        for (int index = 1; index <= dexIndexes.size(); index++) {
            if (!dexIndexes.contains(index)) {
                throw new IOException("APK 的 DEX 编号不连续：缺少 classes" + index + ".dex");
            }
        }
    }

    static String sanitizeApkFileName(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        name = name.replace('\\', '/');
        int lastSeparator = name.lastIndexOf('/');
        if (lastSeparator >= 0) {
            name = name.substring(lastSeparator + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}:*?\"<>|]", "_");
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return "unknown.apk";
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            name += ".apk";
        }
        return name;
    }

    private static boolean isSafeEntryName(String name) {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.startsWith("\\")) {
            return false;
        }
        String normalized = name.replace('\\', '/');
        if (normalized.matches("^[A-Za-z]:.*")) {
            return false;
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
    }
}
