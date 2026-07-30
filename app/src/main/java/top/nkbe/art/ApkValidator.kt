package top.nkbe.art

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipFile

object ApkValidator {
    private const val MAX_ENTRY_COUNT = 100_000
    private const val MAX_DEX_COUNT = 128
    private val dexName = Regex("""^classes(\d*)\.dex$""")
    private val invalidFileNameChars = Regex("""[\p{Cntrl}:*?"<>|]""")
    private val drivePath = Regex("""^[A-Za-z]:.*""")

    @JvmStatic
    @Throws(IOException::class)
    fun validate(apkFile: File?) {
        if (apkFile == null || !apkFile.isFile || apkFile.length() == 0L) {
            throw IOException("APK 文件不存在或为空")
        }

        val names = hashSetOf<String>()
        val dexIndexes = hashSetOf<Int>()
        var hasManifest = false
        var entryCount = 0

        ZipFile(apkFile).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                if (++entryCount > MAX_ENTRY_COUNT) {
                    throw IOException("APK 条目数量异常")
                }
                if (!isSafeEntryName(name)) {
                    throw IOException("APK 包含不安全路径：$name")
                }
                if (!names.add(name)) {
                    throw IOException("APK 包含重复条目：$name")
                }
                if (name == "AndroidManifest.xml" && !entry.isDirectory) {
                    hasManifest = true
                }

                val match = dexName.matchEntire(name)
                if (match != null && !entry.isDirectory) {
                    val suffix = match.groupValues[1]
                    val index = if (suffix.isEmpty()) {
                        1
                    } else {
                        suffix.toIntOrNull() ?: throw IOException("DEX 编号无效：$name")
                    }
                    if (index !in 1..MAX_DEX_COUNT) {
                        throw IOException("DEX 编号超出支持范围：$name")
                    }
                    dexIndexes += index
                }
            }
        }

        if (!hasManifest) {
            throw IOException("APK 中缺少 AndroidManifest.xml")
        }
        if (1 !in dexIndexes) {
            throw IOException("APK 中缺少 classes.dex")
        }
        for (index in 1..dexIndexes.size) {
            if (index !in dexIndexes) {
                throw IOException("APK 的 DEX 编号不连续：缺少 classes$index.dex")
            }
        }
    }

    @JvmStatic
    fun sanitizeApkFileName(fileName: String?): String {
        var name = fileName.orEmpty().trim().replace('\\', '/').substringAfterLast('/')
        name = invalidFileNameChars.replace(name, "_").trimStart('.')
        if (name.isEmpty()) {
            return "unknown.apk"
        }
        if (!name.lowercase(Locale.ROOT).endsWith(".apk")) {
            name += ".apk"
        }
        return name
    }

    private fun isSafeEntryName(name: String?): Boolean {
        if (name.isNullOrEmpty() || name.startsWith('/') || name.startsWith('\\')) {
            return false
        }
        val normalized = name.replace('\\', '/')
        if (drivePath.matches(normalized)) {
            return false
        }
        return normalized.split('/').none { it == ".." }
    }
}
