package com.ark.jar.xml2axml.chunks;

import android.content.Context;

import com.ark.jar.xml2axml.IntWriter;
import com.ark.jar.xml2axml.ReferenceResolver;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Android 修复版 Chunk
 */
public abstract class Chunk<H extends Chunk.Header> {

    public abstract class Header {
        public short type;
        public short headerSize;
        public int size;

        public Header(ChunkType ct) {
            type = ct.type;
            headerSize = ct.headerSize;
        }

        public void write(IntWriter w) throws IOException {
            w.write(type);
            w.write(headerSize);
            w.write(size);
            writeEx(w);
        }

        public abstract void writeEx(IntWriter w) throws IOException;
    }

    public abstract class NodeHeader extends Header {

        public int lineNo = 1;
        public int comment = -1;

        public NodeHeader(ChunkType ct) {
            super(ct);
            headerSize = 0x10;
        }

        @Override
        public void write(IntWriter w) throws IOException {
            w.write(type);
            w.write(headerSize);
            w.write(size);
            w.write(lineNo);
            w.write(comment);
            writeEx(w);
        }

        @Override
        public void writeEx(IntWriter w) throws IOException {
            // 默认无扩展
        }
    }

    public class EmptyHeader extends Header {
        public EmptyHeader() {
            super(ChunkType.Null);
        }

        @Override
        public void writeEx(IntWriter w) throws IOException {}

        @Override
        public void write(IntWriter w) throws IOException {}
    }

    protected Context context;
    private Chunk parent;
    public H header;

    @SuppressWarnings("unchecked")
    public Chunk(Chunk parent) {
        this.parent = parent;

        try {
            Type superType = getClass().getGenericSuperclass();
            if (!(superType instanceof ParameterizedType)) {
                throw new IllegalStateException(
                        "Chunk 子类必须保留泛型 Header 声明: " + getClass().getName()
                );
            }

            Type headerType =
                    ((ParameterizedType) superType).getActualTypeArguments()[0];

            Class<H> headerClass = (Class<H>) headerType;

            Constructor<?>[] constructors = headerClass.getConstructors();
            for (Constructor<?> c : constructors) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 1 && Chunk.class.isAssignableFrom(params[0])) {
                    header = (H) c.newInstance(this);
                    return;
                }
            }

            throw new IllegalStateException(
                    "未找到 Header(Chunk) 构造方法: " + headerClass.getName()
            );

        } catch (Exception e) {
            throw new RuntimeException("初始化 Chunk Header 失败", e);
        }
    }

    public void write(IntWriter w) throws IOException {
        int pos = w.getPos();
        calc();
        header.write(w);
        writeEx(w);

        int written = w.getPos() - pos;
        if (written != header.size) {
            throw new IllegalStateException(
                    "Chunk 写入大小错误: 实际 "
                            + written + " 期望 "
                            + header.size + " 类型 "
                            + getClass().getName()
            );
        }
    }

    public Chunk getParent() {
        return parent;
    }

    public Context getContext() {
        if (context != null) return context;
        if (parent == null)
            throw new IllegalStateException("Root Chunk 未设置 Context");
        return parent.getContext();
    }

    private boolean isCalculated = false;

    public int calc() {
        if (!isCalculated) {
            preWrite();
            isCalculated = true;
        }
        return header.size;
    }

    private XmlChunk root;

    public XmlChunk root() {
        if (root != null) return root;
        if (parent == null)
            throw new IllegalStateException("找不到 XmlChunk Root");
        return parent.root();
    }

    public int stringIndex(String namespace, String s) {
        return stringPool().stringIndex(namespace, s);
    }

    private StringPoolChunk stringPool;

    public StringPoolChunk stringPool() {
        return root().stringPool;
    }

    public ReferenceResolver getReferenceResolver() {
        return root().getReferenceResolver();
    }

    public void preWrite() {}

    public abstract void writeEx(IntWriter w) throws IOException;
}
