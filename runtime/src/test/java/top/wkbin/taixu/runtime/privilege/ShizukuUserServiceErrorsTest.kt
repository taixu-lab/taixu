package top.wkbin.taixu.runtime.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuUserServiceErrorsTest {
    @Test fun startTimeoutNamesUserServiceAndStaysRecognizableAsTimeout() {
        val message = shizukuUserServiceStartTimeoutMessage(20_000L)
        assertEquals("Shizuku UserService 启动超时（20000ms）", message)
        assertTrue(message.contains("超时"))
    }
}
