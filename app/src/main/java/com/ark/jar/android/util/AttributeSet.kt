package com.ark.jar.android.util

interface AttributeSet {
    val attributeCount: Int
    fun getAttributeName(index: Int): String?
    fun getAttributeValue(index: Int): String?
    fun getPositionDescription(): String?
    fun getAttributeNameResource(index: Int): Int
    fun getAttributeListValue(index: Int, options: Array<String?>?, defaultValue: Int): Int
    fun getAttributeBooleanValue(index: Int, defaultValue: Boolean): Boolean
    fun getAttributeResourceValue(index: Int, defaultValue: Int): Int
    fun getAttributeIntValue(index: Int, defaultValue: Int): Int
    fun getAttributeUnsignedIntValue(index: Int, defaultValue: Int): Int
    fun getAttributeFloatValue(index: Int, defaultValue: Float): Float
    fun getIdAttribute(): String?
    fun getClassAttribute(): String?
    fun getIdAttributeResourceValue(index: Int): Int
    fun getStyleAttribute(): Int
    fun getAttributeValue(namespace: String?, attribute: String?): String?
    fun getAttributeListValue(namespace: String?, attribute: String?, options: Array<String?>?, defaultValue: Int): Int
    fun getAttributeBooleanValue(namespace: String?, attribute: String?, defaultValue: Boolean): Boolean
    fun getAttributeResourceValue(namespace: String?, attribute: String?, defaultValue: Int): Int
    fun getAttributeIntValue(namespace: String?, attribute: String?, defaultValue: Int): Int
    fun getAttributeUnsignedIntValue(namespace: String?, attribute: String?, defaultValue: Int): Int
    fun getAttributeFloatValue(namespace: String?, attribute: String?, defaultValue: Float): Float
    fun getAttributeValueType(index: Int): Int
    fun getAttributeValueData(index: Int): Int
}
