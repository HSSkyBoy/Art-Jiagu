/*
 * Copyright 2008 Android4ME
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ark.jar.android.content.res

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * @author Dmitry Skiba
 *
 * Simple helper class that allows reading of integers.
 *
 * TODO:
 * 	* implement buffering
 *
 */
class IntReader {
    constructor()
    constructor(stream: InputStream, bigEndian: Boolean) {
        reset(stream, bigEndian)
    }

    fun reset(stream: InputStream?, bigEndian: Boolean) {
        m_stream = stream
        m_bigEndian = bigEndian
        m_position = 0
    }

    fun close() {
        if (m_stream == null) {
            return
        }
        try {
            m_stream!!.close()
        } catch (e: IOException) {
        }
        reset(null, false)
    }

    fun getStream(): InputStream? {
        return m_stream
    }

    fun isBigEndian(): Boolean {
        return m_bigEndian
    }

    fun setBigEndian(bigEndian: Boolean) {
        m_bigEndian = bigEndian
    }

    @Throws(IOException::class)
    fun readByte(): Int {
        return readInt(1)
    }

    @Throws(IOException::class)
    fun readShort(): Int {
        return readInt(2)
    }

    @Throws(IOException::class)
    fun readInt(): Int {
        return readInt(4)
    }

    @Throws(IOException::class)
    fun readInt(length: Int): Int {
        if (length < 0 || length > 4) {
            throw IllegalArgumentException()
        }
        var result = 0
        if (m_bigEndian) {
            var i = (length - 1) * 8
            while (i >= 0) {
                val b = m_stream!!.read()
                if (b == -1) {
                    throw EOFException()
                }
                m_position += 1
                result = result or (b shl i)
                i -= 8
            }
        } else {
            var len = length * 8
            var i = 0
            while (i != len) {
                val b = m_stream!!.read()
                if (b == -1) {
                    throw EOFException()
                }
                m_position += 1
                result = result or (b shl i)
                i += 8
            }
        }
        return result
    }

    @Throws(IOException::class)
    fun readIntArray(length: Int): IntArray {
        val array = IntArray(length)
        readIntArray(array, 0, length)
        return array
    }

    @Throws(IOException::class)
    fun readIntArray(array: IntArray, offset: Int, length: Int) {
        var off = offset
        var len = length
        while (len > 0) {
            array[off++] = readInt()
            len -= 1
        }
    }

    @Throws(IOException::class)
    fun readByteArray(length: Int): ByteArray {
        val array = ByteArray(length)
        val read = m_stream!!.read(array)
        m_position += read
        if (read != length) {
            throw EOFException()
        }
        return array
    }

    @Throws(IOException::class)
    fun skip(bytes: Int) {
        if (bytes <= 0) {
            return
        }
        val skipped = m_stream!!.skip(bytes.toLong())
        m_position += skipped.toInt()
        if (skipped != bytes.toLong()) {
            throw EOFException()
        }
    }

    @Throws(IOException::class)
    fun skipInt() {
        skip(4)
    }

    @Throws(IOException::class)
    fun available(): Int {
        return m_stream!!.available()
    }

    fun getPosition(): Int {
        return m_position
    }

    private var m_stream: InputStream? = null
    private var m_bigEndian = false
    private var m_position = 0
}
