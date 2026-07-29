package com.ark.jiagu;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

public class ApkValidatorTest {
    @Test
    public void acceptsMinimalApk() throws Exception {
        File apk = createZip("AndroidManifest.xml", "classes.dex", "classes2.dex");
        ApkValidator.validate(apk);
    }

    @Test(expected = IOException.class)
    public void rejectsMissingPrimaryDex() throws Exception {
        File apk = createZip("AndroidManifest.xml", "classes2.dex");
        ApkValidator.validate(apk);
    }

    @Test(expected = IOException.class)
    public void rejectsDexNumberGap() throws Exception {
        File apk = createZip("AndroidManifest.xml", "classes.dex", "classes3.dex");
        ApkValidator.validate(apk);
    }

    @Test(expected = IOException.class)
    public void rejectsTraversalEntry() throws Exception {
        File apk = createZip("AndroidManifest.xml", "classes.dex", "../payload");
        ApkValidator.validate(apk);
    }

    @Test
    public void sanitizesOutputName() {
        assertEquals("evil.apk", ApkValidator.sanitizeApkFileName("../../evil"));
        assertEquals("unknown.apk", ApkValidator.sanitizeApkFileName(null));
        assertEquals("demo.APK", ApkValidator.sanitizeApkFileName("demo.APK"));
    }

    private static File createZip(String... names) throws Exception {
        File file = File.createTempFile("ark-validator-", ".apk");
        file.deleteOnExit();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            for (String name : names) {
                output.putNextEntry(new ZipEntry(name));
                output.write(1);
                output.closeEntry();
            }
        }
        return file;
    }
}
