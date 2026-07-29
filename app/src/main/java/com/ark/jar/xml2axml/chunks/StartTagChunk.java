package com.ark.jar.xml2axml.chunks;

import com.ark.jar.xml2axml.IntWriter;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * Created by Roy on 15-10-5.
 */
public class StartTagChunk extends Chunk<StartTagChunk.H>{
    public class H extends Chunk.NodeHeader{

        public H() {
            super(ChunkType.XmlStartElement);
        }
    }

    public String name;
    public String prefix;
    public String namespace;
    public short attrStart=20;
    public short attrSize=20;
    public short idIndex=0;
    public short styleIndex=0;
    public short classIndex=0;
    public LinkedList<AttrChunk> attrs=new LinkedList<AttrChunk>();
    public List<StartNameSpaceChunk> startNameSpace=new Stack<StartNameSpaceChunk>();

    /*public StartTagChunk(Chunk parent,XmlPullParser p) throws XmlPullParserException {
        super(parent);
        name = p.getName();
        stringPool().addString(name);
        prefix = p.getPrefix();
        namespace = p.getNamespace();
        int ac = p.getAttributeCount();
        for (short i = 0; i < ac; ++i) {
            String prefix = p.getAttributePrefix(i);
            String namespace = p.getAttributeNamespace(i);
            String name = p.getAttributeName(i);
            String val = p.getAttributeValue(i);
            AttrChunk attr = new AttrChunk(this);
            attr.prefix = prefix;
            attr.namespace = namespace;
            attr.rawValue = val;
            attr.name = name;
            stringPool().addString(namespace,name);
            attrs.add(attr);
            if ("id".equals(name)&&"http://schemas.android.com/apk/res/android".equals(namespace)){
                idIndex=i;
            }else if (prefix==null&&"style".equals(name)){
                styleIndex=i;
            }else if (prefix==null&&"class".equals(name)){
                classIndex=i;
            }
        }
        int nsStart = p.getNamespaceCount(p.getDepth() - 1);
        int nsEnd = p.getNamespaceCount(p.getDepth());
        for (int i = nsStart; i < nsEnd; i++) {
            StartNameSpaceChunk snc=new StartNameSpaceChunk(parent);
            snc.prefix = p.getNamespacePrefix(i);
            stringPool().addString(null,snc.prefix);
            snc.uri = p.getNamespaceUri(i);
            stringPool().addString(null,snc.uri);
            startNameSpace.add(snc);
        }
    }*/
    //20260412修复编码时候axml_auto_00的问题
    public StartTagChunk(Chunk parent, XmlPullParser p) throws XmlPullParserException {
        super(parent);

        // ====== 标签名 ======
        name = p.getName();
        stringPool().addString(name);

        prefix = p.getPrefix();
        namespace = p.getNamespace();

        // 统一空字符串
        if (prefix != null && prefix.isEmpty()) prefix = null;
        if (namespace != null && namespace.isEmpty()) namespace = null;

        // ====== 属性处理 ======
        int ac = p.getAttributeCount();
        for (short i = 0; i < ac; ++i) {
            String attrPrefix = p.getAttributePrefix(i);
            String attrNamespace = p.getAttributeNamespace(i);
            String attrName = p.getAttributeName(i);
            String val = p.getAttributeValue(i);

            // 统一空字符串
            if (attrPrefix != null && attrPrefix.isEmpty()) attrPrefix = null;
            if (attrNamespace != null && attrNamespace.isEmpty()) attrNamespace = null;

            AttrChunk attr = new AttrChunk(this);
            attr.prefix = attrPrefix;
            attr.namespace = attrNamespace;
            attr.rawValue = val;
            attr.name = attrName;

            // 只在 namespace 合法时加入
            stringPool().addString(attrNamespace, attrName);

            attrs.add(attr);

            if ("id".equals(attrName) &&
                    "http://schemas.android.com/apk/res/android".equals(attrNamespace)) {
                idIndex = i;
            } else if (attrPrefix == null && "style".equals(attrName)) {
                styleIndex = i;
            } else if (attrPrefix == null && "class".equals(attrName)) {
                classIndex = i;
            }
        }

        // ====== 命名空间处理（核心修复点） ======
        int nsStart = p.getNamespaceCount(p.getDepth() - 1);
        int nsEnd = p.getNamespaceCount(p.getDepth());

        for (int i = nsStart; i < nsEnd; i++) {
            String nsPrefix = p.getNamespacePrefix(i);
            String nsUri = p.getNamespaceUri(i);

            // 统一空字符串
            if (nsPrefix != null && nsPrefix.isEmpty()) nsPrefix = null;
            if (nsUri != null && nsUri.isEmpty()) nsUri = null;

            // ❗关键：必须过滤非法 namespace（uri 不能为空）
            if (nsUri == null) {
                continue;
            }

            StartNameSpaceChunk snc = new StartNameSpaceChunk(parent);
            snc.prefix = nsPrefix;
            snc.uri = nsUri;

            // prefix 可以为空（默认命名空间），uri 必须存在
            if (nsPrefix != null) {
                stringPool().addString(null, nsPrefix);
            }
            stringPool().addString(null, nsUri);

            startNameSpace.add(snc);
        }
    }

    @Override
    public void preWrite() {
        for (AttrChunk a:attrs) a.calc();
        header.size=36+20*attrs.size();
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(stringIndex(null,namespace));
        w.write(stringIndex(null,name));
        w.write(attrStart);
        w.write(attrSize);
        w.write((short)attrs.size());
        w.write(idIndex);
        w.write(classIndex);
        w.write(styleIndex);
        for (AttrChunk a:attrs){
            a.write(w);
        }
    }
}
