package com.ark.jar.xml2axml.chunks;

import android.text.TextUtils;

import com.ark.jar.xml2axml.Encoder;
import com.ark.jar.xml2axml.IntWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Android 修复版 StringPoolChunk
 */
public class StringPoolChunk extends Chunk<StringPoolChunk.H> {

    public StringPoolChunk(Chunk parent) {
        super(parent);
    }

    public class H extends Chunk.Header {
        public int stringCount;
        public int styleCount;
        public int flags;
        public int stringPoolOffset;
        public int stylePoolOffset;

        public H() {
            super(ChunkType.StringPool);
        }

        @Override
        public void writeEx(IntWriter w) throws IOException {
            w.write(stringCount);
            w.write(styleCount);
            w.write(flags);
            w.write(stringPoolOffset);
            w.write(stylePoolOffset);
        }
    }

    public static class RawString {

        StringItem origin;
        char[] cdata;
        byte[] bdata;

        int length() {
            return cdata != null ? cdata.length : origin.string.length();
        }

        int padding() {
            if (cdata != null) {
                return (cdata.length * 2 + 4) & 2;
            } else {
                return 0;
            }
        }

        int size() {
            if (cdata != null) {
                return cdata.length * 2 + 4 + padding();
            } else {
                return bdata.length + 3 + padding();
            }
        }

        void write(IntWriter w) throws IOException {
            int pos = w.getPos();

            if (cdata != null) {
                w.write((short) length());
                for (char c : cdata) w.write(c);
                w.write((short) 0);
                if (padding() == 2) w.write((short) 0);
            } else {
                w.write((byte) length());
                w.write((byte) bdata.length);
                for (byte c : bdata) w.write(c);
                w.write((byte) 0);
                for (int i = 0; i < padding(); i++) w.write((byte) 0);
            }

            int written = w.getPos() - pos;
            if (written != size()) {
                throw new IllegalStateException(
                        "RawString 写入长度错误: " + written + " != " + size()
                );
            }
        }
    }

    public enum Encoding {UNICODE, UTF8}

    public int[] stringsOffset;
    public int[] stylesOffset;
    public ArrayList<RawString> rawStrings;

    public Encoding encoding = Encoder.Config.encoding;

    @Override
    public void preWrite() {
        rawStrings = new ArrayList<>();
        LinkedList<Integer> offsets = new LinkedList<>();
        int off = 0;

        if (encoding == Encoding.UNICODE) {
            for (LinkedList<StringItem> ss : map.values()) {
                for (StringItem s : ss) {
                    RawString r = new RawString();
                    r.cdata = s.string.toCharArray();
                    r.origin = s;
                    rawStrings.add(r);
                }
            }
        } else {
            for (LinkedList<StringItem> ss : map.values()) {
                for (StringItem s : ss) {
                    RawString r = new RawString();
                    try {
                        r.bdata = s.string.getBytes("UTF-8");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    r.origin = s;
                    rawStrings.add(r);
                }
            }
        }

        Collections.sort(rawStrings, new Comparator<RawString>() {
            @Override
            public int compare(RawString a, RawString b) {
                int l = a.origin.id == -1 ? Integer.MAX_VALUE : a.origin.id;
                int r = b.origin.id == -1 ? Integer.MAX_VALUE : b.origin.id;
                return l - r;
            }
        });

        for (RawString r : rawStrings) {
            offsets.add(off);
            off += r.size();
        }

        header.stringCount = rawStrings.size();
        header.styleCount = 0;
        header.size = off + header.headerSize
                + header.stringCount * 4
                + header.styleCount * 4;

        header.stringPoolOffset = offsets.size() * 4 + header.headerSize;
        header.stylePoolOffset = 0;

        stringsOffset = new int[offsets.size()];
        int i = 0;
        for (int x : offsets) stringsOffset[i++] = x;

        stylesOffset = new int[0];

        if (encoding == Encoding.UTF8) header.flags |= 0x100;
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        for (int i : stringsOffset) w.write(i);
        for (int i : stylesOffset) w.write(i);
        for (RawString r : rawStrings) r.write(w);
    }

    public class StringItem {
        public String namespace;
        public String string;
        public int id = -1;

        public StringItem(String s) {
            string = s;
        }

        public StringItem(String namespace, String s) {
            this.string = s;
            this.namespace = namespace;
            genId();
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
            genId();
        }

        public void genId() {
            if (namespace == null) return;

            String pkg;
            if ("http://schemas.android.com/apk/res-auto".equals(namespace)) {
                pkg = getContext().getPackageName();
            } else if (namespace.startsWith("http://schemas.android.com/apk/res/")) {
                pkg = namespace.substring(
                        "http://schemas.android.com/apk/res/".length()
                );
            } else {
                return;
            }

            id = getContext()
                    .getResources()
                    .getIdentifier(string, "attr", pkg);
        }
    }

    private HashMap<String, LinkedList<StringItem>> map = new HashMap<>();

    public void addString(String s) {
        if (s == null) return;
        LinkedList<StringItem> list = map.get(s);
        if (list == null) {
            list = new LinkedList<>();
            map.put(s, list);
        }
        if (list.isEmpty()) {
            list.add(new StringItem(s));
        }
    }

    public void addString(String namespace, String s) {
        if (s == null) return;

        LinkedList<StringItem> list = map.get(s);
        if (list == null) {
            list = new LinkedList<>();
            map.put(s, list);
        }

        for (StringItem e : list) {
            if (e.namespace == null || TextUtils.equals(e.namespace, namespace)) {
                e.setNamespace(namespace);
                return;
            }
        }
        list.add(new StringItem(namespace, s));
    }

    @Override
    public int stringIndex(String namespace, String s) {
        if (s == null) return -1;

        int l = rawStrings.size();
        for (int i = 0; i < l; ++i) {
            StringItem item = rawStrings.get(i).origin;
            if (s.equals(item.string)
                    && (TextUtils.isEmpty(namespace)
                    || TextUtils.equals(namespace, item.namespace))) {
                return i;
            }
        }

        if (TextUtils.isEmpty(s)) return -1;

        throw new RuntimeException("String 未找到: " + s);
    }
}
