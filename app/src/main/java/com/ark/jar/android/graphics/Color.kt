package com.ark.jar.android.graphics

import java.util.HashMap
import java.util.Locale

object Color {
    @JvmField
    val BLACK = 0xFF000000.toInt()
    @JvmField
    val DKGRAY = 0xFF444444.toInt()
    @JvmField
    val GRAY = 0xFF888888.toInt()
    @JvmField
    val LTGRAY = 0xFFCCCCCC.toInt()
    @JvmField
    val WHITE = 0xFFFFFFFF.toInt()
    @JvmField
    val RED = 0xFFFF0000.toInt()
    @JvmField
    val GREEN = 0xFF00FF00.toInt()
    @JvmField
    val BLUE = 0xFF0000FF.toInt()
    @JvmField
    val YELLOW = 0xFFFFFF00.toInt()
    @JvmField
    val CYAN = 0xFF00FFFF.toInt()
    @JvmField
    val MAGENTA = 0xFFFF00FF.toInt()
    @JvmField
    val TRANSPARENT = 0

    @JvmStatic
    fun alpha(color: Int): Int = color ushr 24

    @JvmStatic
    fun red(color: Int): Int = (color shr 16) and 0xFF

    @JvmStatic
    fun green(color: Int): Int = (color shr 8) and 0xFF

    @JvmStatic
    fun blue(color: Int): Int = color and 0xFF

    @JvmStatic
    fun rgb(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    @JvmStatic
    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    @JvmStatic
    fun hue(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val V = Math.max(b, Math.max(r, g))
        val temp = Math.min(b, Math.min(r, g))
        var H: Float
        if (V == temp) {
            H = 0f
        } else {
            val vtemp = (V - temp).toFloat()
            val cr = (V - r) / vtemp
            val cg = (V - g) / vtemp
            val cb = (V - b) / vtemp
            H = if (r == V) cb - cg
            else if (g == V) 2f + cr - cb
            else 4f + cg - cr
            H /= 6f
            if (H < 0) H++
        }
        return H
    }

    @JvmStatic
    fun saturation(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val V = Math.max(b, Math.max(r, g))
        val temp = Math.min(b, Math.min(r, g))
        return if (V == temp) 0f else (V - temp) / V.toFloat()
    }

    @JvmStatic
    fun brightness(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return Math.max(b, Math.max(r, g)) / 255f
    }

    @JvmStatic
    fun parseColor(colorString: String): Int {
        if (colorString[0] == '#') {
            val color = java.lang.Long.parseLong(colorString.substring(1), 16)
            return if (colorString.length == 7) (color or 0x00000000ff000000L).toInt()
            else if (colorString.length != 9) throw IllegalArgumentException("Unknown color")
            else color.toInt()
        } else {
            val color = sColorNameMap[colorString.lowercase(Locale.ROOT)]
            if (color != null) return color
            else throw IllegalArgumentException("Unknown color")
        }
    }

    @JvmStatic
    fun RGBToHSV(red: Int, green: Int, blue: Int, hsv: FloatArray) {
        if (hsv.size < 3) throw RuntimeException("3 components required for hsv")
        nativeRGBToHSV(red, green, blue, hsv)
    }

    @JvmStatic
    fun colorToHSV(color: Int, hsv: FloatArray) {
        RGBToHSV((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, hsv)
    }

    @JvmStatic
    fun HSVToColor(hsv: FloatArray): Int = HSVToColor(0xFF, hsv)

    @JvmStatic
    fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        if (hsv.size < 3) throw RuntimeException("3 components required for hsv")
        return nativeHSVToColor(alpha, hsv)
    }

    @JvmStatic
    private external fun nativeRGBToHSV(red: Int, greed: Int, blue: Int, hsv: FloatArray)

    @JvmStatic
    private external fun nativeHSVToColor(alpha: Int, hsv: FloatArray): Int

    @JvmField
    val sColorNameMap: HashMap<String, Int> = HashMap<String, Int>().apply {
        put("black", BLACK)
        put("darkgray", DKGRAY)
        put("gray", GRAY)
        put("lightgray", LTGRAY)
        put("white", WHITE)
        put("red", RED)
        put("green", GREEN)
        put("blue", BLUE)
        put("yellow", YELLOW)
        put("cyan", CYAN)
        put("magenta", MAGENTA)
        put("aqua", 0xFF00FFFF.toInt())
        put("fuchsia", 0xFFFF00FF.toInt())
        put("darkgrey", DKGRAY)
        put("grey", GRAY)
        put("lightgrey", LTGRAY)
        put("lime", 0xFF00FF00.toInt())
        put("maroon", 0xFF800000.toInt())
        put("navy", 0xFF000080.toInt())
        put("olive", 0xFF808000.toInt())
        put("purple", 0xFF800080.toInt())
        put("silver", 0xFFC0C0C0.toInt())
        put("teal", 0xFF008080.toInt())
    }
}
