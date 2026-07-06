package com.ark.jar.xml2axml

object ValueType {
    const val NULL: Byte = 0x00
    const val REFERENCE: Byte = 0x01
    const val ATTRIBUTE: Byte = 0x02
    const val STRING: Byte = 0x03
    const val FLOAT: Byte = 0x04
    const val DIMENSION: Byte = 0x05
    const val FRACTION: Byte = 0x06
    const val FIRST_INT: Byte = 0x10
    const val INT_DEC: Byte = 0x10
    const val INT_HEX: Byte = 0x11
    const val INT_BOOLEAN: Byte = 0x12
    const val FIRST_COLOR_INT: Byte = 0x1c
    const val INT_COLOR_ARGB8: Byte = 0x1c
    const val INT_COLOR_RGB8: Byte = 0x1d
    const val INT_COLOR_ARGB4: Byte = 0x1e
    const val INT_COLOR_RGB4: Byte = 0x1f
    const val LAST_COLOR_INT: Byte = 0x1f
    const val LAST_INT: Byte = 0x1f
}
