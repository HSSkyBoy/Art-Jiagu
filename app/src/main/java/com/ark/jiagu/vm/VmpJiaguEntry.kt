package com.ark.jiagu.vm

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.ExceptionHandler
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.MethodImplementation
import com.android.tools.smali.dexlib2.iface.MethodParameter
import com.android.tools.smali.dexlib2.iface.TryBlock
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31i
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class VmpJiaguEntry {

    private class MethodRule {
        var raw: String? = null
        var packageName: String? = null
        var className: String? = null
        var methodName: String? = null
    }

    private class ExtractMethodBlock {
        var classId: Int = 0
        var dexName: String? = null
        var className: String? = null
        var methodName: String? = null
        var methodSignature: String? = null
        var accessFlags: Int = 0
        var registerCount: Int = 0
        var paramCount: Int = 0
        var returnType: String? = null
        var instructions: MutableList<ExtractInstruction> = ArrayList()
    }

    private class ExtractInstruction {
        var vmOpcode: Int = 0
        var opcodeName: String? = null
        var dexlibOpcodeValue: Int = 0
        var formatName: String? = null
        var codeUnits: Int = 0
        var registers: MutableList<Int> = ArrayList()
        var literalType: Int = 0
        var literalValue: Long = 0
        var offsetType: Int = 0
        var offsetValue: Int = 0
        var referenceType: Int = 0
        var referenceData: String? = null
    }

    private class ClassIndexEntry {
        var classId: Int = 0
        var offset: Long = 0
        var size: Int = 0
    }

    private class ExtractedMethodInfo {
        var classId: Int = 0
        var dexName: String? = null
        var className: String? = null
        var methodName: String? = null
        var methodSignature: String? = null
        var accessFlags: Int = 0
        var registerCount: Int = 0
        var paramCount: Int = 0
        var returnType: String? = null
    }

    companion object {
        private val EXTRACTED_METHOD_MAP = LinkedHashMap<String, ExtractedMethodInfo>()

        //这个类是将dex中的方法的字节码抽取并转native的类，只是抽取部分，真正的vmp解释器还没实现
        /**
         * :TODO
         * 解压 APK 中的 AndroidManifest.xml 和连续合法 dex 文件。
         * 合法 dex：
         * classes.dex
         * classes2.dex
         * classes3.dex
         * ...
         * 如果中间断号，例如没有 classes4.dex，则停止继续解压。
         */
        @JvmStatic
        fun extractManifestAndDex(apkFile: File, outDir: File) {
            if (apkFile == null || !apkFile.isFile()) {
                throw IOException("APK文件不存在")
            }

            if (outDir == null) {
                throw IOException("输出目录为空")
            }

            if (!outDir.exists() && !outDir.mkdirs()) {
                throw IOException("创建输出目录失败：" + outDir.absolutePath)
            }

            ZipFile(apkFile).use { zipFile ->
                extractEntry(zipFile, "AndroidManifest.xml", File(outDir, "AndroidManifest.xml"))

                var index = 1
                while (true) {
                    val dexName = if (index == 1) "classes.dex" else "classes$index.dex"
                    val dexEntry = zipFile.getEntry(dexName)

                    if (dexEntry == null) {
                        break
                    }

                    extractEntry(zipFile, dexName, File(outDir, dexName))
                    index++
                }
            }
        }

        private fun extractEntry(zipFile: ZipFile, entryName: String, outFile: File) {
            val entry = zipFile.getEntry(entryName)
            if (entry == null) {
                throw IOException("APK中未找到文件：" + entryName)
            }

            val parent = outFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw IOException("创建目录失败：" + parent.absolutePath)
            }

            zipFile.getInputStream(entry).use { `in` ->
                FileOutputStream(outFile).use { out ->
                    val buffer = ByteArray(8192)
                    var len: Int

                    while (`in`.read(buffer).also { len = it } != -1) {
                        out.write(buffer, 0, len)
                    }
                }
            }
        }

        //----------------------------------------------------------------------------------------------------------
        /**
         * :TODO
         * 生成一个VMP的入口类。
         */
        @JvmStatic
        fun createVmpClass(soName: String, packageName: String): ClassDef {
            if (soName == null || soName.trim { it <= ' ' }.isEmpty()) {
                throw IllegalArgumentException("so库名称不能为空")
            }

            if (packageName == null || packageName.trim { it <= ' ' }.isEmpty()) {
                throw IllegalArgumentException("包名不能为空")
            }

            val cleanPackageName = packageName.trim { it <= ' ' }
            val classType = "L" + cleanPackageName.replace('.', '/') + "/VMP;"

            val methods = ArrayList<Method>()

            // public VMP()
            val initImpl = ImmutableMethodImplementation(
                1,
                Arrays.asList(
                    ImmutableInstruction35c(
                        Opcode.INVOKE_DIRECT,
                        1, 0, 0, 0, 0, 0,
                        ImmutableMethodReference(
                            "Ljava/lang/Object;",
                            "<init>",
                            Collections.emptyList(),
                            "V"
                        )
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                null,
                null
            )

            methods.add(
                ImmutableMethod(
                    classType,
                    "<init>",
                    Collections.emptyList(),
                    "V",
                    AccessFlags.PUBLIC.getValue() or AccessFlags.CONSTRUCTOR.getValue(),
                    null,
                    null,
                    initImpl
                )
            )

            // static { System.loadLibrary("传入的so库名称"); }
            val clinitImpl = ImmutableMethodImplementation(
                1,
                Arrays.asList(
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        0,
                        ImmutableStringReference(soName)
                    ),
                    ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC,
                        1, 0, 0, 0, 0, 0,
                        ImmutableMethodReference(
                            "Ljava/lang/System;",
                            "loadLibrary",
                            Collections.singletonList("Ljava/lang/String;"),
                            "V"
                        )
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                null,
                null
            )

            methods.add(
                ImmutableMethod(
                    classType,
                    "<clinit>",
                    Collections.emptyList(),
                    "V",
                    AccessFlags.STATIC.getValue() or AccessFlags.CONSTRUCTOR.getValue(),
                    null,
                    null,
                    clinitImpl
                )
            )

            // public static native void init(int id, Class<?> clazz);
            val initParams = Arrays.asList(
                ImmutableMethodParameter("I", Collections.emptySet(), null),
                ImmutableMethodParameter("Ljava/lang/Class;", Collections.emptySet(), null)
            )

            methods.add(
                ImmutableMethod(
                    classType,
                    "init",
                    initParams,
                    "V",
                    AccessFlags.PUBLIC.getValue()
                        or AccessFlags.STATIC.getValue()
                        or AccessFlags.NATIVE.getValue(),
                    null,
                    null,
                    null
                )
            )

            return ImmutableClassDef(
                classType,
                AccessFlags.PUBLIC.getValue(),
                "Ljava/lang/Object;",
                Collections.emptyList(),
                "VMP.java",
                null,
                Collections.emptyList(),
                methods
            )
        }

        /**
         * :TODO
         * 返回一个带 VMP 入口调用的DEX
         * */
        @JvmStatic
        fun createVmpShellDex(soName: String, packageName: String): DexFile {
            val vmpClass = createVmpClass(soName, packageName)

            val classes = ArrayList<ClassDef>()
            classes.add(vmpClass)

            return ImmutableDexFile(
                Opcodes.getDefault(),
                classes
            )
        }

        /**
         * :TODO
         * 生成一张自定义指令映射表
         * */
        @JvmStatic
        fun printDexlib2Opcodes(): Map<Opcode, Int> {
            val customOpcodeMap = LinkedHashMap<Opcode, Int>()

            val customValues = ArrayList<Int>()
            for (i in 0..0xFF) {
                customValues.add(i)
            }

            Collections.shuffle(customValues, Random(System.currentTimeMillis()))

            var index = 0

            for (opcode in Opcode.values()) {
                if (index >= customValues.size) {
                    break
                }

                val rawOpcode = opcode.ordinal
                val customOpcode = customValues[index]
                index++

                customOpcodeMap[opcode] = customOpcode

                System.out.println(
                    "名称=" + opcode.name
                        + " 值=0x" + String.format("%02x", rawOpcode)
                        + " 自定义字节=0x" + String.format("%02x", customOpcode)
                )
            }

            return customOpcodeMap
        }

        /**
         * :TODO
         * 打印出待抽取方法的内容
         * */
        @JvmStatic
        fun printOnCreateExtractInfo(dexFile: File) {
            if (dexFile == null || !dexFile.isFile()) {
                throw IOException("dex文件不存在")
            }

            val dex = DexFileFactory.loadDexFile(
                dexFile,
                Opcodes.getDefault()
            )

            for (classDef in dex.classes) {
                for (method in classDef.methods) {
                    if ("onCreate" != method.name) {
                        continue
                    }

                    System.out.println("========================================")
                    System.out.println("发现待抽取方法")
                    System.out.println("类名: " + classDef.type)
                    System.out.println("方法名: " + method.name)
                    System.out.println("方法签名: " + buildMethodSignature(method))
                    System.out.println("访问标志: 0x" + Integer.toHexString(method.accessFlags))
                    System.out.println("访问标志文本: " + formatAccessFlags(method.accessFlags))
                    System.out.println("返回类型: " + method.returnType)

                    System.out.println("参数数量: " + method.parameters.size)
                    var paramIndex = 0
                    for (parameter in method.parameters) {
                        System.out.println("参数[$paramIndex]: " + parameter.type)
                        paramIndex++
                    }

                    val impl = method.implementation
                    if (impl == null) {
                        System.out.println("方法实现: 空，可能已经是native或abstract")
                        continue
                    }

                    System.out.println("寄存器数量: " + impl.registerCount)

                    var insnIndex = 0
                    for (instruction in impl.instructions) {
                        System.out.println(
                            "指令[$insnIndex] "
                                + "opcode=" + instruction.opcode.name
                                + " 格式=" + instruction.opcode.format
                                + " codeUnits=" + instruction.codeUnits
                        )
                        printInstructionOperands(instruction)
                        printInstructionReference(instruction)

                        insnIndex++
                    }

                    System.out.println("try-catch数量: " + impl.tryBlocks.size)
                    var tryIndex = 0
                    for (tryBlock in impl.tryBlocks) {
                        System.out.println(
                            "try[$tryIndex] "
                                + "startCodeAddress=" + tryBlock.startCodeAddress
                                + " codeUnitCount=" + tryBlock.codeUnitCount
                        )

                        for (handler in tryBlock.exceptionHandlers) {
                            System.out.println(
                                "  catch type=" + handler.exceptionType
                                    + " handlerCodeAddress=" + handler.handlerCodeAddress
                            )
                        }

                        tryIndex++
                    }
                }
            }
        }

        private fun printInstructionOperands(instruction: Instruction) {
            if (instruction is OneRegisterInstruction) {
                System.out.println("  寄存器A: v" + instruction.registerA)
            }

            if (instruction is TwoRegisterInstruction) {
                System.out.println("  寄存器A: v" + instruction.registerA)
                System.out.println("  寄存器B: v" + instruction.registerB)
            }

            if (instruction is ThreeRegisterInstruction) {
                System.out.println("  寄存器A: v" + instruction.registerA)
                System.out.println("  寄存器B: v" + instruction.registerB)
                System.out.println("  寄存器C: v" + instruction.registerC)
            }

            if (instruction is FiveRegisterInstruction) {
                System.out.println("  寄存器数量: " + instruction.registerCount)
                System.out.println("  寄存器C: v" + instruction.registerC)
                System.out.println("  寄存器D: v" + instruction.registerD)
                System.out.println("  寄存器E: v" + instruction.registerE)
                System.out.println("  寄存器F: v" + instruction.registerF)
                System.out.println("  寄存器G: v" + instruction.registerG)
            }

            if (instruction is RegisterRangeInstruction) {
                System.out.println("  起始寄存器: v" + instruction.startRegister)
                System.out.println("  寄存器数量: " + instruction.registerCount)
            }

            if (instruction is NarrowLiteralInstruction) {
                System.out.println("  int常量: " + instruction.narrowLiteral)
                System.out.println("  int常量HEX: 0x" + Integer.toHexString(instruction.narrowLiteral))
            }

            if (instruction is WideLiteralInstruction) {
                System.out.println("  long常量: " + instruction.wideLiteral)
                System.out.println("  long常量HEX: 0x" + java.lang.Long.toHexString(instruction.wideLiteral))
            }

            if (instruction is OffsetInstruction) {
                System.out.println("  跳转偏移: " + instruction.codeOffset)
            }
        }

        private fun buildMethodSignature(method: Method): String {
            val sb = StringBuilder()
            sb.append("(")

            for (paramType in method.parameterTypes) {
                sb.append(paramType)
            }

            sb.append(")")
            sb.append(method.returnType)

            return sb.toString()
        }

        private fun formatAccessFlags(accessFlags: Int): String {
            val sb = StringBuilder()

            for (flag in AccessFlags.values()) {
                if (flag.isSet(accessFlags)) {
                    if (sb.length > 0) {
                        sb.append(" ")
                    }
                    sb.append(flag.name)
                }
            }

            return sb.toString()
        }

        private fun printInstructionReference(instruction: Instruction) {
            if (instruction !is ReferenceInstruction) {
                return
            }

            val reference = instruction.reference

            if (reference is StringReference) {
                System.out.println("  字符串引用: " + reference.string)
            } else if (reference is TypeReference) {
                System.out.println("  类型引用: " + reference.type)
            } else if (reference is FieldReference) {
                System.out.println(
                    "  字段引用: "
                        + reference.definingClass
                        + "->"
                        + reference.name
                        + ":"
                        + reference.type
                )
            } else if (reference is MethodReference) {
                System.out.println(
                    "  方法引用: "
                        + reference.definingClass
                        + "->"
                        + reference.name
                        + buildMethodReferenceSignature(reference)
                )
            } else {
                System.out.println("  其他引用: " + reference)
            }
        }

        private fun buildMethodReferenceSignature(methodRef: MethodReference): String {
            val sb = StringBuilder()
            sb.append("(")

            for (paramType in methodRef.parameterTypes) {
                sb.append(paramType)
            }

            sb.append(")")
            sb.append(methodRef.returnType)

            return sb.toString()
        }

        /**
         * :TODO
         * 抽取方法到bin
         * */
        @JvmStatic
        fun extractOnCreateToBin(dexDir: File, vararg methodRules: String) {
            if (dexDir == null || !dexDir.isDirectory()) {
                throw IOException("dex目录不存在")
            }

            val rules = parseMethodRules(*methodRules)
            if (rules.isEmpty()) {
                throw IOException("抽取规则为空")
            }

            val blocks = ArrayList<ExtractMethodBlock>()

            val opcodeMap = LinkedHashMap<String, Int>()
            val opcodePool = buildRandomOpcodePool()
            val opcodePoolIndex = intArrayOf(0)

            var nextClassId = 1
            var dexIndex = 1

            while (true) {
                val dexName = if (dexIndex == 1) "classes.dex" else "classes${dexIndex}.dex"
                val dexFile = File(dexDir, dexName)

                if (!dexFile.isFile()) {
                    System.out.println("dex编号断开，停止扫描：" + dexName)
                    break
                }

                if (!isValidDexFile(dexFile)) {
                    System.out.println("跳过非法dex文件：" + dexFile.absolutePath)
                    break
                }

                val dex: DexBackedDexFile
                try {
                    dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
                } catch (e: Throwable) {
                    System.out.println("解析dex失败，停止扫描：" + dexFile.name)
                    System.out.println("失败原因：" + e.message)
                    break
                }

                for (classDef in dex.classes) {
                    val javaClassName = dexTypeToJavaName(classDef.type)

                    for (method in classDef.methods) {
                        if (!matchAnyRule(rules, javaClassName, method.name)) {
                            continue
                        }

                        if (isForbiddenExtractMethod(method)) {
                            System.out.println("跳过禁止抽取方法：" + classDef.type + "->" + method.name)
                            continue
                        }

                        val impl = method.implementation
                        if (impl == null) {
                            System.out.println("跳过无实现方法：" + classDef.type + "->" + method.name)
                            continue
                        }

                        val block = ExtractMethodBlock()
                        block.classId = nextClassId++
                        block.dexName = dexFile.name
                        block.className = classDef.type
                        block.methodName = method.name
                        block.methodSignature = buildMethodSignature(method)
                        block.accessFlags = method.accessFlags
                        block.registerCount = impl.registerCount
                        block.paramCount = method.parameters.size
                        block.returnType = method.returnType

                        for (instruction in impl.instructions) {
                            block.instructions.add(
                                buildExtractInstruction(
                                    instruction,
                                    opcodeMap,
                                    opcodePool,
                                    opcodePoolIndex
                                )
                            )
                        }

                        blocks.add(block)
                        recordExtractedMethod(block)
                        System.out.println("已抽取 classId=" + block.classId
                            + " dex=" + block.dexName
                            + " class=" + block.className
                            + " method=" + block.methodName
                            + block.methodSignature)
                    }
                }

                dexIndex++
            }

            val binFile = File(dexDir, "vmp.bin")
            val txtFile = File(dexDir, "vmp.txt")

            val indexEntries = writeVmpBinary(binFile, opcodeMap, blocks)
            writeVmpText(txtFile, opcodeMap, indexEntries, blocks)

            System.out.println("抽取完成，总数量=" + blocks.size)
            System.out.println("opcode映射数量=" + opcodeMap.size)
            System.out.println("索引表数量=" + indexEntries.size)
            System.out.println("明文抽取文件: " + txtFile.absolutePath)
            System.out.println("二进制抽取文件: " + binFile.absolutePath)
        }

        private fun parseMethodRules(vararg methodRules: String): List<MethodRule> {
            val rules = ArrayList<MethodRule>()

            if (methodRules == null) {
                return rules
            }

            for (ruleText in methodRules) {
                var text = ruleText
                if (text == null) {
                    continue
                }

                text = text.trim { it <= ' ' }
                if (text.isEmpty()) {
                    continue
                }

                val parts = text.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.size < 3) {
                    System.out.println("跳过非法规则：" + text)
                    continue
                }

                val methodName = parts[parts.size - 1]
                val className = parts[parts.size - 2]

                val pkg = StringBuilder()
                for (i in 0 until parts.size - 2) {
                    if (i > 0) {
                        pkg.append(".")
                    }
                    pkg.append(parts[i])
                }

                val rule = MethodRule()
                rule.raw = text
                rule.packageName = pkg.toString()
                rule.className = className
                rule.methodName = methodName

                rules.add(rule)

                System.out.println("添加抽取规则：" + rule.raw
                    + " 包名=" + rule.packageName
                    + " 类名=" + rule.className
                    + " 方法=" + rule.methodName)
            }

            return rules
        }

        private fun matchAnyRule(rules: List<MethodRule>, javaClassName: String, methodName: String): Boolean {
            for (rule in rules) {
                if (matchRule(rule, javaClassName, methodName)) {
                    return true
                }
            }
            return false
        }

        private fun matchRule(rule: MethodRule, javaClassName: String, methodName: String): Boolean {
            if (rule == null || javaClassName == null || methodName == null) {
                return false
            }

            val lastDot = javaClassName.lastIndexOf('.')
            val pkg = if (lastDot >= 0) javaClassName.substring(0, lastDot) else ""
            val cls = if (lastDot >= 0) javaClassName.substring(lastDot + 1) else javaClassName

            return matchPart(rule.packageName, pkg)
                && matchPart(rule.className, cls)
                && matchPart(rule.methodName, methodName)
        }

        private fun matchPart(rulePart: String?, value: String): Boolean {
            if ("*" == rulePart) {
                return true
            }
            return rulePart == value
        }

        private fun dexTypeToJavaName(dexType: String?): String {
            if (dexType == null) {
                return ""
            }

            var result = dexType
            if (result.startsWith("L") && result.endsWith(";")) {
                result = result.substring(1, result.length - 1)
            }

            return result.replace('/', '.')
        }

        private fun isForbiddenExtractMethod(method: Method?): Boolean {
            if (method == null) {
                return true
            }

            val name = method.name
            val flags = method.accessFlags

            if ("<init>" == name || "<clinit>" == name) {
                return true
            }

            if ((flags and AccessFlags.ABSTRACT.getValue()) != 0) {
                return true
            }

            if ((flags and AccessFlags.NATIVE.getValue()) != 0) {
                return true
            }

            if ((flags and AccessFlags.BRIDGE.getValue()) != 0) {
                return true
            }

            if ((flags and AccessFlags.SYNTHETIC.getValue()) != 0) {
                return true
            }

            if ((flags and AccessFlags.DECLARED_SYNCHRONIZED.getValue()) != 0) {
                return true
            }

            if ((flags and AccessFlags.SYNCHRONIZED.getValue()) != 0) {
                return true
            }

            if ((flags and AccessFlags.VARARGS.getValue()) != 0) {
                return true
            }

            return false
        }

        private fun buildRandomOpcodePool(): MutableList<Int> {
            val list = ArrayList<Int>()
            for (i in 1..255) {
                list.add(i)
            }
            Collections.shuffle(list, Random(System.currentTimeMillis()))
            return list
        }

        private fun getOrCreateVmOpcode(
            opcodeName: String,
            opcodeMap: MutableMap<String, Int>,
            opcodePool: List<Int>,
            opcodePoolIndex: IntArray
        ): Int {
            val old = opcodeMap[opcodeName]
            if (old != null) {
                return old
            }

            if (opcodePoolIndex[0] >= opcodePool.size) {
                throw IllegalStateException("自定义opcode数量超过255")
            }

            val vmOpcode = opcodePool[opcodePoolIndex[0]]
            opcodePoolIndex[0]++

            opcodeMap[opcodeName] = vmOpcode

            System.out.println("生成opcode映射: " + opcodeName
                + " -> 自定义opcode=0x" + String.format("%02x", vmOpcode))

            return vmOpcode
        }

        private fun isValidDexFile(file: File): Boolean {
            if (file == null || !file.isFile() || file.length() < 0x70) {
                return false
            }

            try {
                FileInputStream(file).use { `in` ->
                    val magic = ByteArray(4)
                    val read = `in`.read(magic)

                    if (read != 4) {
                        return false
                    }

                    return magic[0] == 'd'.code.toByte()
                        && magic[1] == 'e'.code.toByte()
                        && magic[2] == 'x'.code.toByte()
                        && magic[3] == '\n'.code.toByte()
                }
            } catch (e: Exception) {
                return false
            }
        }

        private fun getDexIndex(name: String): Int {
            if ("classes.dex" == name) {
                return 1
            }

            val num = name.replace("classes", "").replace(".dex", "")
            return try {
                Integer.parseInt(num)
            } catch (e: Exception) {
                Integer.MAX_VALUE
            }
        }

        private fun buildExtractInstruction(
            instruction: Instruction,
            opcodeMap: MutableMap<String, Int>,
            opcodePool: List<Int>,
            opcodePoolIndex: IntArray
        ): ExtractInstruction {
            val out = ExtractInstruction()

            out.opcodeName = instruction.opcode.name
            out.vmOpcode = getOrCreateVmOpcode(out.opcodeName!!, opcodeMap, opcodePool, opcodePoolIndex)
            out.dexlibOpcodeValue = instruction.opcode.ordinal
            out.formatName = instruction.opcode.format.toString()
            out.codeUnits = instruction.codeUnits

            if (instruction is FiveRegisterInstruction) {
                val count = instruction.registerCount
                if (count >= 1) out.registers.add(instruction.registerC)
                if (count >= 2) out.registers.add(instruction.registerD)
                if (count >= 3) out.registers.add(instruction.registerE)
                if (count >= 4) out.registers.add(instruction.registerF)
                if (count >= 5) out.registers.add(instruction.registerG)
            } else if (instruction is RegisterRangeInstruction) {
                for (i in 0 until instruction.registerCount) {
                    out.registers.add(instruction.startRegister + i)
                }
            } else if (instruction is ThreeRegisterInstruction) {
                out.registers.add(instruction.registerA)
                out.registers.add(instruction.registerB)
                out.registers.add(instruction.registerC)
            } else if (instruction is TwoRegisterInstruction) {
                out.registers.add(instruction.registerA)
                out.registers.add(instruction.registerB)
            } else if (instruction is OneRegisterInstruction) {
                out.registers.add(instruction.registerA)
            }

            if (instruction is WideLiteralInstruction) {
                out.literalType = 2
                out.literalValue = instruction.wideLiteral
            } else if (instruction is NarrowLiteralInstruction) {
                out.literalType = 1
                out.literalValue = instruction.narrowLiteral.toLong()
            }

            if (instruction is OffsetInstruction) {
                out.offsetType = 1
                out.offsetValue = instruction.codeOffset
            }

            if (instruction is ReferenceInstruction) {
                val reference = instruction.reference

                if (reference is StringReference) {
                    out.referenceType = 1
                    out.referenceData = reference.string
                } else if (reference is TypeReference) {
                    out.referenceType = 2
                    out.referenceData = reference.type
                } else if (reference is FieldReference) {
                    out.referenceType = 3
                    out.referenceData = reference.definingClass
                        + "->" + reference.name
                        + ":" + reference.type
                } else if (reference is MethodReference) {
                    out.referenceType = 4
                    out.referenceData = reference.definingClass
                        + "->" + reference.name
                        + buildMethodReferenceSignature(reference)
                } else {
                    out.referenceType = 9
                    out.referenceData = reference.toString()
                }
            }

            return out
        }

        private fun writeVmpBinary(
            outFile: File,
            opcodeMap: Map<String, Int>,
            blocks: List<ExtractMethodBlock>
        ): List<ClassIndexEntry> {
            val indexEntries = ArrayList<ClassIndexEntry>()

            RandomAccessFile(outFile, "rw").use { raf ->
                raf.setLength(0)

                writeBytes(raf, byteArrayOf('A'.code.toByte(), 'V'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte()))
                writeIntLE(raf, 3)

                writeIntLE(raf, opcodeMap.size)
                for (entry in opcodeMap) {
                    writeStringLE(raf, entry.key)
                    writeIntLE(raf, entry.value)
                }

                writeIntLE(raf, blocks.size)

                val indexTableOffset = raf.filePointer
                for (i in blocks.indices) {
                    writeIntLE(raf, 0)
                    writeLongLE(raf, 0)
                    writeIntLE(raf, 0)
                }

                for (block in blocks) {
                    val blockOffset = raf.filePointer

                    writeIntLE(raf, block.classId)
                    writeStringLE(raf, block.dexName)
                    writeStringLE(raf, block.className)

                    writeIntLE(raf, 1)

                    writeStringLE(raf, block.methodName)
                    writeStringLE(raf, block.methodSignature)
                    writeIntLE(raf, block.accessFlags)
                    writeIntLE(raf, block.registerCount)
                    writeIntLE(raf, block.paramCount)
                    writeStringLE(raf, block.returnType)
                    writeIntLE(raf, block.instructions.size)

                    for (insn in block.instructions) {
                        writeIntLE(raf, insn.vmOpcode)
                        writeStringLE(raf, insn.opcodeName)
                        writeIntLE(raf, insn.dexlibOpcodeValue)
                        writeStringLE(raf, insn.formatName)
                        writeIntLE(raf, insn.codeUnits)

                        writeIntLE(raf, insn.registers.size)
                        for (reg in insn.registers) {
                            writeIntLE(raf, reg)
                        }

                        writeIntLE(raf, insn.literalType)
                        writeLongLE(raf, insn.literalValue)

                        writeIntLE(raf, insn.offsetType)
                        writeIntLE(raf, insn.offsetValue)

                        writeIntLE(raf, insn.referenceType)
                        writeStringLE(raf, insn.referenceData)
                    }

                    val blockEnd = raf.filePointer

                    val index = ClassIndexEntry()
                    index.classId = block.classId
                    index.offset = blockOffset
                    index.size = (blockEnd - blockOffset).toInt()
                    indexEntries.add(index)

                    //System.out.println("写入数据块索引 classId=" + index.classId + " offset=" + index.offset + " size=" + index.size);
                }

                val fileEnd = raf.filePointer

                raf.seek(indexTableOffset)
                for (index in indexEntries) {
                    writeIntLE(raf, index.classId)
                    writeLongLE(raf, index.offset)
                    writeIntLE(raf, index.size)
                }

                raf.seek(fileEnd)

                System.out.println("bin索引表偏移=" + indexTableOffset)
                System.out.println("bin文件总大小=" + fileEnd)
            }

            return indexEntries
        }

        private fun writeVmpText(
            outFile: File,
            opcodeMap: Map<String, Int>,
            indexEntries: List<ClassIndexEntry>,
            blocks: List<ExtractMethodBlock>
        ) {
            PrintWriter(OutputStreamWriter(FileOutputStream(outFile), StandardCharsets.UTF_8)).use { pw ->
                pw.println("magic=AVMP")
                pw.println("version=3")
                pw.println()

                pw.println("========== 自定义opcode映射表 ==========")
                pw.println("opcodeMapCount=" + opcodeMap.size)
                for (entry in opcodeMap) {
                    pw.println(entry.key
                        + " -> 0x" + String.format("%02x", entry.value))
                }

                pw.println()
                pw.println("========== classId索引表 ==========")
                pw.println("classIndexCount=" + indexEntries.size)
                for (index in indexEntries) {
                    pw.println("classId=" + index.classId
                        + " offset=" + index.offset
                        + " size=" + index.size)
                }

                pw.println()
                pw.println("========== 方法数据块 ==========")
                pw.println("classBlockCount=" + blocks.size)
                pw.println()

                for (block in blocks) {
                    pw.println("========================================")
                    pw.println("classId=" + block.classId)
                    pw.println("dexName=" + block.dexName)
                    pw.println("className=" + block.className)
                    pw.println("methodCount=1")
                    pw.println("methodName=" + block.methodName)
                    pw.println("methodSignature=" + block.methodSignature)
                    pw.println("accessFlags=0x" + Integer.toHexString(block.accessFlags))
                    pw.println("registerCount=" + block.registerCount)
                    pw.println("paramCount=" + block.paramCount)
                    pw.println("returnType=" + block.returnType)
                    pw.println("instructionCount=" + block.instructions.size)

                    var insnIdx = 0
                    for (insn in block.instructions) {
                        pw.println()
                        pw.println("  instruction[$insnIdx]")
                        pw.println("    vmOpcode=0x" + String.format("%02x", insn.vmOpcode))
                        pw.println("    realOpcodeName=" + insn.opcodeName)
                        pw.println("    dexlibOpcodeValue=0x" + Integer.toHexString(insn.dexlibOpcodeValue))
                        pw.println("    formatName=" + insn.formatName)
                        pw.println("    codeUnits=" + insn.codeUnits)
                        pw.println("    registers=" + insn.registers)
                        pw.println("    literalType=" + insn.literalType)
                        pw.println("    literalValue=" + insn.literalValue)
                        pw.println("    offsetType=" + insn.offsetType)
                        pw.println("    offsetValue=" + insn.offsetValue)
                        pw.println("    referenceType=" + insn.referenceType)
                        pw.println("    referenceData=" + insn.referenceData)
                        insnIdx++
                    }

                    pw.println()
                }
            }
        }

        // FileOutputStream-based write helpers
        private fun writeStringLE(out: FileOutputStream, value: String?) {
            if (value == null) {
                writeIntLE(out, -1)
                return
            }

            val data = value.toByteArray(StandardCharsets.UTF_8)
            writeIntLE(out, data.size)
            writeBytes(out, data)
        }

        private fun writeIntLE(out: FileOutputStream, value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
            out.write((value shr 16) and 0xff)
            out.write((value shr 24) and 0xff)
        }

        private fun writeLongLE(out: FileOutputStream, value: Long) {
            out.write((value and 0xff).toInt())
            out.write(((value shr 8) and 0xff).toInt())
            out.write(((value shr 16) and 0xff).toInt())
            out.write(((value shr 24) and 0xff).toInt())
            out.write(((value shr 32) and 0xff).toInt())
            out.write(((value shr 40) and 0xff).toInt())
            out.write(((value shr 48) and 0xff).toInt())
            out.write(((value shr 56) and 0xff).toInt())
        }

        private fun writeBytes(out: FileOutputStream, data: ByteArray) {
            out.write(data)
        }

        // RandomAccessFile-based write helpers
        private fun writeStringLE(out: RandomAccessFile, value: String?) {
            if (value == null) {
                writeIntLE(out, -1)
                return
            }

            val data = value.toByteArray(StandardCharsets.UTF_8)
            writeIntLE(out, data.size)
            writeBytes(out, data)
        }

        private fun writeIntLE(out: RandomAccessFile, value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
            out.write((value shr 16) and 0xff)
            out.write((value shr 24) and 0xff)
        }

        private fun writeLongLE(out: RandomAccessFile, value: Long) {
            out.write((value and 0xff).toInt())
            out.write(((value shr 8) and 0xff).toInt())
            out.write(((value shr 16) and 0xff).toInt())
            out.write(((value shr 24) and 0xff).toInt())
            out.write(((value shr 32) and 0xff).toInt())
            out.write(((value shr 40) and 0xff).toInt())
            out.write(((value shr 48) and 0xff).toInt())
            out.write(((value shr 56) and 0xff).toInt())
        }

        private fun writeBytes(out: RandomAccessFile, data: ByteArray) {
            out.write(data)
        }

        // RandomAccessFile-based read helpers
        private fun readIntLE(`in`: RandomAccessFile): Int {
            val b0 = `in`.read()
            val b1 = `in`.read()
            val b2 = `in`.read()
            val b3 = `in`.read()

            if ((b0 or b1 or b2 or b3) < 0) {
                throw IOException("读取int失败，文件长度不足")
            }

            return (b0 and 0xff)
                or ((b1 and 0xff) shl 8)
                or ((b2 and 0xff) shl 16)
                or ((b3 and 0xff) shl 24)
        }

        private fun readLongLE(`in`: RandomAccessFile): Long {
            val b0 = `in`.read().toLong()
            val b1 = `in`.read().toLong()
            val b2 = `in`.read().toLong()
            val b3 = `in`.read().toLong()
            val b4 = `in`.read().toLong()
            val b5 = `in`.read().toLong()
            val b6 = `in`.read().toLong()
            val b7 = `in`.read().toLong()

            if ((b0 or b1 or b2 or b3 or b4 or b5 or b6 or b7) < 0) {
                throw IOException("读取long失败，文件长度不足")
            }

            return (b0 and 0xff)
                or ((b1 and 0xff) shl 8)
                or ((b2 and 0xff) shl 16)
                or ((b3 and 0xff) shl 24)
                or ((b4 and 0xff) shl 32)
                or ((b5 and 0xff) shl 40)
                or ((b6 and 0xff) shl 48)
                or ((b7 and 0xff) shl 56)
        }

        private fun readStringLE(`in`: RandomAccessFile): String? {
            val len = readIntLE(`in`)

            if (len == -1) {
                return null
            }

            if (len < 0) {
                throw IOException("字符串长度非法：" + len)
            }

            val data = ByteArray(len)
            readFully(`in`, data)

            return String(data, StandardCharsets.UTF_8)
        }

        private fun readFully(`in`: RandomAccessFile, data: ByteArray) {
            var offset = 0

            while (offset < data.size) {
                val read = `in`.read(data, offset, data.size - offset)
                if (read == -1) {
                    throw IOException("读取文件失败，文件长度不足")
                }
                offset += read
            }
        }

        // FileInputStream-based read helpers
        private fun readIntLE(`in`: FileInputStream): Int {
            val b0 = `in`.read()
            val b1 = `in`.read()
            val b2 = `in`.read()
            val b3 = `in`.read()

            if ((b0 or b1 or b2 or b3) < 0) {
                throw IOException("读取int失败，文件长度不足")
            }

            return (b0 and 0xff)
                or ((b1 and 0xff) shl 8)
                or ((b2 and 0xff) shl 16)
                or ((b3 and 0xff) shl 24)
        }

        private fun readLongLE(`in`: FileInputStream): Long {
            val b0 = `in`.read().toLong()
            val b1 = `in`.read().toLong()
            val b2 = `in`.read().toLong()
            val b3 = `in`.read().toLong()
            val b4 = `in`.read().toLong()
            val b5 = `in`.read().toLong()
            val b6 = `in`.read().toLong()
            val b7 = `in`.read().toLong()

            if ((b0 or b1 or b2 or b3 or b4 or b5 or b6 or b7) < 0) {
                throw IOException("读取long失败，文件长度不足")
            }

            return (b0 and 0xff)
                or ((b1 and 0xff) shl 8)
                or ((b2 and 0xff) shl 16)
                or ((b3 and 0xff) shl 24)
                or ((b4 and 0xff) shl 32)
                or ((b5 and 0xff) shl 40)
                or ((b6 and 0xff) shl 48)
                or ((b7 and 0xff) shl 56)
        }

        private fun readStringLE(`in`: FileInputStream): String? {
            val len = readIntLE(`in`)

            if (len == -1) {
                return null
            }

            if (len < 0) {
                throw IOException("字符串长度非法：" + len)
            }

            val data = ByteArray(len)
            readFully(`in`, data)

            return String(data, StandardCharsets.UTF_8)
        }

        private fun readFully(`in`: FileInputStream, data: ByteArray) {
            var offset = 0

            while (offset < data.size) {
                val read = `in`.read(data, offset, data.size - offset)
                if (read == -1) {
                    throw IOException("读取文件失败，文件长度不足")
                }
                offset += read
            }
        }

        /**
         * :TODO
         * 解析bin文件以校验读取是否正常
         * */
        @JvmStatic
        fun parseVmpBinByClassId(binFile: File, targetClassId: Int) {
            if (binFile == null || !binFile.isFile()) {
                throw IOException("bin文件不存在")
            }

            val startNs = System.nanoTime()

            RandomAccessFile(binFile, "r").use { raf ->
                val magic = ByteArray(4)
                readFully(raf, magic)

                val magicText = String(magic, StandardCharsets.UTF_8)
                System.out.println("magic=" + magicText)

                if ("AVMP" != magicText) {
                    throw IOException("bin格式错误，magic不匹配")
                }

                val version = readIntLE(raf)
                System.out.println("version=" + version)

                if (version != 3) {
                    throw IOException("当前解析器只支持version=3，当前version=" + version)
                }

                val opcodeMapCount = readIntLE(raf)
                System.out.println("opcodeMapCount=" + opcodeMapCount)

                val vmOpcodeToRealName = LinkedHashMap<Int, String>()

                System.out.println("========== 解析opcode映射表 ==========")
                for (i in 0 until opcodeMapCount) {
                    val realOpcodeName = readStringLE(raf)
                    val vmOpcode = readIntLE(raf)

                    vmOpcodeToRealName[vmOpcode] = realOpcodeName!!

                    System.out.println("map[$i] 自定义opcode=0x"
                        + String.format("%02x", vmOpcode)
                        + " -> 真实指令=" + realOpcodeName)
                }

                val classIndexCount = readIntLE(raf)
                val indexTableOffset = raf.filePointer

                System.out.println("========== 解析classId索引表 ==========")
                System.out.println("classIndexCount=" + classIndexCount)
                System.out.println("indexTableOffset=" + indexTableOffset)

                var targetIndex: ClassIndexEntry? = null

                for (i in 0 until classIndexCount) {
                    val index = ClassIndexEntry()
                    index.classId = readIntLE(raf)
                    index.offset = readLongLE(raf)
                    index.size = readIntLE(raf)

                    System.out.println("index[$i] classId=" + index.classId
                        + " offset=" + index.offset
                        + " size=" + index.size)

                    if (index.classId == targetClassId) {
                        targetIndex = index
                    }
                }

                if (targetIndex == null) {
                    val endNs = System.nanoTime()
                    System.out.println("未找到目标classId：" + targetClassId)
                    System.out.println("解析耗时=" + ((endNs - startNs) / 1000000.0) + " ms")
                    return
                }

                System.out.println("========================================")
                System.out.println("命中目标索引")
                System.out.println("targetClassId=" + targetClassId)
                System.out.println("blockOffset=" + targetIndex.offset)
                System.out.println("blockSize=" + targetIndex.size)
                System.out.println("开始seek到数据块地址：" + targetIndex.offset)

                raf.seek(targetIndex.offset)

                val blockStartPos = raf.filePointer

                val classId = readIntLE(raf)
                val dexName = readStringLE(raf)
                val className = readStringLE(raf)
                val methodCount = readIntLE(raf)

                System.out.println("========================================")
                System.out.println("开始解析目标数据块")
                System.out.println("blockStartPos=" + blockStartPos)
                System.out.println("classId=" + classId)
                System.out.println("dexName=" + dexName)
                System.out.println("className=" + className)
                System.out.println("methodCount=" + methodCount)

                for (methodIndex in 0 until methodCount) {
                    val methodName = readStringLE(raf)
                    val methodSignature = readStringLE(raf)
                    val accessFlags = readIntLE(raf)
                    val registerCount = readIntLE(raf)
                    val paramCount = readIntLE(raf)
                    val returnType = readStringLE(raf)
                    val instructionCount = readIntLE(raf)

                    /*System.out.println("----------------------------------------");
                    System.out.println("methodIndex=" + methodIndex);
                    System.out.println("methodName=" + methodName);
                    System.out.println("methodSignature=" + methodSignature);
                    System.out.println("accessFlags=0x" + Integer.toHexString(accessFlags));
                    System.out.println("registerCount=" + registerCount);
                    System.out.println("paramCount=" + paramCount);
                    System.out.println("returnType=" + returnType);
                    System.out.println("instructionCount=" + instructionCount);*/

                    for (insnIndex in 0 until instructionCount) {
                        val insnOffset = raf.filePointer

                        val vmOpcode = readIntLE(raf)
                        val opcodeNameInInsn = readStringLE(raf)
                        val dexlibOpcodeValue = readIntLE(raf)
                        val formatName = readStringLE(raf)
                        val codeUnits = readIntLE(raf)

                        val regCount = readIntLE(raf)
                        val registers = ArrayList<Int>()
                        for (i in 0 until regCount) {
                            registers.add(readIntLE(raf))
                        }

                        val literalType = readIntLE(raf)
                        val literalValue = readLongLE(raf)

                        val offsetType = readIntLE(raf)
                        val offsetValue = readIntLE(raf)

                        val referenceType = readIntLE(raf)
                        val referenceData = readStringLE(raf)

                        val realOpcodeName = vmOpcodeToRealName[vmOpcode]

                        /*System.out.println();
                        System.out.println("  instruction[" + insnIndex + "]");
                        System.out.println("    insnOffset=" + insnOffset);
                        System.out.println("    vmOpcode=0x" + String.format("%02x", vmOpcode));
                        System.out.println("    mapRealOpcodeName=" + realOpcodeName);
                        System.out.println("    insnRealOpcodeName=" + opcodeNameInInsn);
                        System.out.println("    dexlibOpcodeValue=0x" + Integer.toHexString(dexlibOpcodeValue));
                        System.out.println("    formatName=" + formatName);
                        System.out.println("    codeUnits=" + codeUnits);
                        System.out.println("    registerCount=" + regCount);
                        System.out.println("    registers=" + registers);
                        System.out.println("    literalType=" + literalType);
                        System.out.println("    literalValue=" + literalValue);
                        System.out.println("    offsetType=" + offsetType);
                        System.out.println("    offsetValue=" + offsetValue);
                        System.out.println("    referenceType=" + referenceType);
                        System.out.println("    referenceData=" + referenceData);*/
                    }
                }

                val blockEndPos = raf.filePointer
                val parsedSize = blockEndPos - blockStartPos

                System.out.println("========================================")
                System.out.println("目标classId解析完成：" + targetClassId)
                System.out.println("blockEndPos=" + blockEndPos)
                System.out.println("parsedBlockSize=" + parsedSize)
                System.out.println("indexBlockSize=" + targetIndex.size)

                if (parsedSize != targetIndex.size.toLong()) {
                    System.out.println("警告：解析出来的数据块大小和索引表记录不一致")
                }

                val endNs = System.nanoTime()
                System.out.println("解析耗时=" + ((endNs - startNs) / 1000000.0) + " ms")
            }
        }

        private fun buildExtractedMethodKey(className: String, methodName: String, methodSignature: String): String {
            return className + "->" + methodName + methodSignature
        }

        private fun recordExtractedMethod(block: ExtractMethodBlock) {
            if (block == null) {
                return
            }

            val info = ExtractedMethodInfo()
            info.classId = block.classId
            info.dexName = block.dexName
            info.className = block.className
            info.methodName = block.methodName
            info.methodSignature = block.methodSignature
            info.accessFlags = block.accessFlags
            info.registerCount = block.registerCount
            info.paramCount = block.paramCount
            info.returnType = block.returnType

            val key = buildExtractedMethodKey(
                block.className!!,
                block.methodName!!,
                block.methodSignature!!
            )

            EXTRACTED_METHOD_MAP[key] = info

            System.out.println("记录待native重写方法 key=" + key
                + " classId=" + block.classId
                + " accessFlags=0x" + Integer.toHexString(block.accessFlags)
                + " returnType=" + block.returnType)
        }

        private fun getExtractedMethodInfo(
            className: String,
            methodName: String,
            methodSignature: String
        ): ExtractedMethodInfo? {
            val key = buildExtractedMethodKey(className, methodName, methodSignature)
            return EXTRACTED_METHOD_MAP[key]
        }

        /**
         * :TODO
         * 重组dex
         * */
        @JvmStatic
        fun rewriteExtractedMethodsToNativeDex(
            dexDir: File,
            soName: String,
            vmpPackageName: String
        ) {
            if (dexDir == null || !dexDir.isDirectory()) {
                throw IOException("dex目录不存在")
            }

            if (EXTRACTED_METHOD_MAP.isEmpty()) {
                throw IOException("EXTRACTED_METHOD_MAP为空，请先执行方法抽取")
            }

            val vmpClassType = "L" + vmpPackageName.replace('.', '/') + "/VMP;"

            var outDexIndex = 1
            var currentMethodCount = 0
            var currentClasses = ArrayList<ClassDef>()

            val shellDex = createVmpShellDex(soName, vmpPackageName)
            for (shellClass in shellDex.classes) {
                currentClasses.add(shellClass)
                currentMethodCount += countClassMethods(shellClass)
            }

            var inDexIndex = 1

            while (true) {
                val dexName = if (inDexIndex == 1) "classes.dex" else "classes${inDexIndex}.dex"
                val dexFile = File(dexDir, dexName)

                if (!dexFile.isFile()) {
                    System.out.println("dex编号断开，停止重写：" + dexName)
                    break
                }

                if (!isValidDexFile(dexFile)) {
                    System.out.println("跳过非法dex文件：" + dexFile.absolutePath)
                    break
                }

                val dex: DexBackedDexFile
                try {
                    dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
                } catch (e: Throwable) {
                    System.out.println("解析dex失败，停止重写：" + dexFile.name)
                    System.out.println("失败原因：" + e.message)
                    break
                }

                System.out.println("开始重写dex：" + dexFile.name)

                for (classDef in dex.classes) {
                    val newClass = rewriteClassForNative(classDef, vmpClassType)
                    val addMethodCount = countClassMethods(newClass)

                    if (currentClasses.isNotEmpty() && currentMethodCount + addMethodCount > 60000) {
                        writeCombinedDex(dexDir, outDexIndex, currentClasses)
                        outDexIndex++

                        currentClasses = ArrayList()
                        currentMethodCount = 0
                    }

                    currentClasses.add(newClass)
                    currentMethodCount += addMethodCount
                }

                inDexIndex++
            }

            if (currentClasses.isNotEmpty()) {
                writeCombinedDex(dexDir, outDexIndex, currentClasses)
            }

            System.out.println("native重写完成，输出dex数量=" + outDexIndex)
        }

        private fun rewriteClassForNative(classDef: ClassDef, vmpClassType: String): ClassDef {
            val isInterface = (classDef.accessFlags and AccessFlags.INTERFACE.getValue()) != 0

            if (isInterface) {
                System.out.println("接口类原样复制：" + classDef.type)
                return classDef
            }

            val newMethods = ArrayList<Method>()
            val needInitList = ArrayList<ExtractedMethodInfo>()

            var hasClinit = false

            for (method in classDef.methods) {
                val signature = buildMethodSignature(method)
                val info = getExtractedMethodInfo(
                    classDef.type,
                    method.name,
                    signature
                )

                if ("<clinit>" == method.name) {
                    hasClinit = true
                    newMethods.add(method)
                    continue
                }

                if (info != null) {
                    val nativeMethod = ImmutableMethod(
                        method.definingClass,
                        method.name,
                        method.parameters,
                        method.returnType,
                        (method.accessFlags or AccessFlags.NATIVE.getValue()),
                        method.annotations,
                        null,
                        null
                    )

                    newMethods.add(nativeMethod)
                    needInitList.add(info)

                    System.out.println("方法改为native："
                        + classDef.type
                        + "->"
                        + method.name
                        + signature
                        + " classId=" + info.classId)
                } else {
                    newMethods.add(method)
                }
            }

            if (needInitList.isNotEmpty()) {
                val finalMethods = ArrayList<Method>()

                for (method in newMethods) {
                    if ("<clinit>" == method.name) {
                        finalMethods.add(rebuildClinitWithVmpInit(
                            classDef.type,
                            method,
                            needInitList,
                            vmpClassType
                        ))
                    } else {
                        finalMethods.add(method)
                    }
                }

                if (!hasClinit) {
                    finalMethods.add(createClinitWithVmpInit(
                        classDef.type,
                        needInitList,
                        vmpClassType
                    ))
                }

                // Note: newMethods reference is replaced below via the final return statement
                return ImmutableClassDef(
                    classDef.type,
                    classDef.accessFlags,
                    classDef.superclass,
                    classDef.interfaces,
                    classDef.sourceFile,
                    classDef.annotations,
                    classDef.fields,
                    finalMethods
                )
            }

            return ImmutableClassDef(
                classDef.type,
                classDef.accessFlags,
                classDef.superclass,
                classDef.interfaces,
                classDef.sourceFile,
                classDef.annotations,
                classDef.fields,
                newMethods
            )
        }

        private fun createClinitWithVmpInit(
            classType: String,
            infos: List<ExtractedMethodInfo>,
            vmpClassType: String
        ): Method {
            val instructions = ArrayList<Instruction>()
            appendVmpInitInstructions(instructions, infos, classType, vmpClassType)
            instructions.add(ImmutableInstruction10x(Opcode.RETURN_VOID))

            val impl = ImmutableMethodImplementation(
                2,
                instructions,
                null,
                null
            )

            return ImmutableMethod(
                classType,
                "<clinit>",
                Collections.emptyList(),
                "V",
                AccessFlags.STATIC.getValue() or AccessFlags.CONSTRUCTOR.getValue(),
                null,
                null,
                impl
            )
        }

        private fun rebuildClinitWithVmpInit(
            classType: String,
            oldClinit: Method,
            infos: List<ExtractedMethodInfo>,
            vmpClassType: String
        ): Method {
            val oldImpl = oldClinit.implementation

            val instructions = ArrayList<Instruction>()
            var registerCount = 2

            if (oldImpl != null) {
                registerCount = Math.max(oldImpl.registerCount, 2)

                for (instruction in oldImpl.instructions) {
                    if (instruction.opcode == Opcode.RETURN_VOID) {
                        continue
                    }
                    instructions.add(instruction)
                }
            }

            appendVmpInitInstructions(instructions, infos, classType, vmpClassType)
            instructions.add(ImmutableInstruction10x(Opcode.RETURN_VOID))

            val impl = ImmutableMethodImplementation(
                registerCount,
                instructions,
                oldImpl?.tryBlocks,
                oldImpl?.debugItems
            )

            return ImmutableMethod(
                classType,
                "<clinit>",
                Collections.emptyList(),
                "V",
                oldClinit.accessFlags,
                oldClinit.annotations,
                null,
                impl
            )
        }

        private fun appendVmpInitInstructions(
            instructions: MutableList<Instruction>,
            infos: List<ExtractedMethodInfo>,
            currentClassType: String,
            vmpClassType: String
        ) {
            for (info in infos) {
                instructions.add(
                    ImmutableInstruction31i(
                        Opcode.CONST,
                        0,
                        info.classId
                    )
                )

                instructions.add(
                    ImmutableInstruction21c(
                        Opcode.CONST_CLASS,
                        1,
                        ImmutableTypeReference(currentClassType)
                    )
                )

                instructions.add(
                    ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC,
                        2, 0, 1, 0, 0, 0,
                        ImmutableMethodReference(
                            vmpClassType,
                            "init",
                            Arrays.asList("I", "Ljava/lang/Class;"),
                            "V"
                        )
                    )
                )

                System.out.println("插入VMP.init classId=" + info.classId
                    + " class=" + currentClassType)
            }
        }

        private fun writeCombinedDex(
            dexDir: File,
            outDexIndex: Int,
            classes: List<ClassDef>
        ) {
            val outName = if (outDexIndex == 1) "classes_c.dex" else "classes${outDexIndex}_c.dex"
            val outFile = File(dexDir, outName)

            val outDex = ImmutableDexFile(
                Opcodes.getDefault(),
                classes
            )

            DexFileFactory.writeDexFile(outFile.absolutePath, outDex)

            System.out.println("写出重组dex：" + outFile.absolutePath
                + " classCount=" + classes.size)
        }

        private fun countClassMethods(classDef: ClassDef): Int {
            var count = 0
            for (ignored in classDef.methods) {
                count++
            }
            return count
        }
    }
}
