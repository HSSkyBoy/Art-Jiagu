package com.ark.jar.xml2axml.chunks

import com.ark.jar.xml2axml.IntWriter
import org.xmlpull.v1.XmlPullParser
import java.io.IOException

class TagChunk(parent: Chunk, p: XmlPullParser) : Chunk<Chunk.EmptyHeader>(parent) {

    var startNameSpace: List<StartNameSpaceChunk> = emptyList()
    lateinit var startTag: StartTagChunk
    val content: MutableList<TagChunk> = mutableListOf()
    lateinit var endTag: EndTagChunk
    val endNameSpace: MutableList<EndNameSpaceChunk> = mutableListOf()

    init {
        when (parent) {
            is TagChunk -> parent.content.add(this)
            is XmlChunk -> parent.content = this
            else -> throw IllegalArgumentException("parent must be XmlChunk or TagChunk")
        }
        startTag = StartTagChunk(this, p)
        endTag = EndTagChunk(this, startTag)
        startNameSpace = startTag.startNameSpace
        for (c in startNameSpace) {
            endNameSpace.add(EndNameSpaceChunk(this, c))
        }
        endTag.header.lineNo = p.lineNumber
        startTag.header.lineNo = p.lineNumber
    }

    override fun preWrite() {
        var sum = 0
        for (e in startNameSpace) sum += e.calc()
        for (e in endNameSpace) sum += e.calc()
        sum += startTag.calc()
        sum += endTag.calc()
        for (c in content) sum += c.calc()
        header.size = sum
    }

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        for (c in startNameSpace) c.write(w)
        startTag.write(w)
        for (c in content) c.write(w)
        endTag.write(w)
        for (c in endNameSpace) c.write(w)
    }
}
