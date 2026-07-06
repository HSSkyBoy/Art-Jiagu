package com.ark.jar.xml2axml.chunks

import android.text.TextUtils
import com.ark.jar.xml2axml.IntWriter
import com.ark.jar.xml2axml.ValueType
import java.io.IOException

class AttrChunk(startTagChunk: StartTagChunk) : Chunk<Chunk.EmptyHeader>(startTagChunk) {
    private val startTagChunk: StartTagChunk = startTagChunk
    var prefix: String? = null
    var name: String? = null
    var namespace: String? = null
    var rawValue: String? = null

    val value: ValueChunk = ValueChunk(this)

    init {
        header.size = 20
    }

    fun preWrite() {
        value.calc()
    }

    @Throws(IOException::class)
    fun writeEx(w: IntWriter) {
        w.write(startTagChunk.stringIndex(null, if (TextUtils.isEmpty(namespace)) null else namespace))
        w.write(startTagChunk.stringIndex(namespace, name))
        if (value.type == ValueType.STRING) {
            w.write(startTagChunk.stringIndex(null, rawValue))
        } else {
            w.write(-1)
        }
        value.write(w)
    }
}
