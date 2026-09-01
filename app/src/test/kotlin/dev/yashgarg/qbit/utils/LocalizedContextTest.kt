package dev.yashgarg.qbit.utils

import android.content.Context
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class LocalizedContextTest {
    @Test
    fun `returns the base context when the locale cannot be resolved`() {
        // On the JVM the AppCompatDelegate statics behind this are unavailable, which stands in for
        // any failure to build a localised wrapper: callers are about to show a message and must
        // get a usable context back rather than an exception. NumberFormatTest relies on this.
        val base = mock<Context>()
        assertSame(base, LocalizedContext.of(base))
    }
}
