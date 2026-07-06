package com.ark.jar.xml2axml.chunks

import com.ark.jar.xml2axml.IntWriter
import java.io.IOException

class EndNameSpaceChunk(
    parent: Chunk<*>,
    start: StartNameSpaceChunk
) : Chunk<EndNameSpaceChunk.H>(parent) {

    @JvmField
    var start: StartNameSpaceChunk = start

    inner class H : Chunk.NodeHeader {
        constructor() : super(ChunkType.XmlEndNamespace) {
            size = 0x18
        }
    }

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        start.writeEx(w)
    }
}
