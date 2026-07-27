package dev.yashgarg.qbit.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashHandlerTest {

    @Test
    fun `redacts a full url embedded in an exception message`() {
        val text =
            """java.lang.IllegalArgumentException: invalid baseUrl, got: "https://192.168.1.50:8080""""

        assertEquals(
            """java.lang.IllegalArgumentException: invalid baseUrl, got: "[redacted-url]"""",
            CrashHandler.redact(text),
        )
    }

    @Test
    fun `redacts a bare ip with no scheme`() {
        val text = "Failed to connect to /192.168.1.50:8080"

        assertEquals("Failed to connect to /[redacted-ip]:8080", CrashHandler.redact(text))
    }

    @Test
    fun `leaves text without a host untouched`() {
        val text = "java.lang.NullPointerException: torrent was null"

        assertEquals(text, CrashHandler.redact(text))
    }
}
