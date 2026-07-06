package com.ark.jar.android.text

object TextUtils {
    @JvmStatic
    fun isEmpty(cs: CharSequence?): Boolean = cs == null || cs.length == 0

    @JvmStatic
    fun isNotEmpty(cs: CharSequence?): Boolean = !isEmpty(cs)

    @JvmStatic
    fun equals(a: CharSequence?, b: CharSequence?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        if (a.length != b.length) return false
        for (i in a.indices) {
            if (a[i] != b[i]) return false
        }
        return true
    }

    @JvmStatic
    fun isBlank(cs: CharSequence?): Boolean {
        if (cs == null) return true
        val len = cs.length
        for (i in 0 until len) {
            if (!Character.isWhitespace(cs[i])) return false
        }
        return true
    }

    @JvmStatic
    fun trimToEmpty(str: String?): String = str?.trim() ?: ""
}
