package com.ark.jar.xml2axml.chunks

import android.content.Context
import com.ark.jar.xml2axml.IntWriter
import com.ark.jar.xml2axml.ReferenceResolver
import java.io.IOException
import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

abstract class Chunk<H : Chunk<H>.Header>(parent: Chunk<*>?) {

    inner abstract class Header(val ct: ChunkType) {
        var type: Short = ct.type
        var headerSize: Short = ct.headerSize
        var size: Int = 0

        @Throws(IOException::class)
        open fun write(w: IntWriter) {
            w.write(type)
            w.write(headerSize)
            w.write(size)
            writeEx(w)
        }

        @Throws(IOException::class)
        abstract fun writeEx(w: IntWriter)
    }

    inner abstract class NodeHeader(ct: ChunkType) : Header(ct) {
        var lineNo: Int = 1
        var comment: Int = -1

        init {
            headerSize = 0x10.toShort()
        }

        @Throws(IOException::class)
        override fun write(w: IntWriter) {
            w.write(type)
            w.write(headerSize)
            w.write(size)
            w.write(lineNo)
            w.write(comment)
            writeEx(w)
        }

        @Throws(IOException::class)
        override fun writeEx(w: IntWriter) {
            // no-op
        }
    }

    inner class EmptyHeader : Header(ChunkType.Null) {
        @Throws(IOException::class)
        override fun writeEx(w: IntWriter) {
            // no-op
        }

        @Throws(IOException::class)
        override fun write(w: IntWriter) {
            // no-op
        }
    }

    protected var context: Context? = null
    private var parent: Chunk<*>? = parent
    var header: H? = null

    @Suppress("UNCHECKED_CAST")
    init {
        try {
            val superType: Type = this.javaClass.genericSuperclass
            if (superType !is ParameterizedType) {
                throw IllegalStateException(
                    "Chunk subclass must retain generic Header declaration: " + this.javaClass.name
                )
            }
            val headerType: Type = superType.actualTypeArguments[0]
            val headerClass = headerType as Class<H>
            val constructors: Array<Constructor<*>> = headerClass.constructors
            for (c in constructors) {
                val params: Array<Class<*>> = c.parameterTypes
                if (params.size == 1 && Chunk::class.java.isAssignableFrom(params[0])) {
                    header = c.newInstance(this@Chunk) as H
                    return
                }
            }
            throw IllegalStateException(
                "Header(Chunk) constructor not found: " + headerClass.name
            )
        } catch (e: Exception) {
            throw RuntimeException("Failed to init Chunk Header", e)
        }
    }

    @Throws(IOException::class)
    open fun write(w: IntWriter) {
        val pos = w.getPos()
        calc()
        header!!.write(w)
        writeEx(w)
        val written = w.getPos() - pos
        if (written != header!!.size) {
            throw IllegalStateException(
                "Chunk write size mismatch: actual $written expected ${header!!.size} type " + this.javaClass.name
            )
        }
    }

    fun getParent(): Chunk<*>? = parent

    fun getContext(): Context {
        if (context != null) return context!!
        if (parent == null) throw IllegalStateException("Root Chunk has no Context")
        return parent!!.getContext()
    }

    private var isCalculated = false

    fun calc(): Int {
        if (!isCalculated) {
            preWrite()
            isCalculated = true
        }
        return header!!.size
    }

    private var _root: XmlChunk? = null

    fun root(): XmlChunk {
        if (_root != null) return _root!!
        if (parent == null) throw IllegalStateException("Cannot find XmlChunk Root")
        return parent!!.root()
    }

    fun stringIndex(namespace: String, s: String): Int {
        return stringPool().stringIndex(namespace, s)
    }

    fun stringPool(): StringPoolChunk {
        return root().stringPool
    }

    fun getReferenceResolver(): ReferenceResolver {
        return root().getReferenceResolver()
    }

    open fun preWrite() {
        // no-op
    }

    @Throws(IOException::class)
    abstract fun writeEx(w: IntWriter)
}
