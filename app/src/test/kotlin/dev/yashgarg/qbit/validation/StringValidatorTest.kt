package dev.yashgarg.qbit.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringValidatorTest {
    private val stringValidator = StringValidator()

    @Test
    fun `text is valid`() {
        assertTrue(stringValidator.isValid("admin"))
    }

    @Test
    fun `empty text is invalid`() {
        assertFalse(stringValidator.isValid(""))
    }

    @Test
    fun `whitespace-only text is invalid`() {
        assertFalse(stringValidator.isValid(" "))
        assertFalse(stringValidator.isValid("   \t"))
    }

    @Test
    fun `surrounding whitespace does not make text invalid`() {
        assertTrue(stringValidator.isValid("  admin  "))
    }
}
