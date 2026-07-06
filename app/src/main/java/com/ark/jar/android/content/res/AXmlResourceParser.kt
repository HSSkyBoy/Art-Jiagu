package com.ark.jar.android.content.res

import com.ark.jar.android.content.res.ChunkUtil
import com.ark.jar.android.content.res.IntReader
import com.ark.jar.android.content.res.StringBlock
import android.content.res.XmlResourceParser
import android.util.TypedValue
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.Reader

class AXmlResourceParser : XmlResourceParser {
    constructor() {
        resetEventInfo()
    }

    fun open(stream: InputStream?) {
        close()
        if (stream != null) {
            m_reader = IntReader(stream, false)
        }
    }

    override fun close() {
        if (!m_operational) return
        m_operational = false
        m_reader!!.close()
        m_reader = null
        m_strings = null
        m_resourceIDs = null
        m_namespaces.reset()
        resetEventInfo()
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun next(): Int {
        if (m_reader == null) throw XmlPullParserException("Parser is not opened.", this, null)
        return try {
            doNext()
            m_event
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextToken(): Int {
        return next()
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextTag(): Int {
        var eventType = next()
        if (eventType == TEXT && isWhitespace()) eventType = next()
        if (eventType != START_TAG && eventType != END_TAG) throw XmlPullParserException("Expected start or end tag.", this, null)
        return eventType
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextText(): String {
        if (getEventType() != START_TAG) throw XmlPullParserException("Parser must be on START_TAG to read next text.", this, null)
        var eventType = next()
        if (eventType == TEXT) {
            val result = getText()
            eventType = next()
            if (eventType != END_TAG) throw XmlPullParserException("Event TEXT must be immediately followed by END_TAG.", this, null)
            return result
        } else if (eventType == END_TAG) return ""
        else throw XmlPullParserException("Parser must be on START_TAG or TEXT to read text.", this, null)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun require(type: Int, namespace: String?, name: String?) {
        if (type != getEventType() || (namespace != null && namespace != getNamespace()) || (name != null && name != getName()))
            throw XmlPullParserException(TYPES[type] + " is expected.", this, null)
    }

    override fun getDepth(): Int {
        return m_namespaces.getDepth() - 1
    }

    @Throws(XmlPullParserException::class)
    override fun getEventType(): Int {
        return m_event
    }

    override fun getLineNumber(): Int {
        return m_lineNumber
    }

    override fun getName(): String? {
        if (m_name == -1 || (m_event != START_TAG && m_event != END_TAG)) return null
        return m_strings!!.getString(m_name)
    }

    override fun getText(): String? {
        if (m_name == -1 || m_event != TEXT) return null
        return m_strings!!.getString(m_name)
    }

    override fun getTextCharacters(holderForStartAndLength: IntArray): CharArray? {
        val text = getText()
        if (text == null) return null
        holderForStartAndLength[0] = 0
        holderForStartAndLength[1] = text.length
        return text.toCharArray()
    }

    override fun getNamespace(): String? {
        return m_strings!!.getString(m_namespaceUri)
    }

    override fun getPrefix(): String? {
        val prefix = m_namespaces.findPrefix(m_namespaceUri)
        return m_strings!!.getString(prefix)
    }

    override fun getPositionDescription(): String {
        return "XML line #" + getLineNumber()
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespaceCount(depth: Int): Int {
        return m_namespaces.getAccumulatedCount(depth)
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespacePrefix(pos: Int): String? {
        val prefix = m_namespaces.getPrefix(pos)
        return m_strings!!.getString(prefix)
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespaceUri(pos: Int): String? {
        val uri = m_namespaces.getUri(pos)
        return m_strings!!.getString(uri)
    }

    override fun getClassAttribute(): String? {
        if (m_classAttribute == -1) return null
        val offset = getAttributeOffset(m_classAttribute)
        val value = m_attributes!![offset + ATTRIBUTE_IX_VALUE_STRING]
        return m_strings!!.getString(value)
    }

    override fun getIdAttribute(): String? {
        if (m_idAttribute == -1) return null
        val offset = getAttributeOffset(m_idAttribute)
        val value = m_attributes!![offset + ATTRIBUTE_IX_VALUE_STRING]
        return m_strings!!.getString(value)
    }

    override fun getIdAttributeResourceValue(defaultValue: Int): Int {
        if (m_idAttribute == -1) return defaultValue
        val offset = getAttributeOffset(m_idAttribute)
        val valueType = m_attributes!![offset + ATTRIBUTE_IX_VALUE_TYPE]
        if (valueType != TypedValue.TYPE_REFERENCE) return defaultValue
        return m_attributes!![offset + ATTRIBUTE_IX_VALUE_DATA]
    }

    override fun getStyleAttribute(): Int {
        if (m_styleAttribute == -1) return 0
        val offset = getAttributeOffset(m_styleAttribute)
        return m_attributes!![offset + ATTRIBUTE_IX_VALUE_DATA]
    }

    // IMPORTANT: Use "override val attributeCount: Int" since AttributeSet now defines it as a property
    override val attributeCount: Int
        get() {
            if (m_event != START_TAG) return -1
            return m_attributes!!.size / ATTRIBUTE_LENGHT
        }

    override fun getAttributeNamespace(index: Int): String? {
        val offset = getAttributeOffset(index)
        val namespace = m_attributes!![offset + ATTRIBUTE_IX_NAMESPACE_URI]
        if (namespace == -1) return ""
        return m_strings!!.getString(namespace)
    }

    override fun getAttributePrefix(index: Int): String? {
        val offset = getAttributeOffset(index)
        val uri = m_attributes!![offset + ATTRIBUTE_IX_NAMESPACE_URI]
        val prefix = m_namespaces.findPrefix(uri)
        if (prefix == -1) return ""
        return m_strings!!.getString(prefix)
    }

    override fun getAttributeName(index: Int): String? {
        val offset = getAttributeOffset(index)
        val name = m_attributes!![offset + ATTRIBUTE_IX_NAME]
        if (name == -1) return ""
        return m_strings!!.getString(name)
    }

    override fun getAttributeNameResource(index: Int): Int {
        val offset = getAttributeOffset(index)
        val name = m_attributes!![offset + ATTRIBUTE_IX_NAME]
        if (m_resourceIDs == null || name < 0 || name >= m_resourceIDs!!.size) return 0
        return m_resourceIDs!![name]
    }

    override fun getAttributeValueType(index: Int): Int {
        val offset = getAttributeOffset(index)
        return m_attributes!![offset + ATTRIBUTE_IX_VALUE_TYPE]
    }

    override fun getAttributeValueData(index: Int): Int {
        val offset = getAttributeOffset(index)
        return m_attributes!![offset + ATTRIBUTE_IX_VALUE_DATA]
    }

    override fun getAttributeValue(index: Int): String? {
        val offset = getAttributeOffset(index)
        val valueType = m_attributes!![offset + ATTRIBUTE_IX_VALUE_TYPE]
        if (valueType == TypedValue.TYPE_STRING) {
            val valueString = m_attributes!![offset + ATTRIBUTE_IX_VALUE_STRING]
            val value = m_strings!!.getString(valueString)
            if (value != null) return value
        }
        val valueData = m_attributes!![offset + ATTRIBUTE_IX_VALUE_DATA]
        return ""
    }

    override fun getAttributeBooleanValue(index: Int, defaultValue: Boolean): Boolean {
        return getAttributeIntValue(index, if (defaultValue) 1 else 0) != 0
    }

    override fun getAttributeFloatValue(index: Int, defaultValue: Float): Float {
        val offset = getAttributeOffset(index)
        val valueType = m_attributes!![offset + ATTRIBUTE_IX_VALUE_TYPE]
        if (valueType == TypedValue.TYPE_FLOAT) {
            val valueData = m_attributes!![offset + ATTRIBUTE_IX_VALUE_DATA]
            return Float.fromBits(valueData)
        }
        return defaultValue
    }

    override fun getAttributeIntValue(index: Int, defaultValue: Int): Int {
        val offset = getAttributeOffset(index)
        val valueType = m_attributes!![offset + ATTRIBUTE_IX_VALUE_TYPE]
        if (valueType >= TypedValue.TYPE_FIRST_INT && valueType <= TypedValue.TYPE_LAST_INT) return m_attributes!![offset + ATTRIBUTE_IX_VALUE_DATA]
        return defaultValue
    }

    override fun getAttributeUnsignedIntValue(index: Int, defaultValue: Int): Int {
        return getAttributeIntValue(index, defaultValue)
    }

    override fun getAttributeResourceValue(index: Int, defaultValue: Int): Int {
        val offset = getAttributeOffset(index)
        val valueType = m_attributes!![offset + ATTRIBUTE_IX_VALUE_TYPE]
        if (valueType == TypedValue.TYPE_REFERENCE) return m_attributes!![offset + ATTRIBUTE_IX_VALUE_DATA]
        return defaultValue
    }

    override fun getAttributeValue(namespace: String?, attribute: String?): String? {
        val index = findAttribute(namespace, attribute)
        if (index == -1) return null
        return getAttributeValue(index)
    }

    override fun getAttributeBooleanValue(namespace: String?, attribute: String?, defaultValue: Boolean): Boolean {
        val index = findAttribute(namespace, attribute)
        if (index == -1) return defaultValue
        return getAttributeBooleanValue(index, defaultValue)
    }

    override fun getAttributeFloatValue(namespace: String?, attribute: String?, defaultValue: Float): Float {
        val index = findAttribute(namespace, attribute)
        if (index == -1) return defaultValue
        return getAttributeFloatValue(index, defaultValue)
    }

    override fun getAttributeIntValue(namespace: String?, attribute: String?, defaultValue: Int): Int {
        val index = findAttribute(namespace, attribute)
        if (index == -1) return defaultValue
        return getAttributeIntValue(index, defaultValue)
    }

    override fun getAttributeUnsignedIntValue(namespace: String?, attribute: String?, defaultValue: Int): Int {
        val index = findAttribute(namespace, attribute)
        if (index == -1) return defaultValue
        return getAttributeUnsignedIntValue(index, defaultValue)
    }

    override fun getAttributeResourceValue(namespace: String?, attribute: String?, defaultValue: Int): Int {
        val index = findAttribute(namespace, attribute)
        if (index == -1) return defaultValue
        return getAttributeResourceValue(index, defaultValue)
    }

    override fun getAttributeListValue(index: Int, options: Array<String?>, defaultValue: Int): Int {
        return 0
    }

    override fun getAttributeListValue(namespace: String?, attribute: String?, options: Array<String?>, defaultValue: Int): Int {
        return 0
    }

    override fun getAttributeType(index: Int): String {
        return "CDATA"
    }

    override fun isAttributeDefault(index: Int): Boolean {
        return false
    }

    @Throws(XmlPullParserException::class)
    override fun setInput(stream: InputStream?, inputEncoding: String?) {
        throw XmlPullParserException(E_NOT_SUPPORTED)
    }

    @Throws(XmlPullParserException::class)
    override fun setInput(reader: Reader?) {
        throw XmlPullParserException(E_NOT_SUPPORTED)
    }

    override fun getInputEncoding(): String? {
        return null
    }

    override fun getColumnNumber(): Int {
        return -1
    }

    @Throws(XmlPullParserException::class)
    override fun isEmptyElementTag(): Boolean {
        return false
    }

    @Throws(XmlPullParserException::class)
    override fun isWhitespace(): Boolean {
        return false
    }

    @Throws(XmlPullParserException::class)
    override fun defineEntityReplacementText(entityName: String?, replacementText: String?) {
        throw XmlPullParserException(E_NOT_SUPPORTED)
    }

    override fun getNamespace(prefix: String?): String? {
        throw RuntimeException(E_NOT_SUPPORTED)
    }

    override fun getProperty(name: String?): Any? {
        return null
    }

    @Throws(XmlPullParserException::class)
    override fun setProperty(name: String?, value: Any?) {
        throw XmlPullParserException(E_NOT_SUPPORTED)
    }

    override fun getFeature(name: String?): Boolean {
        return false
    }

    @Throws(XmlPullParserException::class)
    override fun setFeature(name: String?, value: Boolean) {
        throw XmlPullParserException(E_NOT_SUPPORTED)
    }

    // ---- The rest of the class: NamespaceStack inner class, data fields, constants ----
    // Keep these exactly as-is in the conversion

    private class NamespaceStack {
        constructor() {
            m_data = IntArray(32)
        }

        fun reset() {
            m_dataLength = 0
            m_count = 0
            m_depth = 0
        }

        fun getTotalCount(): Int {
            return m_count
        }

        fun getCurrentCount(): Int {
            if (m_dataLength == 0) return 0
            val offset = m_dataLength - 1
            return m_data[offset]
        }

        fun getAccumulatedCount(depth: Int): Int {
            if (m_dataLength == 0 || depth < 0) return 0
            var depthVar = depth
            if (depthVar > m_depth) depthVar = m_depth
            var accumulatedCount = 0
            var offset = 0
            while (depthVar != 0) {
                val count = m_data[offset]
                accumulatedCount += count
                offset += 2 + count * 2
                --depthVar
            }
            return accumulatedCount
        }

        fun push(prefix: Int, uri: Int) {
            if (m_depth == 0) increaseDepth()
            ensureDataCapacity(2)
            val offset = m_dataLength - 1
            val count = m_data[offset]
            m_data[offset - 1 - count * 2] = count + 1
            m_data[offset] = prefix
            m_data[offset + 1] = uri
            m_data[offset + 2] = count + 1
            m_dataLength += 2
            m_count += 1
        }

        fun pop(prefix: Int, uri: Int): Boolean {
            if (m_dataLength == 0) return false
            val offset = m_dataLength - 1
            val count = m_data[offset]
            var i = 0
            var o = offset - 2
            while (i != count) {
                if (m_data[o] == prefix && m_data[o + 1] == uri) {
                    var newCount = count - 1
                    if (i == 0) {
                        m_data[o] = newCount
                        o -= 1 + newCount * 2
                        m_data[o] = newCount
                    } else {
                        m_data[offset] = newCount
                        var newOffset = offset - (1 + 2 + newCount * 2)
                        m_data[newOffset] = newCount
                        System.arraycopy(m_data, o + 2, m_data, o, m_dataLength - o)
                    }
                    m_dataLength -= 2
                    m_count -= 1
                    return true
                }
                ++i
                o -= 2
            }
            return false
        }

        fun pop(): Boolean {
            if (m_dataLength == 0) return false
            val offset = m_dataLength - 1
            val count = m_data[offset]
            if (count == 0) return false
            var newCount = count - 1
            var o = offset - 2
            m_data[o] = newCount
            o -= 1 + newCount * 2
            m_data[o] = newCount
            m_dataLength -= 2
            m_count -= 1
            return true
        }

        fun getPrefix(index: Int): Int {
            return get(index, true)
        }

        fun getUri(index: Int): Int {
            return get(index, false)
        }

        fun findPrefix(uri: Int): Int {
            return find(uri, false)
        }

        fun findUri(prefix: Int): Int {
            return find(prefix, true)
        }

        fun getDepth(): Int {
            return m_depth
        }

        fun increaseDepth() {
            ensureDataCapacity(2)
            val offset = m_dataLength
            m_data[offset] = 0
            m_data[offset + 1] = 0
            m_dataLength += 2
            m_depth += 1
        }

        fun decreaseDepth() {
            if (m_dataLength == 0) return
            val offset = m_dataLength - 1
            val count = m_data[offset]
            if (offset - 1 - count * 2 == 0) return
            m_dataLength -= 2 + count * 2
            m_count -= count
            m_depth -= 1
        }

        private fun ensureDataCapacity(capacity: Int) {
            val available = m_data.size - m_dataLength
            if (available > capacity) return
            val newLength = (m_data.size + available) * 2
            val newData = IntArray(newLength)
            System.arraycopy(m_data, 0, newData, 0, m_dataLength)
            m_data = newData
        }

        private fun find(prefixOrUri: Int, prefix: Boolean): Int {
            if (m_dataLength == 0) return -1
            var offset = m_dataLength - 1
            for (i in m_depth downTo 1) {
                var count = m_data[offset]
                offset -= 2
                while (count != 0) {
                    if (prefix) {
                        if (m_data[offset] == prefixOrUri) return m_data[offset + 1]
                    } else {
                        if (m_data[offset + 1] == prefixOrUri) return m_data[offset]
                    }
                    offset -= 2
                    --count
                }
            }
            return -1
        }

        private fun get(index: Int, prefix: Boolean): Int {
            if (m_dataLength == 0 || index < 0) return -1
            var idx = index
            var offset = 0
            for (i in m_depth downTo 1) {
                val count = m_data[offset]
                if (idx >= count) {
                    idx -= count
                    offset += 2 + count * 2
                    continue
                }
                offset += 1 + idx * 2
                if (!prefix) offset += 1
                return m_data[offset]
            }
            return -1
        }

        private var m_data: IntArray
        private var m_dataLength: Int = 0
        private var m_count: Int = 0
        private var m_depth: Int = 0
    }

    fun getStrings(): StringBlock? {
        return m_strings
    }

    private fun getAttributeOffset(index: Int): Int {
        if (m_event != START_TAG) throw IndexOutOfBoundsException("Current event is not START_TAG.")
        val offset = index * 5
        if (offset >= m_attributes!!.size) throw IndexOutOfBoundsException("Invalid attribute index (" + index + ").")
        return offset
    }

    private fun findAttribute(namespace: String?, attribute: String?): Int {
        if (m_strings == null || attribute == null) return -1
        val name = m_strings!!.find(attribute)
        if (name == -1) return -1
        val uri = if (namespace != null) m_strings!!.find(namespace) else -1
        var o = 0
        while (o != m_attributes!!.size) {
            if (name == m_attributes!![o + ATTRIBUTE_IX_NAME] && (uri == -1 || uri == m_attributes!![o + ATTRIBUTE_IX_NAMESPACE_URI]))
                return o / ATTRIBUTE_LENGHT
            ++o
        }
        return -1
    }

    private fun resetEventInfo() {
        m_event = -1
        m_lineNumber = -1
        m_name = -1
        m_namespaceUri = -1
        m_attributes = null
        m_idAttribute = -1
        m_classAttribute = -1
        m_styleAttribute = -1
    }

    @Throws(IOException::class)
    private fun doNext() {
        if (m_strings == null) {
            ChunkUtil.readCheckType(m_reader!!, CHUNK_AXML_FILE)
            m_reader!!.skipInt()
            m_strings = StringBlock.read(m_reader!!)
            m_namespaces.increaseDepth()
            m_operational = true
        }
        if (m_event == END_DOCUMENT) return
        var event = m_event
        resetEventInfo()
        while (true) {
            if (m_decreaseDepth) {
                m_decreaseDepth = false
                m_namespaces.decreaseDepth()
            }
            if (event == END_TAG && m_namespaces.getDepth() == 1 && m_namespaces.getCurrentCount() == 0) {
                m_event = END_DOCUMENT
                break
            }
            val chunkType: Int
            if (event == START_DOCUMENT) chunkType = CHUNK_XML_START_TAG
            else chunkType = m_reader!!.readInt()

            if (chunkType == CHUNK_RESOURCEIDS) {
                val chunkSize = m_reader!!.readInt()
                if (chunkSize < 8 || chunkSize % 4 != 0) throw IOException("Invalid resource ids size (" + chunkSize + ").")
                m_resourceIDs = m_reader!!.readIntArray(chunkSize / 4 - 2)
                continue
            }
            if (chunkType < CHUNK_XML_FIRST || chunkType > CHUNK_XML_LAST) throw IOException("Invalid chunk type (" + chunkType + ").")
            if (chunkType == CHUNK_XML_START_TAG && event == -1) {
                m_event = START_DOCUMENT
                break
            }
            m_reader!!.skipInt()
            val lineNumber = m_reader!!.readInt()
            m_reader!!.skipInt()
            if (chunkType == CHUNK_XML_START_NAMESPACE || chunkType == CHUNK_XML_END_NAMESPACE) {
                if (chunkType == CHUNK_XML_START_NAMESPACE) {
                    val prefix = m_reader!!.readInt()
                    val uri = m_reader!!.readInt()
                    if (uri == -1) continue
                    val uriStr = m_strings!!.getString(uri)
                    if (uriStr == null || uriStr.isEmpty()) continue
                    m_namespaces.push(prefix, uri)
                } else {
                    m_reader!!.skipInt()
                    m_reader!!.skipInt()
                    m_namespaces.pop()
                }
                continue
            }
            m_lineNumber = lineNumber
            if (chunkType == CHUNK_XML_START_TAG) {
                m_namespaceUri = m_reader!!.readInt()
                m_name = m_reader!!.readInt()
                m_reader!!.skipInt()
                var attributeCount = m_reader!!.readInt()
                m_idAttribute = (attributeCount ushr 16) - 1
                attributeCount = attributeCount and 0xFFFF
                m_classAttribute = m_reader!!.readInt()
                m_styleAttribute = (m_classAttribute ushr 16) - 1
                m_classAttribute = (m_classAttribute and 0xFFFF) - 1
                m_attributes = m_reader!!.readIntArray(attributeCount * ATTRIBUTE_LENGHT)
                var i = ATTRIBUTE_IX_VALUE_TYPE
                while (i < m_attributes!!.size) {
                    m_attributes!![i] = m_attributes!![i] ushr 24
                    i += ATTRIBUTE_LENGHT
                }
                m_namespaces.increaseDepth()
                m_event = START_TAG
                break
            }
            if (chunkType == CHUNK_XML_END_TAG) {
                m_namespaceUri = m_reader!!.readInt()
                m_name = m_reader!!.readInt()
                m_event = END_TAG
                m_decreaseDepth = true
                break
            }
            if (chunkType == CHUNK_XML_TEXT) {
                m_name = m_reader!!.readInt()
                m_reader!!.skipInt()
                m_reader!!.skipInt()
                m_event = TEXT
                break
            }
        }
    }

    private var m_reader: IntReader? = null
    private var m_operational: Boolean = false
    private var m_strings: StringBlock? = null
    private var m_resourceIDs: IntArray? = null
    private var m_namespaces: NamespaceStack = NamespaceStack()
    private var m_decreaseDepth: Boolean = false
    private var m_event: Int = 0
    private var m_lineNumber: Int = 0
    private var m_name: Int = 0
    private var m_namespaceUri: Int = 0
    private var m_attributes: IntArray? = null
    private var m_idAttribute: Int = 0
    private var m_classAttribute: Int = 0
    private var m_styleAttribute: Int = 0

    companion object {
        private const val E_NOT_SUPPORTED = "Method is not supported."
        private const val ATTRIBUTE_IX_NAMESPACE_URI = 0
        private const val ATTRIBUTE_IX_NAME = 1
        private const val ATTRIBUTE_IX_VALUE_STRING = 2
        private const val ATTRIBUTE_IX_VALUE_TYPE = 3
        private const val ATTRIBUTE_IX_VALUE_DATA = 4
        private const val ATTRIBUTE_LENGHT = 5
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_RESOURCEIDS = 0x00080180
        private const val CHUNK_XML_FIRST = 0x00100100
        private const val CHUNK_XML_START_NAMESPACE = 0x00100100
        private const val CHUNK_XML_END_NAMESPACE = 0x00100101
        private const val CHUNK_XML_START_TAG = 0x00100102
        private const val CHUNK_XML_END_TAG = 0x00100103
        private const val CHUNK_XML_TEXT = 0x00100104
        private const val CHUNK_XML_LAST = 0x00100104
    }
}
