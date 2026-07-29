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
package com.ark.jar.android.content.res;

import com.ark.jar.android.content.res.ChunkUtil;

import java.io.IOException;

/**
 * @author Dmitry Skiba
 * 
 * Block of strings, used in binary xml and arsc.
 * 
 * TODO:
 * - implement get()
 *
 */
public class StringBlock {
	
	/**
	 * Reads whole (including chunk type) string block from stream.
	 * Stream must be at the chunk type.
	 */
	/*public static StringBlock read(IntReader reader) throws IOException {
		ChunkUtil.readCheckType(reader,CHUNK_TYPE);
		int chunkSize=reader.readInt();
		int stringCount=reader.readInt();
		int styleOffsetCount=reader.readInt();
		*//*?*//*reader.readInt();
		int stringsOffset=reader.readInt();
		int stylesOffset=reader.readInt();
		
		StringBlock block=new StringBlock();
		block.m_stringOffsets=reader.readIntArray(stringCount);
		if (styleOffsetCount!=0) {
			block.m_styleOffsets=reader.readIntArray(styleOffsetCount);
		}
		{
			int size=((stylesOffset==0)?chunkSize:stylesOffset)-stringsOffset;
			if ((size%4)!=0) {
				throw new IOException("String data size is not multiple of 4 ("+size+").");
			}
			block.m_strings=reader.readIntArray(size/4);
		}
		if (stylesOffset!=0) {
			int size=(chunkSize-stylesOffset);
			if ((size%4)!=0) {
				throw new IOException("Style data size is not multiple of 4 ("+size+").");
			}
			block.m_styles=reader.readIntArray(size/4);
		}

		return block;	
	}*/
	public static StringBlock read(IntReader reader) throws IOException {
		ChunkUtil.readCheckType(reader, CHUNK_TYPE);
		int chunkSize = reader.readInt();
		int stringCount = reader.readInt();
		int styleOffsetCount = reader.readInt();
		int flags = reader.readInt(); // 原来是 /*?*/reader.readInt(); 这里其实是 flags
		int stringsOffset = reader.readInt();
		int stylesOffset = reader.readInt();

		StringBlock block = new StringBlock();
		block.m_isUTF8 = (flags & UTF8_FLAG) != 0;

		block.m_stringOffsets = reader.readIntArray(stringCount);
		if (styleOffsetCount != 0) {
			block.m_styleOffsets = reader.readIntArray(styleOffsetCount);
		}
		{
			int size = ((stylesOffset == 0) ? chunkSize : stylesOffset) - stringsOffset;
			if ((size % 4) != 0) {
				throw new IOException("String data size is not multiple of 4 (" + size + ").");
			}
			block.m_strings = reader.readIntArray(size / 4);
		}
		if (stylesOffset != 0) {
			int size = (chunkSize - stylesOffset);
			if ((size % 4) != 0) {
				throw new IOException("Style data size is not multiple of 4 (" + size + ").");
			}
			block.m_styles = reader.readIntArray(size / 4);
		}

		return block;
	}


	/**
	 * Returns number of strings in block. 
	 */
	public int getCount() {
		return m_stringOffsets!=null?
			m_stringOffsets.length:
			0;
	}
	
	/**
	 * Returns raw string (without any styling information) at specified index.
	 */
	/*public String getString(int index) {
		if (index<0 ||
			m_stringOffsets==null ||
			index>=m_stringOffsets.length)
		{
			return null;
		}
		int offset=m_stringOffsets[index];
		int length=getShort(m_strings,offset);
		StringBuilder result=new StringBuilder(length);
		for (;length!=0;length-=1) {
			offset+=2;
			result.append((char)getShort(m_strings,offset));
		}
		return result.toString();
	}*/
	public String getString(int index) {
		if (index < 0 || m_stringOffsets == null || index >= m_stringOffsets.length) {
			return null;
		}
		if (m_strings == null) {
			return null;
		}

		final int dataSizeBytes = m_strings.length * 4;
		int offset = m_stringOffsets[index];

		// 基础边界：偏移必须落在字符串数据区内
		if (offset < 0 || offset >= dataSizeBytes) {
			return null;
		}

		try {
			if (m_isUTF8) {
				// ---- UTF-8 字符串池 ----
				// 结构：[utf16Len(varint)][utf8ByteLen(varint)][bytes...][0]
				int[] out = new int[1];

				// utf16Len：用于一些场景，这里读取并跳过即可
				int o1 = getVarint(m_strings, offset, dataSizeBytes, out);
				if (o1 < 0) return null;
				int off = o1;

				// utf8ByteLen：真正的字节长度
				int o2 = getVarint(m_strings, off, dataSizeBytes, out);
				if (o2 < 0) return null;
				off = o2;
				int byteLen = out[0];

				// byteLen 可能为 0，也要允许
				if (byteLen < 0) return null;
				if (off + byteLen > dataSizeBytes) return null;

				byte[] bytes = new byte[byteLen];
				for (int i = 0; i < byteLen; i++) {
					bytes[i] = (byte) getByte(m_strings, off + i);
				}

				// 这里使用 UTF-8 解码
				return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
			} else {
				// ---- UTF-16 字符串池 ----
				// 结构：[utf16CharLen(u16 或扩展)][utf16Chars...][0x0000]
				int[] outLen = new int[1];
				int off = getShortLenUtf16(m_strings, offset, dataSizeBytes, outLen);
				if (off < 0) return null;

				int length = outLen[0];
				if (length < 0) return null;

				// 每个 UTF-16 字符 2 字节
				int needBytes = length * 2;
				if (off + needBytes > dataSizeBytes) return null;

				StringBuilder result = new StringBuilder(length);
				int p = off;
				for (int i = 0; i < length; i++) {
					int ch = getShort(m_strings, p, dataSizeBytes);
					if (ch < 0) return null;
					result.append((char) ch);
					p += 2;
				}
				return result.toString();
			}
		} catch (Throwable t) {
			// 防御：任何异常都不让它炸进程，返回 null 由上层决定怎么处理
			return null;
		}
	}


	/**
	 * Not yet implemented. 
	 * 
	 * Returns string with style information (if any).
	 */
	public CharSequence get(int index) {
		return getString(index);
	}

	/**
	 * Returns string with style tags (html-like). 
	 */
	public String getHTML(int index) {
		String raw=getString(index);
		if (raw==null) {
			return raw;
		}
		int[] style=getStyle(index);
		if (style==null) {
			return raw;
		}
		StringBuilder html=new StringBuilder(raw.length()+32);
		int offset=0;
		while (true) {
			int i=-1;
			for (int j=0;j!=style.length;j+=3) {
				if (style[j+1]==-1) {
					continue;
				}
				if (i==-1 || style[i+1]>style[j+1]) {
					i=j;
				}
			}
			int start=((i!=-1)?style[i+1]:raw.length());
			for (int j=0;j!=style.length;j+=3) {
				int end=style[j+2];
				if (end==-1 || end>=start) {
					continue;
				}
				if (offset<=end) {
					html.append(raw,offset,end+1);
					offset=end+1;
				}
				style[j+2]=-1;
				html.append('<');
				html.append('/');
				html.append(getString(style[j]));
				html.append('>');
			}
			if (offset<start) {
				html.append(raw,offset,start);
				offset=start;
			}
			if (i==-1) {
				break;
			}
			html.append('<');
			html.append(getString(style[i]));
			html.append('>');
			style[i+1]=-1;
		}
		return html.toString();
	}
	
	/**
	 * Finds index of the string.
	 * Returns -1 if the string was not found.
	 */
	/*public int find(String string) {
		if (string==null) {
			return -1;
		}
		for (int i=0;i!=m_stringOffsets.length;++i) {
			int offset=m_stringOffsets[i];
			int length=getShort(m_strings,offset);
			if (length!=string.length()) {
				continue;
			}
			int j=0;
			for (;j!=length;++j) {
				offset+=2;
				if (string.charAt(j)!=getShort(m_strings,offset)) {
					break;
				}
			}
			if (j==length) {
				return i;
			}
		}
		return -1;
	}*/
	public int find(String string) {
		if (string == null || m_stringOffsets == null) {
			return -1;
		}
		for (int i = 0; i < m_stringOffsets.length; i++) {
			String s = getString(i); // 统一走 getString（支持 UTF-8 / UTF-16）
			if (s == null) {
				continue;
			}
			if (string.equals(s)) {
				return i;
			}
		}
		return -1;
	}


	///////////////////////////////////////////// implementation

	private StringBlock() {
	}
	
	/**
	 * Returns style information - array of int triplets,
	 * where in each triplet:
	 * 	* first int is index of tag name ('b','i', etc.)
	 * 	* second int is tag start index in string
	 * 	* third int is tag end index in string
	 */
	private int[] getStyle(int index) {
		if (m_styleOffsets==null || m_styles==null ||
			index>=m_styleOffsets.length)
		{
			return null;
		}
		int offset=m_styleOffsets[index]/4;
		int style[];
		{
			int count=0;
			for (int i=offset;i<m_styles.length;++i) {
				if (m_styles[i]==-1) {
					break;
				}
				count+=1;
			}
			if (count==0 || (count%3)!=0) {
				return null;
			}
			style=new int[count];
		}
		for (int i=offset,j=0;i<m_styles.length;) {
			if (m_styles[i]==-1) {
				break;
			}
			style[j++]=m_styles[i++];
		}
		return style;
	}
	
	/*private static final int getShort(int[] array,int offset) {
		int value=array[offset/4];
		if ((offset%4)/2==0) {
			return (value & 0xFFFF);
		} else {
			return (value >>> 16);
		}
	}*/
	private static int getShort(int[] array, int offset, int dataSizeBytes) {
		if (array == null) return -1;
		if (offset < 0 || offset + 1 >= dataSizeBytes) return -1; // 读 2 字节，必须至少剩 2 字节
		int index = offset / 4;
		if (index < 0 || index >= array.length) return -1;

		int value = array[index];
		if (((offset % 4) / 2) == 0) {
			return (value & 0xFFFF);
		} else {
			return (value >>> 16) & 0xFFFF;
		}
	}


	private int[] m_stringOffsets;
	private int[] m_strings;
	private int[] m_styleOffsets;
	private int[] m_styles;

	private static final int CHUNK_TYPE=0x001C0001;

	// flags 的 UTF-8 标志（AOSP ResStringPool 约定）
	private static final int UTF8_FLAG = 0x00000100;

	// 字符串池是否为 UTF-8
	private boolean m_isUTF8 = false;

	/**
	 * 读取单个字节（offset 为字节偏移）
	 */
	private static int getByte(int[] array, int offset) {
		int value = array[offset / 4];
		int shift = (offset % 4) * 8;
		return (value >>> shift) & 0xFF;
	}

	/**
	 * 读取 AXML UTF-8 用的变长长度（1 或 2 字节）
	 * 返回：读取完成后的新 offset；失败返回 -1
	 * out[0]：解析出的长度
	 */
	private static int getVarint(int[] array, int offset, int dataSizeBytes, int[] out) {
		if (offset < 0 || offset >= dataSizeBytes) return -1;

		int b0 = getByte(array, offset);
		offset += 1;

		// 最高位 0：单字节
		if ((b0 & 0x80) == 0) {
			out[0] = b0;
			return offset;
		}

		// 最高位 1：两字节
		if (offset >= dataSizeBytes) return -1;
		int b1 = getByte(array, offset);
		offset += 1;

		out[0] = ((b0 & 0x7F) << 8) | b1;
		return offset;
	}

	/**
	 * 读取 UTF-16 字符串长度（2 或 4 字节）
	 * 返回：读取完成后的新 offset（指向字符串内容起始）；失败返回 -1
	 * outLen[0]：字符数
	 */
	private static int getShortLenUtf16(int[] array, int offset, int dataSizeBytes, int[] outLen) {
		int s0 = getShort(array, offset, dataSizeBytes);
		if (s0 < 0) return -1;
		offset += 2;

		// 高位未置：长度就是 s0
		if ((s0 & 0x8000) == 0) {
			outLen[0] = s0;
			return offset;
		}

		// 高位置位：长度占 4 字节
		int s1 = getShort(array, offset, dataSizeBytes);
		if (s1 < 0) return -1;
		offset += 2;

		outLen[0] = ((s0 & 0x7FFF) << 16) | s1;
		return offset;
	}

}
