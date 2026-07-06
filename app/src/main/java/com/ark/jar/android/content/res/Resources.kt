package com.ark.jar.android.content.res

import java.lang.reflect.Field

class Resources {
    val attrMap = HashMap<String, Int>()

    init {
        try {
            val fields: Array<Field> = android.R.attr::class.java.fields
            for (field in fields) {
                attrMap[field.name] = field.get(null) as Int
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun getIdentifier(name: String, type: String, pkg: String, log: Boolean): Int {
        if ("android" == pkg && "attr" == type) {
            val x = attrMap[name]
            if (x != null) {
                if (log) println(String.format("@%s:%s/%s=0x%x", pkg, type, name, x))
                return x
            }
            if (log) println("attr not found: $name")
        } else {
            if (log) println(String.format("@%s:%s/%s=0x%x", pkg, type, name, 0))
        }
        return 0
    }
}
