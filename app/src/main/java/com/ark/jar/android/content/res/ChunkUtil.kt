package com.ark.jar.android.content.res

import java.io.IOException

internal object ChunkUtil {
    @JvmStatic
    @Throws(IOException::class)
    fun readCheckType(reader: IntReader, expectedType: Int) {
        val type = reader.readInt()
        if (type != expectedType) {
            throw IOException("Expected chunk of type 0x" + Integer.toHexString(expectedType) + ", read 0x" + Integer.toHexString(type) + ".")
        }
    }
}
