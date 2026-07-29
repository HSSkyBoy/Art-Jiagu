package com.ark.jar.xml2axml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class IntWriterTest {
    @Test
    public void writesLittleEndianInteger() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IntWriter writer = new IntWriter(output);
        writer.write(0x12345678);
        assertArrayEquals(new byte[]{0x78, 0x56, 0x34, 0x12}, output.toByteArray());
        assertEquals(4, writer.getPos());
    }

    @Test
    public void writesBigEndianInteger() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IntWriter writer = new IntWriter(output);
        writer.bigEndian = true;
        writer.write(0x12345678);
        assertArrayEquals(new byte[]{0x12, 0x34, 0x56, 0x78}, output.toByteArray());
        assertEquals(4, writer.getPos());
    }

    @Test
    public void placeholderAdvancesPosition() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IntWriter writer = new IntWriter(output);
        writer.writePlaceHolder(7, "test");
        assertEquals(7, output.size());
        assertEquals(7, writer.getPos());
    }
}
