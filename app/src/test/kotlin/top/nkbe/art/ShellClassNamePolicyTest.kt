package top.nkbe.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellClassNamePolicyTest {
    @Test
    fun `old art class names migrate to independent shell namespace`() {
        assertEquals(
            ShellClassNamePolicy.DEFAULT_CLASS_NAME,
            ShellClassNamePolicy.normalize("top.nkbe.art.StubApp"),
        )
    }

    @Test
    fun `nkbe class name is preserved`() {
        val className = "top.nkbe.safe.StubApp"
        assertEquals(className, ShellClassNamePolicy.normalize(className))
        assertFalse(ShellClassNamePolicy.containsArt(className))
    }

    @Test
    fun `art check is case insensitive`() {
        assertTrue(ShellClassNamePolicy.containsArt("demo.ART.StubApp"))
    }
}
