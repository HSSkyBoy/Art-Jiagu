package top.nkbe.art

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31i
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.dexlib2.DexFileFactory
import java.io.File
import java.io.DataOutputStream
import java.io.FileOutputStream

/** Rewrites conservative business-string candidates to a shell-native decoder call. */
object StringEncryptionRewriter {
    data class Result(val rewrittenStrings: Int, val encryptedStringPool: ByteArray)

    fun rewrite(inputDex: File, outputDex: File, stubClassName: String): Result {
        val decoderType = "L${stubClassName.replace('.', '/')};"
        val decoder = ImmutableMethodReference(
            decoderType,
            "decodeString",
            listOf("I"),
            "Ljava/lang/String;",
        )
        var rewrittenStrings = 0
        val stringIndexes = LinkedHashMap<String, Int>()
        val input = DexFileFactory.loadDexFile(inputDex, Opcodes.getDefault())
        val pool = DexPool(Opcodes.getDefault())
        input.classes.forEach { classDef ->
            val rewritten = rewriteClass(classDef, decoder) { value ->
                rewrittenStrings++
                stringIndexes.getOrPut(value) { stringIndexes.size }
            }
            pool.internClass(rewritten)
        }
        outputDex.parentFile?.mkdirs()
        pool.writeTo(FileDataStore(outputDex))
        return Result(rewrittenStrings, buildEncryptedStringPool(stringIndexes.keys.toList()))
    }

    private fun rewriteClass(
        classDef: ClassDef,
        decoder: ImmutableMethodReference,
        onRewrite: (String) -> Int,
    ): ClassDef = ImmutableClassDef(
        classDef.type,
        classDef.accessFlags,
        classDef.superclass,
        classDef.interfaces,
        classDef.sourceFile,
        classDef.annotations,
        classDef.fields,
        classDef.methods.map { rewriteMethod(it, decoder, onRewrite) },
    )

    private fun rewriteMethod(
        method: Method,
        decoder: ImmutableMethodReference,
        onRewrite: (String) -> Int,
    ): Method {
        val implementation = method.implementation ?: return method
        val mutable = MutableMethodImplementation(implementation)
        for (index in mutable.instructions.indices.reversed()) {
            val instruction = mutable.instructions[index]
            if (instruction.opcode !in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO)) continue
            val reference = (instruction as? ReferenceInstruction)?.reference as? StringReference ?: continue
            val register = (instruction as? OneRegisterInstruction)?.registerA ?: continue
            val value = reference.string
            if (!isCandidate(value)) continue

            val stringIndex = onRewrite(value)
            mutable.replaceInstruction(index, BuilderInstruction31i(Opcode.CONST, register, stringIndex))
            mutable.addInstruction(index + 1, BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE, register, 1, decoder))
            mutable.addInstruction(index + 2, BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, register))
        }
        return ImmutableMethod(
            method.definingClass,
            method.name,
            method.parameters,
            method.returnType,
            method.accessFlags,
            method.annotations,
            method.hiddenApiRestrictions,
            mutable,
        )
    }

    private fun isCandidate(value: String): Boolean {
        if (value.length < 4 || value.length > 512 || value.any { it == '\u0000' }) return false
        if (value.startsWith("L") && value.endsWith(";")) return false // type descriptor
        if (value.startsWith("(") || value.contains("->") || value.startsWith("[")) return false // signature/member
        if (value.matches(Regex("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*){1,}"))) return false // class name
        if (value.startsWith("lib") && value.endsWith(".so")) return false // System.loadLibrary target
        return true
    }

    fun writeEncryptedStringPool(output: File, pool: ByteArray) {
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { it.write(pool) }
    }

    private fun buildEncryptedStringPool(strings: List<String>): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x41535452) // ASTR
            data.writeInt(1)
            data.writeInt(strings.size)
            strings.forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                data.writeInt(bytes.size)
                bytes.forEachIndexed { index, byte ->
                    data.writeByte((byte.toInt() and 0xff) xor ((0xa7 + index * 31) and 0xff))
                }
            }
        }
        return output.toByteArray()
    }
}
