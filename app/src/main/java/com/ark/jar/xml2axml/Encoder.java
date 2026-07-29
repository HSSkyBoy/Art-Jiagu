package com.ark.jar.xml2axml;

import android.content.Context;

import com.ark.jar.xml2axml.chunks.Chunk;
import com.ark.jar.xml2axml.chunks.StringPoolChunk;
import com.ark.jar.xml2axml.chunks.TagChunk;
import com.ark.jar.xml2axml.chunks.XmlChunk;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;

public class Encoder {

    public static class Config {
        public static StringPoolChunk.Encoding encoding =
                StringPoolChunk.Encoding.UNICODE;
        public static int defaultReferenceRadix = 16;
    }

    public byte[] encodeFile(Context context, String filename)
            throws XmlPullParserException, IOException {

        if (context == null) {
            throw new IllegalArgumentException("Context 不能为空");
        }

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new FileInputStream(filename), "UTF-8");

        return encode(context, parser);
    }

    public byte[] encodeString(Context context, String xml)
            throws XmlPullParserException, IOException {

        if (context == null) {
            throw new IllegalArgumentException("Context 不能为空");
        }

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xml));

        return encode(context, parser);
    }

    public byte[] encode(Context context, XmlPullParser parser)
            throws XmlPullParserException, IOException {

        if (context == null) {
            throw new IllegalArgumentException("Context 不能为空");
        }

        XmlChunk chunk = new XmlChunk(context);
        TagChunk current = null;

        for (int event = parser.getEventType();
             event != XmlPullParser.END_DOCUMENT;
             event = parser.next()) {

            switch (event) {
                case XmlPullParser.START_DOCUMENT:
                    break;

                case XmlPullParser.START_TAG:
                    current = new TagChunk(
                            current == null ? chunk : current,
                            parser
                    );
                    break;

                case XmlPullParser.END_TAG:
                    Chunk parent = current.getParent();
                    current = parent instanceof TagChunk
                            ? (TagChunk) parent
                            : null;
                    break;

                case XmlPullParser.TEXT:
                    break;

                default:
                    break;
            }
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IntWriter writer = new IntWriter(os);
        chunk.write(writer);
        writer.close();

        return os.toByteArray();
    }
}
