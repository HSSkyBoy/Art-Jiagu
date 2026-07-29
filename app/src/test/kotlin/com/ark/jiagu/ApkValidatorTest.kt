package com.ark.jiagu

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkValidatorTest {
    @Test
    fun acceptsMinimalApk() {
        ApkValidator.validate(createZip("AndroidManifest.xml", "classes.dex", "classes2.dex"))
    }

    @Test(expected = IOException::class)
    fun rejectsMissingPrimaryDex() {
        ApkValidator.validate(createZip("AndroidManifest.xml", "classes2.dex"))
    }

    @Test(expected = IOException::class)
    fun rejectsDexNumberGap() {
        ApkValidator.validate(createZip("AndroidManifest.xml", "classes.dex", "classes3.dex"))
    }

    @Test(expected = IOException::class)
    fun rejectsTraversalEntry() {
        ApkValidator.validate(createZip("AndroidManifest.xml", "classes.dex", "../payload"))
    }

    @Test
    fun sanitizesOutputName() {
        assertEquals("evil.apk", ApkValidator.sanitizeApkFileName("../../evil"))
        assertEquals("unknown.apk", ApkValidator.sanitizeApkFileName(null))
        assertEquals("demo.APK", ApkValidator.sanitizeApkFileName("demo.APK"))
    }

    private fun createZip(vararg names: String): File =
        File.createTempFile("ark-validator-", ".apk").apply {
            deleteOnExit()
            ZipOutputStream(FileOutputStream(this)).use { output ->
                names.forEach { name ->
                    output.putNextEntry(ZipEntry(name))
                    output.write(1)
                    output.closeEntry()
                }
            }
        }
}
