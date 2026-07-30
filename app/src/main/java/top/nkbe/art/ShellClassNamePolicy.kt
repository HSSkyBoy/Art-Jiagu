package top.nkbe.art

import java.util.Locale

/**
 * Keeps the shell class written into protected APKs independent from this app's own namespace.
 */
object ShellClassNamePolicy {
    const val DEFAULT_CLASS_NAME = "top.nkbe.safe.StubApp"

    @JvmStatic
    fun normalize(configuredName: String?): String {
        val candidate = configuredName?.trim().orEmpty()
        return if (candidate.isEmpty() || containsArt(candidate)) {
            DEFAULT_CLASS_NAME
        } else {
            candidate
        }
    }

    @JvmStatic
    fun containsArt(className: String?): Boolean =
        className?.lowercase(Locale.ROOT)?.contains("art") == true
}
