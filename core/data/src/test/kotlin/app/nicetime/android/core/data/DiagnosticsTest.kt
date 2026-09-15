package app.nicetime.android.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun `credentials and signed query values are redacted`() {
        val output = redactSensitiveText(
            "GET https://media.test/v.m3u8?token=top-secret&quality=auto Authorization: Bearer abc.def",
        )

        assertFalse(output.contains("top-secret"))
        assertFalse(output.contains("abc.def"))
        assertTrue(output.contains("token=[REDACTED]"))
        assertTrue(output.contains("Bearer [REDACTED]"))
    }

    @Test
    fun `diagnostic log remains bounded`() {
        val log = DiagnosticLog(capacity = 2)
        log.record("one")
        log.record("two")
        log.record("three")

        assertFalse(log.export().contains("one"))
        assertTrue(log.export().contains("two"))
        assertTrue(log.export().contains("three"))
    }
}
