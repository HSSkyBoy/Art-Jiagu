package com.ark.jar.xml2axml.chunks

import android.graphics.Color
import com.ark.jar.xml2axml.ComplexConsts
import com.ark.jar.xml2axml.IntWriter
import com.ark.jar.xml2axml.NotImplementedException
import com.ark.jar.xml2axml.ValueType
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by Roy on 15-10-6.
 */
class ValueChunk(parent: AttrChunk) : Chunk<Chunk.EmptyHeader>(parent) {

    inner class ValPair(m: Matcher) {
        var pos: Int = 0
        var value: String = ""

        init {
            val c = m.groupCount()
            for (i in 1..c) {
                val s = m.group(i)
                if (s == null || s.isEmpty()) continue
                pos = i
                value = s
                return
            }
            pos = -1
            value = m.group()
        }
    }

    private val attrChunk: AttrChunk = parent
    private var realString: String? = null

    var size: Short = 8
    var res0: Byte = 0
    var type: Byte = -1
    var data: Int = -1

    val explicitType = Pattern.compile("!(?:(\\w+)!)?(.*)")
    val types = Pattern.compile(
        ("^(?:" +
                "(@null)" +
                "|(@\\+?(?:\\w+:)?\\w+/\\w+|@(?:\\w+:)?[0-9a-zA-Z]+)" +
                "|(true|false)" +
                "|([-+]?\\d+)" +
                "|(0x[0-9a-zA-Z]+)" +
                "|([-+]?\\d+(?:\\.\\d+)?)" +
                "|([-+]?\\d+(?:\\.\\d+)?(?:dp|dip|in|px|sp|pt|mm))" +
                "|([-+]?\\d+(?:\\.\\d+)?(?:%))" +
                "|(\\#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))" +
                "|(match_parent|wrap_content|fill_parent)" +
                ")$").replaceAll("\\s+", "")
    )

    init {
        header.size = 8
    }

    override fun preWrite() {
        evaluate()
    }

    @Throws(IOException::class)
    override fun writeEx(w: IntWriter) {
        w.write(size)
        w.write(res0)
        if (type == ValueType.STRING) {
            data = stringIndex(null, realString)
        }
        w.write(type)
        w.write(data)
    }

    fun evalcomplex(value: String): Int {
        val (num, unit) = when {
            value.endsWith("%") -> value.substring(0, value.length - 1) to ComplexConsts.UNIT_FRACTION
            value.endsWith("dp") -> value.substring(0, value.length - 2) to ComplexConsts.UNIT_DIP
            value.endsWith("dip") -> value.substring(0, value.length - 3) to ComplexConsts.UNIT_DIP
            value.endsWith("sp") -> value.substring(0, value.length - 2) to ComplexConsts.UNIT_SP
            value.endsWith("px") -> value.substring(0, value.length - 2) to ComplexConsts.UNIT_PX
            value.endsWith("pt") -> value.substring(0, value.length - 2) to ComplexConsts.UNIT_PT
            value.endsWith("in") -> value.substring(0, value.length - 2) to ComplexConsts.UNIT_IN
            value.endsWith("mm") -> value.substring(0, value.length - 2) to ComplexConsts.UNIT_MM
            else -> throw RuntimeException("invalid unit")
        }
        val f = num.toDouble()
        val (base, radix) = when {
            f < 1 && f > -1 -> (f * (1 shl 23)).toInt() to ComplexConsts.RADIX_0p23
            f < 0x100 && f > -0x100 -> (f * (1 shl 15)).toInt() to ComplexConsts.RADIX_8p15
            f < 0x10000 && f > -0x10000 -> (f * (1 shl 7)).toInt() to ComplexConsts.RADIX_16p7
            else -> f.toInt() to ComplexConsts.RADIX_23p0
        }
        return (base shl 8) or (radix shl 4) or unit
    }

    fun evaluate() {
        var m = explicitType.matcher(attrChunk.rawValue)
        if (m.find()) {
            val t = m.group(1)
            val v = m.group(2)
            if (t == null || t.isEmpty() || t == "string" || t == "str") {
                type = ValueType.STRING
                realString = v
                stringPool().addString(realString)
                //data = stringIndex(null, v)
            } else {
                //TODO resolve other type
                throw NotImplementedException()
            }
        } else {
            m = types.matcher(attrChunk.rawValue)
            if (m.find()) {
                val vp = ValPair(m)
                when (vp.pos) {
                    1 -> {
                        type = ValueType.NULL
                        data = 0
                    }
                    2 -> {
                        type = ValueType.REFERENCE
                        data = getReferenceResolver().resolve(this, vp.value)
                    }
                    3 -> {
                        type = ValueType.INT_BOOLEAN
                        data = if ("true".equals(vp.value, ignoreCase = true)) 1 else 0
                    }
                    /*case 4:
                        type = ValueType.INT_DEC;
                        data = Integer.parseInt(vp.val);
                        break;*/
                    //20260302修复一个整数溢出BUG，同步后端XML2AXML库也修复了
                    4 -> {
                        try {
                            val longVal = vp.value.toLong()

                            if (longVal > Int.MAX_VALUE || longVal < Int.MIN_VALUE) {
                                // 超出 int 范围，降级为字符串
                                type = ValueType.STRING
                                realString = vp.value
                                stringPool().addString(realString)
                            } else {
                                type = ValueType.INT_DEC
                                data = longVal.toInt()
                            }

                        } catch (e: NumberFormatException) {
                            // 非法数字，强制当字符串
                            type = ValueType.STRING
                            realString = vp.value
                            stringPool().addString(realString)
                        }
                    }
                    5 -> {
                        type = ValueType.INT_HEX
                        data = Integer.parseInt(vp.value.substring(2), 16)
                    }
                    6 -> {
                        type = ValueType.FLOAT
                        data = java.lang.Float.floatToIntBits(vp.value.toFloat())
                    }
                    7 -> {
                        type = ValueType.DIMENSION
                        data = evalcomplex(vp.value)
                    }
                    8 -> {
                        type = ValueType.FRACTION
                        data = evalcomplex(vp.value)
                    }
                    9 -> {
                        type = ValueType.INT_COLOR_ARGB8
                        data = Color.parseColor(vp.value)
                    }
                    10 -> {
                        type = ValueType.INT_DEC
                        data = if ("wrap_content".equals(vp.value, ignoreCase = true)) -2 else -1
                    }
                    else -> {
                        type = ValueType.STRING
                        realString = vp.value
                        stringPool().addString(realString)
                        //data = stringIndex(null, attrChunk.rawValue)
                    }
                }
            } else {
                type = ValueType.STRING
                realString = attrChunk.rawValue
                stringPool().addString(realString)
                //data = stringIndex(null, attrChunk.rawValue)
            }
        }
    }
}
