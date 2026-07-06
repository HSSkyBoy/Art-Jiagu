package com.ark.jar.xml2axml.chunks

import com.ark.jar.xml2axml.IntWriter
import java.io.IOException

class StartNameSpaceChunk(parent: Chunk<*>?) : Chunk<StartNameSpaceChunk.H>(parent) {

    inner class H : Chunk.NodeHeader(ChunkType.XmlStartNamespace) {
        init {
            size = 0x18
        }
    }

    var prefix: String? = null
    var uri: String? = null

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        w.write(stringIndex(null, prefix))
        w.write(stringIndex(null, uri))
    }
}
