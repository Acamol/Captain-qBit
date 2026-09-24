package dev.yashgarg.qbit.validation

class StringValidator : TextValidator {
    override fun isValid(text: String): Boolean {
        // Blank rather than empty: every field validated here is trimmed before it is persisted,
        // so whitespace-only input would otherwise pass validation and save as "".
        return text.isNotBlank()
    }
}
