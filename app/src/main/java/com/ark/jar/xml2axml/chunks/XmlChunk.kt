package com.ark.jar.xml2axml.chunks

import android.content.Context
import com.ark.jar.xml2axml.DefaultReferenceResolver
import com.ark.jar.xml2axml.IntWriter
import com.ark.jar.xml2axml.ReferenceResolver
import java.io.IOException

class XmlChunk(val context: Context) : Chunk<XmlChunk.H>(null) {

    inner class H : Chunk.Header() {
        init {
            type = ChunkType.Xml
        }

        @Throws(IOException::class)
        override fun writeEx(w: IntWriter) {
            // no-op
        }
    }

    var stringPool = StringPoolChunk(this)
    var resourceMap = ResourceMapChunk(this)
    var content: TagChunk? = null

    override fun preWrite() {
        header.size = header.headerSize + (content?.calc() ?: 0) + stringPool.calc() + resourceMap.calc()
    }

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        stringPool.write(w)
        resourceMap.write(w)
        content?.write(w)
    }

    fun root(): XmlChunk = this

    private var referenceResolver: ReferenceResolver? = null

    fun getReferenceResolver(): ReferenceResolver {
        if (referenceResolver == null) {
            referenceResolver = DefaultReferenceResolver()
        }
        return referenceResolver!!
    }
}
