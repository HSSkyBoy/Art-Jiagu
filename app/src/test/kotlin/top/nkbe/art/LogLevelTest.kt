package top.nkbe.art

import org.junit.Assert.assertEquals
import org.junit.Test

class LogLevelTest {
    @Test
    fun `plain lifecycle messages are tagged as info`() {
        assertEquals(LogLevel.INFO, LogLevel.infer("开始处理 APK"))
        assertEquals("[INFO] 开始处理 APK", LogLevel.format("开始处理 APK"))
    }

    @Test
    fun `recoverable fallbacks are tagged as warnings`() {
        assertEquals(LogLevel.WARN, LogLevel.infer("读取设置失败，使用默认名称"))
        assertEquals(LogLevel.WARN, LogLevel.infer("未开启自动签名，跳过签名"))
    }

    @Test
    fun `processing failures are tagged as errors`() {
        assertEquals(LogLevel.ERROR, LogLevel.infer("处理失败：Invalid opcode"))
        assertEquals("[ERROR] 处理失败：Invalid opcode", LogLevel.format("处理失败：Invalid opcode"))
    }

    @Test
    fun `existing level prefix is not duplicated`() {
        assertEquals("[WARN] 已跳过", LogLevel.format("[WARN] 已跳过"))
    }
}
