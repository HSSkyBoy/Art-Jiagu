/*
 * Copyright 2008 Android4ME
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ark.jar.android.content.res

import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * @author Dmitry Skiba
 *
 * Block of strings, used in binary xml and arsc.
 */
class StringBlock private constructor() {
    private var m_stringOffsets: IntArray? = null
    private var m_strings: IntArray? = null
    private var m_styleOffsets: IntArray? = null
    private var m_styles: IntArray? = null
    private var m_isUTF8 = false

    companion object {
        private const val CHUNK_TYPE = 0x001C0001
        private const val UTF8_FLAG = 0x00000100

        @JvmStatic
        @Throws(IOException::class)
        fun read(reader: IntReader): StringBlock {
            ChunkUtil.readCheckType(reader, CHUNK_TYPE)
            val chunkSize = reader.readInt()
            val stringCount = reader.readInt()
            val styleOffsetCount = reader.readInt()
            val flags = reader.readInt()
            val stringsOffset = reader.readInt()
            val stylesOffset = reader.readInt()
            val block = StringBlock()
            block.m_isUTF8 = (flags and UTF8_FLAG) != 0
            block.m_stringOffsets = reader.readIntArray(stringCount)
            if (styleOffsetCount != 0) {
                block.m_styleOffsets = reader.readIntArray(styleOffsetCount)
            }
            run {
                val size = ((if (stylesOffset == 0) chunkSize else stylesOffset) - stringsOffset)
                if ((size % 4) != 0) throw IOException("String data size is not multiple of 4 ($size).")
                block.m_strings = reader.readIntArray(size / 4)
            }
            if (stylesOffset != 0) {
                val size = (chunkSize - stylesOffset)
                if ((size % 4) != 0) throw IOException("Style data size is not multiple of 4 ($size).")
                block.m_styles = reader.readIntArray(size / 4)
            }
            return block
        }

        private fun getShort(array: IntArray?, offset: Int, dataSizeBytes: Int): Int {
            if (array == null) return -1
            if (offset < 0 || offset + 1 >= dataSizeBytes) return -1
            val index = offset / 4
            if (index < 0 || index >= array.size) return -1
            val value = array[index]
            return if ((offset % 4 / 2) == 0) value and 0xFFFF else (value ushr 16) and 0xFFFF
        }

        private fun getByte(array: IntArray, offset: Int): Int {
            val value = array[offset / 4]
            val shift = (offset % 4) * 8
            return (value ushr shift) and 0xFF
        }

        private fun getVarint(array: IntArray, offset: Int, dataSizeBytes: Int, out: IntArray): Int {
            var off = offset
            if (off < 0 || off >= dataSizeBytes) return -1
            val b0 = getByte(array, off); off += 1
            if ((b0 and 0x80) == 0) { out[0] = b0; return off }
            if (off >= dataSizeBytes) return -1
            val b1 = getByte(array, off); off += 1
            out[0] = ((b0 and 0x7F) shl 8) or b1
            return off
        }

        private fun getShortLenUtf16(array: IntArray, offset: Int, dataSizeBytes: Int, outLen: IntArray): Int {
            var off = offset
            val s0 = getShort(array, off, dataSizeBytes); if (s0 < 0) return -1; off += 2
            if ((s0 and 0x8000) == 0) { outLen[0] = s0; return off }
            val s1 = getShort(array, off, dataSizeBytes); if (s1 < 0) return -1; off += 2
            outLen[0] = ((s0 and 0x7FFF) shl 16) or s1
            return off
        }
    }

    fun getCount(): Int = m_stringOffsets?.size ?: 0

    fun getString(index: Int): String? {
        if (index < 0 || m_stringOffsets == null || index >= m_stringOffsets.size) return null
        if (m_strings == null) return null
        val dataSizeBytes = m_strings.size * 4
        val offset = m_stringOffsets[index]
        if (offset < 0 || offset >= dataSizeBytes) return null
        return try {
            if (m_isUTF8) {
                val out = IntArray(1)
                var o1 = getVarint(m_strings, offset, dataSizeBytes, out)
                if (o1 < 0) return null
                var off = o1
                var o2 = getVarint(m_strings, off, dataSizeBytes, out)
                if (o2 < 0) return null
                off = o2
                val byteLen = out[0]
                if (byteLen < 0) return null
                if (off + byteLen > dataSizeBytes) return null
                val bytes = ByteArray(byteLen)
                for (i in 0 until byteLen) bytes[i] = getByte(m_strings, off + i).toByte()
                String(bytes, StandardCharsets.UTF_8)
            } else {
                val outLen = IntArray(1)
                var off = getShortLenUtf16(m_strings, offset, dataSizeBytes, outLen)
                if (off < 0) return null
                val length = outLen[0]
                if (length < 0) return null
                val needBytes = length * 2
                if (off + needBytes > dataSizeBytes) return null
                val result = StringBuilder(length)
                var p = off
                for (i in 0 until length) {
                    val ch = getShort(m_strings, p, dataSizeBytes)
                    if (ch < 0) return null
                    result.append(ch.toChar())
                    p += 2
                }
                result.toString()
            }
        } catch (t: Throwable) {
            null
        }
    }

    fun get(index: Int): CharSequence? = getString(index)

    fun getHTML(index: Int): String? {
        val raw = getString(index) ?: return null
        val style = getStyle(index) ?: return raw
        val html = StringBuilder(raw.length + 32)
        var offset = 0
        while (true) {
            var i = -1
            for (j in 0 until style.size step 3) {
                if (style[j + 1] == -1) continue
                if (i == -1 || style[i + 1] > style[j + 1]) i = j
            }
            val start = if (i != -1) style[i + 1] else raw.length
            for (j in 0 until style.size step 3) {
                val end = style[j + 2]
                if (end == -1 || end >= start) continue
                if (offset <= end) {
                    html.append(raw, offset, end + 1)
                    offset = end + 1
                }
                style[j + 2] = -1
                html.append('<'); html.append('/'); html.append(getString(style[j])); html.append('>')
            }
            if (offset < start) {
                html.append(raw, offset, start)
                offset = start
            }
            if (i == -1) break
            html.append('<'); html.append(getString(style[i])); html.append('>')
            style[i + 1] = -1
        }
        return html.toString()
    }

    fun find(string: String?): Int {
        if (string == null || m_stringOffsets == null) return -1
        for (i in m_stringOffsets.indices) {
            val s = getString(i) ?: continue
            if (string == s) return i
        }
        return -1
    }

    private fun getStyle(index: Int): IntArray? {
        if (m_styleOffsets == null || m_styles == null || index >= m_styleOffsets.size) return null
        val offset = m_styleOffsets[index] / 4
        var count = 0
        for (i in offset until m_styles.size) {
            if (m_styles[i] == -1) break
            count += 1
        }
        if (count == 0 || (count % 3) != 0) return null
        val style = IntArray(count)
        var i = offset
        var j = 0
        while (i < m_styles.size) {
            if (m_styles[i] == -1) break
            style[j++] = m_styles[i++]
        }
        return style
    }
}
