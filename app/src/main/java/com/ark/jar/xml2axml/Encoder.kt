package com.ark.jar.xml2axml

import android.content.Context
import com.ark.jar.xml2axml.chunks.StringPoolChunk
import com.ark.jar.xml2axml.chunks.TagChunk
import com.ark.jar.xml2axml.chunks.XmlChunk
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.StringReader

class Encoder {

    object Config {
        var encoding: StringPoolChunk.Encoding = StringPoolChunk.Encoding.UNICODE
        var defaultReferenceRadix: Int = 16
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun encodeFile(context: Context, filename: String): ByteArray {
        requireNotNull(context) { "Context cannot be null" }

        val factory = XmlPullParserFactory.newInstance()
        factory.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)

        val parser = factory.newPullParser()
        parser.setInput(FileInputStream(filename), "UTF-8")

        return encode(context, parser)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun encodeString(context: Context, xml: String): ByteArray {
        requireNotNull(context) { "Context cannot be null" }

        val factory = XmlPullParserFactory.newInstance()
        factory.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)

        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        return encode(context, parser)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun encode(context: Context, parser: XmlPullParser): ByteArray {
        requireNotNull(context) { "Context cannot be null" }

        val chunk = XmlChunk(context)
        var current: TagChunk? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    current = TagChunk(current ?: chunk, parser)
                }
                XmlPullParser.END_TAG -> {
                    val parent = current!!.parent
                    current = (parent as? TagChunk) ?: null
                }
            }
            event = parser.next()
        }

        val os = ByteArrayOutputStream()
        val writer = IntWriter(os)
        chunk.write(writer)
        writer.close()

        return os.toByteArray()
    }
}
