package com.ark.jar.xml2axml.chunks

import com.ark.jar.xml2axml.IntWriter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.LinkedList
import java.util.Stack

class StartTagChunk @Throws(XmlPullParserException::class) constructor(parent: Chunk<*>, p: XmlPullParser) : Chunk<StartTagChunk.H>(parent) {

    inner class H : NodeHeader(ChunkType.XmlStartElement)

    lateinit var name: String
    var prefix: String? = null
    var namespace: String? = null
    var attrStart: Short = 20
    var attrSize: Short = 20
    var idIndex: Short = 0
    var styleIndex: Short = 0
    var classIndex: Short = 0
    val attrs: LinkedList<AttrChunk> = LinkedList()
    val startNameSpace: MutableList<StartNameSpaceChunk> = Stack()

    init {
        name = p.name!!
        stringPool().addString(name)
        prefix = p.prefix
        namespace = p.namespace
        if (prefix != null && prefix!!.isEmpty()) prefix = null
        if (namespace != null && namespace!!.isEmpty()) namespace = null
        val ac = p.attributeCount
        for (i in 0 until ac) {
            var attrPrefix: String? = p.getAttributePrefix(i)
            var attrNamespace: String? = p.getAttributeNamespace(i)
            val attrName = p.getAttributeName(i)
            val attrValue = p.getAttributeValue(i)
            if (attrPrefix != null && attrPrefix.isEmpty()) attrPrefix = null
            if (attrNamespace != null && attrNamespace.isEmpty()) attrNamespace = null
            val attr = AttrChunk(this)
            attr.prefix = attrPrefix
            attr.namespace = attrNamespace
            attr.rawValue = attrValue
            attr.name = attrName
            stringPool().addString(attrNamespace, attrName)
            attrs.add(attr)
            if ("id" == attrName && "http://schemas.android.com/apk/res/android" == attrNamespace) {
                idIndex = i.toShort()
            } else if (attrPrefix == null && "style" == attrName) {
                styleIndex = i.toShort()
            } else if (attrPrefix == null && "class" == attrName) {
                classIndex = i.toShort()
            }
        }
        val nsStart = p.getNamespaceCount(p.depth - 1)
        val nsEnd = p.getNamespaceCount(p.depth)
        for (j in nsStart until nsEnd) {
            var nsPrefix: String? = p.getNamespacePrefix(j)
            var nsUri: String? = p.getNamespaceUri(j)
            if (nsPrefix != null && nsPrefix.isEmpty()) nsPrefix = null
            if (nsUri != null && nsUri.isEmpty()) nsUri = null
            if (nsUri == null) continue
            val snc = StartNameSpaceChunk(parent)
            snc.prefix = nsPrefix
            snc.uri = nsUri
            if (nsPrefix != null) stringPool().addString(null, nsPrefix)
            stringPool().addString(null, nsUri)
            startNameSpace.add(snc)
        }
    }

    override fun preWrite() {
        for (a in attrs) a.calc()
        header.size = 36 + 20 * attrs.size
    }

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        w.write(stringIndex(null, namespace))
        w.write(stringIndex(null, name))
        w.write(attrStart)
        w.write(attrSize)
        w.write(attrs.size.toShort())
        w.write(idIndex)
        w.write(classIndex)
        w.write(styleIndex)
        for (a in attrs) {
            a.write(w)
        }
    }
}
