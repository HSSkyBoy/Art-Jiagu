package com.ark.jar.xml2axml.chunks

import com.ark.jar.xml2axml.IntWriter
import java.io.IOException

/**
 * Created by Roy on 15-10-5.
 */
class EndTagChunk(parent: Chunk<*>, val start: StartTagChunk) : Chunk<EndTagChunk.H>(parent) {

    inner class H : NodeHeader(ChunkType.XmlEndElement) {
        init {
            size = 24
        }
    }

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        w.write(stringIndex(null, start.namespace))
        w.write(stringIndex(null, start.name))
    }
}
