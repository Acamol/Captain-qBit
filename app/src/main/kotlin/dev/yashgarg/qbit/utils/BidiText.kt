package dev.yashgarg.qbit.utils

// Written as escapes rather than the characters themselves: they are invisible in an editor, and
// lint's BidiSpoofing check rejects literal bidi controls in source.
private const val LEFT_TO_RIGHT_ISOLATE = "\u2066"
private const val POP_DIRECTIONAL_ISOLATE = "\u2069"

/**
 * Marks this string as a self-contained left-to-right run.
 *
 * Values made of digits and Latin abbreviations - sizes, speeds, dates - are logically LTR.
 * Embedded in an RTL sentence (typically through a `%s` in a translated template), the bidi
 * algorithm has to resolve the neutral characters around them from context, so a colon, full stop
 * or hyphen next to the value can end up on the wrong side of it. Isolating the value resolves
 * those neutrals against the surrounding text instead, leaving them where the translation put them.
 *
 * Equivalent to `android.text.BidiFormatter.unicodeWrap()`, in plain Kotlin so it also works in JVM
 * unit tests without Robolectric.
 */
internal fun String.isolateLtr(): String = "$LEFT_TO_RIGHT_ISOLATE$this$POP_DIRECTIONAL_ISOLATE"
