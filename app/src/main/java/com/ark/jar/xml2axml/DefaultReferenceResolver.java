package com.ark.jar.xml2axml;

import com.ark.jar.xml2axml.chunks.ValueChunk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android 修复版 DefaultReferenceResolver
 */
public class DefaultReferenceResolver implements ReferenceResolver {

    // 支持：
    // @id/name
    // @+id/name
    // @pkg:id/name
    // @pkg:type/name
    static final Pattern PATTERN =
            Pattern.compile("^@\\+?(?:(\\w+):)?(?:(\\w+)/)?(\\w+)$");

    @Override
    public int resolve(ValueChunk value, String ref) {

        Matcher m = PATTERN.matcher(ref);
        if (!m.matches()) {
            throw new IllegalArgumentException("非法资源引用: " + ref);
        }

        String pkg = m.group(1);
        String type = m.group(2);
        String name = m.group(3);

        // 1. 尝试按数字解析（@0x7f010001 这类）
        try {
            return Integer.parseInt(name, Encoder.Config.defaultReferenceRadix);
        } catch (NumberFormatException ignore) {
            // 忽略，继续走资源解析
        }

        // 2. 包名兜底
        if (pkg == null || pkg.length() == 0) {
            pkg = value.getContext().getPackageName();
        }

        // 3. 资源类型兜底（极端情况）
        if (type == null || type.length() == 0) {
            return 0;
        }

        return value.getContext()
                .getResources()
                .getIdentifier(name, type, pkg);
    }
}
