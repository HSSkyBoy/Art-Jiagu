package com.ark.jar.xml2axml

import com.ark.jar.xml2axml.chunks.ValueChunk
import java.util.regex.Pattern

class DefaultReferenceResolver : ReferenceResolver {

    companion object {
        // 支持：
        // @id/name
        // @+id/name
        // @pkg:id/name
        // @pkg:type/name
        val PATTERN = Pattern.compile("^@\\+?(?:(\\w+):)?(?:(\\w+)/)?(\\w+)$")
    }

    override fun resolve(value: ValueChunk, ref: String): Int {
        val m = PATTERN.matcher(ref)
        if (!m.matches()) {
            throw IllegalArgumentException("非法资源引用: $ref")
        }

        var pkg = m.group(1)
        val type = m.group(2)
        val name = m.group(3)!!

        // 1. 尝试按数字解析（@0x7f010001 这类）
        try {
            return name.toInt(Encoder.Config.defaultReferenceRadix)
        } catch (_: NumberFormatException) {
            // 忽略，继续走资源解析
        }

        // 2. 包名兜底
        if (pkg.isNullOrEmpty()) {
            pkg = value.context.packageName
        }

        // 3. 资源类型兜底（极端情况）
        if (type.isNullOrEmpty()) {
            return 0
        }

        return value.context.resources.getIdentifier(name, type, pkg!!)
    }
}
