package com.ark.jar.xml2axml.chunks

import com.ark.jar.xml2axml.IntWriter
import java.io.IOException
import java.util.LinkedList

class ResourceMapChunk(parent: Chunk<*>) : Chunk<ResourceMapChunk.H>(parent) {

    inner class H : Header(ChunkType.XmlResourceMap) {
        @Throws(IOException::class)
        override fun writeEx(w: IntWriter) {
            // no-op
        }
    }

    private var ids = LinkedList<Int>()

    @Throws(IOException::class)
    override fun preWrite() {
        val rss = stringPool().rawStrings
        ids = LinkedList<Int>()
        for (r in rss) {
            if (r.origin.id >= 0) {
                ids.add(r.origin.id)
            } else {
                break
            }
        }
        header.size = ids.size * 4 + header.headerSize
    }

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        for (i in ids) {
            w.write(i)
        }
    }
}
