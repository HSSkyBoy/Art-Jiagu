package com.ark.jar.xml2axml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class IntWriterTest {
    @Test
    fun writesLittleEndianInteger() {
        val output = ByteArrayOutputStream()
        val writer = IntWriter(output)
        writer.write(0x12345678)
        assertArrayEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), output.toByteArray())
        assertEquals(4, writer.pos)
    }

    @Test
    fun writesBigEndianInteger() {
        val output = ByteArrayOutputStream()
        val writer = IntWriter(output).apply { bigEndian = true }
        writer.write(0x12345678)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78), output.toByteArray())
        assertEquals(4, writer.pos)
    }

    @Test
    fun placeholderAdvancesPosition() {
        val output = ByteArrayOutputStream()
        val writer = IntWriter(output)
        writer.writePlaceHolder(7, "test")
        assertEquals(7, output.size())
        assertEquals(7, writer.pos)
    }
}
