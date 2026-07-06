package com.ark.jar.android.util

class TypedValue {
    @JvmField var type: Int = 0
    @JvmField var string: CharSequence? = null
    @JvmField var data: Int = 0
    @JvmField var assetCookie: Int = 0
    @JvmField var resourceId: Int = 0
    @JvmField var changingConfigurations: Int = 0

    companion object {
        const val TYPE_NULL: Int = 0
        const val TYPE_REFERENCE: Int = 1
        const val TYPE_ATTRIBUTE: Int = 2
        const val TYPE_STRING: Int = 3
        const val TYPE_FLOAT: Int = 4
        const val TYPE_DIMENSION: Int = 5
        const val TYPE_FRACTION: Int = 6
        const val TYPE_FIRST_INT: Int = 16
        const val TYPE_INT_DEC: Int = 16
        const val TYPE_INT_HEX: Int = 17
        const val TYPE_INT_BOOLEAN: Int = 18
        const val TYPE_FIRST_COLOR_INT: Int = 28
        const val TYPE_INT_COLOR_ARGB8: Int = 28
        const val TYPE_INT_COLOR_RGB8: Int = 29
        const val TYPE_INT_COLOR_ARGB4: Int = 30
        const val TYPE_INT_COLOR_RGB4: Int = 31
        const val TYPE_LAST_COLOR_INT: Int = 31
        const val TYPE_LAST_INT: Int = 31

        const val COMPLEX_UNIT_PX: Int = 0
        const val COMPLEX_UNIT_DIP: Int = 1
        const val COMPLEX_UNIT_SP: Int = 2
        const val COMPLEX_UNIT_PT: Int = 3
        const val COMPLEX_UNIT_IN: Int = 4
        const val COMPLEX_UNIT_MM: Int = 5
        const val COMPLEX_UNIT_SHIFT: Int = 0
        const val COMPLEX_UNIT_MASK: Int = 15
        const val COMPLEX_UNIT_FRACTION: Int = 0
        const val COMPLEX_UNIT_FRACTION_PARENT: Int = 1
        const val COMPLEX_RADIX_23p0: Int = 0
        const val COMPLEX_RADIX_16p7: Int = 1
        const val COMPLEX_RADIX_8p15: Int = 2
        const val COMPLEX_RADIX_0p23: Int = 3
        const val COMPLEX_RADIX_SHIFT: Int = 4
        const val COMPLEX_RADIX_MASK: Int = 3
        const val COMPLEX_MANTISSA_SHIFT: Int = 8
        const val COMPLEX_MANTISSA_MASK: Int = 0xFFFFFF
    }
}
