package com.ark.jar.android.text;

/**
 * Android 兼容版 TextUtils
 * 替代 commons-lang3 StringUtils
 */
public final class TextUtils {

    private TextUtils() {
        // 禁止实例化
    }

    /**
     * 是否为空（null 或长度为 0）
     */
    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    /**
     * 是否不为空
     */
    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    /**
     * null 安全 equals
     */
    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) return false;
        }
        return true;
    }

    /**
     * 判断是否全是空白字符
     */
    public static boolean isBlank(CharSequence cs) {
        if (cs == null) return true;
        int len = cs.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 去掉首尾空白，null 安全
     */
    public static String trimToEmpty(String str) {
        return str == null ? "" : str.trim();
    }
}
