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
package com.ark.jar.xml2axml.test

import com.ark.jar.android.content.res.AXmlResourceParser
import android.util.TypedValue
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream

/**
 * @author Dmitry Skiba
 *
 * This is example usage of AXMLParser class.
 *
 * Prints xml document from Android's binary xml file.
 */
class AXMLPrinter {
    companion object {
        @JvmStatic
        var out: PrintStream = System.out

        @JvmStatic
        fun main(arguments: Array<String>) {
            if (arguments.size < 1) {
                log("Usage: AXMLPrinter <binary xml file>")
                return
            }
            try {
                FileInputStream(arguments[0]).use { inf -> decode(inf) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        @Throws(XmlPullParserException::class, IOException::class)
        fun decode(inf: InputStream) {
            val parser = AXmlResourceParser()
            parser.open(inf)
            val indent = StringBuilder(10)
            val indentStep = "\t"
            while (true) {
                val type = parser.next()
                if (type == XmlPullParser.END_DOCUMENT) {
                    break
                }
                when (type) {
                    XmlPullParser.START_DOCUMENT -> {
                        log("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                    }
                    XmlPullParser.START_TAG -> {
                        log("%s<%s%s", indent, getNamespacePrefix(parser.prefix), parser.name)
                        indent.append(indentStep)
                        val namespaceCountBefore = parser.getNamespaceCount(parser.depth - 1)
                        val namespaceCount = parser.getNamespaceCount(parser.depth)
                        for (i in namespaceCountBefore until namespaceCount) {
                            log(
                                "%sxmlns:%s=\"%s\"",
                                indent,
                                parser.getNamespacePrefix(i),
                                parser.getNamespaceUri(i)
                            )
                        }
                        for (i in 0 until parser.attributeCount) {
                            log(
                                "%s%s%s=\"%s\"", indent,
                                getNamespacePrefix(parser.getAttributePrefix(i)),
                                parser.getAttributeName(i),
                                escapeXml(getAttributeValue(parser, i))
                            )
                        }
                        log("%s>", indent)
                    }
                    XmlPullParser.END_TAG -> {
                        indent.setLength(indent.length - indentStep.length)
                        log(
                            "%s</%s%s>", indent,
                            getNamespacePrefix(parser.prefix),
                            parser.name
                        )
                    }
                    XmlPullParser.TEXT -> {
                        log("%s%s", indent, parser.text)
                    }
                }
            }
        }

        // XML escapping, prevent values like "google" from breaking structure
        private fun escapeXml(value: String?): String {
            if (value == null) return ""
            return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }

        private fun getNamespacePrefix(prefix: String?): String {
            if (prefix == null || prefix.isEmpty()) {
                return ""
            }
            return "$prefix:"
        }

        private fun getAttributeValue(parser: AXmlResourceParser, index: Int): String {
            val type = parser.getAttributeValueType(index)
            val data = parser.getAttributeValueData(index)
            if (type == TypedValue.TYPE_STRING) {
                return parser.getAttributeValue(index)
            }
            if (type == TypedValue.TYPE_ATTRIBUTE) {
                return "?%s%08X".format(getPackage(data), data)
            }
            if (type == TypedValue.TYPE_REFERENCE) {
                return "@%s%08X".format(getPackage(data), data)
            }
            if (type == TypedValue.TYPE_FLOAT) {
                return Float.intBitsToFloat(data).toString()
            }
            if (type == TypedValue.TYPE_INT_HEX) {
                return "0x%08X".format(data)
            }
            if (type == TypedValue.TYPE_INT_BOOLEAN) {
                return if (data != 0) "true" else "false"
            }
            if (type == TypedValue.TYPE_DIMENSION) {
                return complexToFloat(data).toString() + DIMENSION_UNITS[data and TypedValue.COMPLEX_UNIT_MASK]
            }
            if (type == TypedValue.TYPE_FRACTION) {
                return complexToFloat(data).toString() + FRACTION_UNITS[data and TypedValue.COMPLEX_UNIT_MASK]
            }
            if (type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return "#%08X".format(data)
            }
            if (type >= TypedValue.TYPE_FIRST_INT && type <= TypedValue.TYPE_LAST_INT) {
                return data.toString()
            }
            return "<0x%X, type 0x%02X>".format(data, type)
        }

        private fun getPackage(id: Int): String {
            return if (id ushr 24 == 1) "android:" else ""
        }

        private fun log(format: String, vararg arguments: Any?) {
            out.printf(format, *arguments)
            out.println()
        }

        /////////////////////////////////// ILLEGAL STUFF, DONT LOOK :)

        @JvmStatic
        fun complexToFloat(complex: Int): Float {
            return (complex and 0xFFFFFF00) * RADIX_MULTS[(complex shr 4) and 3]
        }

        private val RADIX_MULTS = floatArrayOf(0.00390625F, 3.051758E-005F, 1.192093E-007F, 4.656613E-010F)
        private val DIMENSION_UNITS = arrayOf("px", "dip", "sp", "pt", "in", "mm", "", "")
        private val FRACTION_UNITS = arrayOf("%", "%p", "", "", "", "", "", "")
    }
}
