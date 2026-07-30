package top.nkbe.art

import com.android.tools.smali.dexlib2.Format
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22b
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import org.junit.Assert.assertEquals
import org.junit.Test

class DexInstructionFormatTest {
    @Test
    fun `lit8 arithmetic uses format 22b`() {
        listOf(
            Opcode.MUL_INT_LIT8,
            Opcode.ADD_INT_LIT8,
            Opcode.REM_INT_LIT8,
        ).forEach { opcode ->
            val instruction = ImmutableInstruction22b(opcode, 1, 0, 7)
            assertEquals(Format.Format22b, instruction.opcode.format)
        }
    }

    @Test
    fun `return with a value uses format 11x`() {
        val instruction = ImmutableInstruction11x(Opcode.RETURN, 0)
        assertEquals(Format.Format11x, instruction.opcode.format)
    }
}
