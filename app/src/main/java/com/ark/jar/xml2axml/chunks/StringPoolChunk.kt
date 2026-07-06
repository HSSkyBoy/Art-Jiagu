package com.ark.jar.xml2axml.chunks

import android.text.TextUtils
import com.ark.jar.xml2axml.Encoder
import com.ark.jar.xml2axml.IntWriter
import java.io.IOException
import java.util.*

class StringPoolChunk(parent: Chunk<*>) : Chunk<StringPoolChunk.H>(parent) {

    inner class H : Chunk.Header {
        var stringCount: Int = 0
        var styleCount: Int = 0
        var flags: Int = 0
        var stringPoolOffset: Int = 0
        var stylePoolOffset: Int = 0

        constructor() : super(ChunkType.StringPool)

        @Throws(IOException::class)
        override fun writeEx(w: IntWriter) {
            w.write(stringCount)
            w.write(styleCount)
            w.write(flags)
            w.write(stringPoolOffset)
            w.write(stylePoolOffset)
        }
    }

    class RawString {
        var origin: StringItem? = null
        var cdata: CharArray? = null
        var bdata: ByteArray? = null

        fun length(): Int {
            return if (cdata != null) cdata!!.size else origin!!.string.length
        }

        fun padding(): Int {
            return if (cdata != null) (cdata!!.size * 2 + 4) and 2 else 0
        }

        fun size(): Int {
            return if (cdata != null) {
                cdata!!.size * 2 + 4 + padding()
            } else {
                bdata!!.size + 3 + padding()
            }
        }

        @Throws(IOException::class)
        fun write(w: IntWriter) {
            val pos = w.getPos()
            if (cdata != null) {
                w.write(length().toShort())
                for (c in cdata!!) w.write(c)
                w.write(0.toShort())
                if (padding() == 2) w.write(0.toShort())
            } else {
                w.write(length().toByte())
                w.write(bdata!!.size.toByte())
                for (c in bdata!!) w.write(c)
                w.write(0.toByte())
                for (i in 0 until padding()) w.write(0.toByte())
            }
            val written = w.getPos() - pos
            if (written != size()) {
                throw IllegalStateException("RawString 写入长度错误: $written != ${size()}")
            }
        }
    }

    enum class Encoding { UNICODE, UTF8 }

    var stringsOffset: IntArray = IntArray(0)
    var stylesOffset: IntArray = IntArray(0)
    var rawStrings: ArrayList<RawString> = ArrayList()
    var encoding: Encoding = Encoder.Config.encoding

    private val map = HashMap<String, LinkedList<StringItem>>()

    override fun preWrite() {
        rawStrings = ArrayList()
        val offsets = LinkedList<Int>()
        var off = 0

        if (encoding == Encoding.UNICODE) {
            for (ss in map.values) {
                for (s in ss) {
                    val r = RawString()
                    r.cdata = s.string.toCharArray()
                    r.origin = s
                    rawStrings.add(r)
                }
            }
        } else {
            for (ss in map.values) {
                for (s in ss) {
                    val r = RawString()
                    try {
                        r.bdata = s.string.toByteArray("UTF-8")
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                    r.origin = s
                    rawStrings.add(r)
                }
            }
        }

        rawStrings.sortBy { if (it.origin!!.id == -1) Int.MAX_VALUE else it.origin!!.id }

        for (r in rawStrings) {
            offsets.add(off)
            off += r.size()
        }

        header.stringCount = rawStrings.size
        header.styleCount = 0
        header.size = off + header.headerSize + header.stringCount * 4 + header.styleCount * 4
        header.stringPoolOffset = offsets.size * 4 + header.headerSize
        header.stylePoolOffset = 0

        stringsOffset = IntArray(offsets.size)
        var i = 0
        for (x in offsets) stringsOffset[i++] = x

        stylesOffset = IntArray(0)

        if (encoding == Encoding.UTF8) header.flags = header.flags or 0x100
    }

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        for (i in stringsOffset) w.write(i)
        for (i in stylesOffset) w.write(i)
        for (r in rawStrings) r.write(w)
    }

    inner class StringItem {
        var namespace: String? = null
        var string: String
        var id: Int = -1

        constructor(s: String) {
            string = s
        }

        constructor(namespace: String?, s: String) {
            this.string = s
            this.namespace = namespace
            genId()
        }

        fun setNamespace(namespace: String?) {
            this.namespace = namespace
            genId()
        }

        fun genId() {
            if (namespace == null) return
            val pkg: String
            if ("http://schemas.android.com/apk/res-auto" == namespace) {
                pkg = getContext().packageName
            } else if (namespace!!.startsWith("http://schemas.android.com/apk/res/")) {
                pkg = namespace!!.substring("http://schemas.android.com/apk/res/".length)
            } else {
                return
            }
            id = getContext().resources!!.getIdentifier(string, "attr", pkg)
        }
    }

    fun addString(s: String?) {
        if (s == null) return
        var list = map[s]
        if (list == null) {
            list = LinkedList()
            map[s] = list
        }
        if (list.isEmpty()) {
            list.add(StringItem(s))
        }
    }

    fun addString(namespace: String?, s: String?) {
        if (s == null) return
        var list = map[s]
        if (list == null) {
            list = LinkedList()
            map[s] = list
        }
        for (e in list) {
            if (e.namespace == null || TextUtils.equals(e.namespace, namespace)) {
                e.setNamespace(namespace)
                return
            }
        }
        list.add(StringItem(namespace, s))
    }

    override fun stringIndex(namespace: String?, s: String?): Int {
        if (s == null) return -1
        val l = rawStrings.size
        for (i in 0 until l) {
            val item = rawStrings[i].origin!!
            if (s == item.string && (TextUtils.isEmpty(namespace) || TextUtils.equals(namespace, item.namespace))) {
                return i
            }
        }
        if (TextUtils.isEmpty(s)) return -1
        throw RuntimeException("String 未找到: $s")
    }
}
