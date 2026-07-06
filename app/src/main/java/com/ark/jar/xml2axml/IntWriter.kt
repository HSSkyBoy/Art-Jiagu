package com.ark.jar.xml2axml

import java.io.IOException
import java.io.OutputStream

class IntWriter(private val os: OutputStream) {
    var bigEndian: Boolean = false
    private var pos: Int = 0

    @Throws(IOException::class)
    fun write(b: Byte) {
        os.write(b.toInt())
        pos += 1
    }

    @Throws(IOException::class)
    fun write(s: Short) {
        if (!bigEndian) {
            os.write(s.toInt() and 0xff)
            os.write((s.toInt() ushr 8) and 0xff)
        } else {
            os.write((s.toInt() ushr 8) and 0xff)
            os.write(s.toInt() and 0xff)
        }
        pos += 2
    }

    @Throws(IOException::class)
    fun write(x: Char) {
        write(x.code.toShort())
    }

    @Throws(IOException::class)
    fun write(x: Int) {
        if (!bigEndian) {
            var v = x
            os.write(v and 0xff)
            v = v ushr 8
            os.write(v and 0xff)
            v = v ushr 8
            os.write(v and 0xff)
            v = v ushr 8
            os.write(v and 0xff)
        } else {
            throw NotImplementedException()
        }
        pos += 4
    }

    @Throws(IOException::class)
    fun writePlaceHolder(len: Int, name: String) {
        os.write(ByteArray(len))
    }

    @Throws(IOException::class)
    fun close() {
        os.close()
    }

    fun getPos(): Int = pos
}
