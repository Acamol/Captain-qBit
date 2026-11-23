package dev.yashgarg.qbit.data

import dev.yashgarg.qbit.data.models.OwnAdds
import dev.yashgarg.qbit.data.models.OwnAdds.pruned
import dev.yashgarg.qbit.data.models.OwnAdds.registered
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnAddsTest {
    @Test
    fun staleEntriesAreExpired() {
        val map = emptyMap<String, Long>().registered("1|a", now = 0L)
        assertTrue("1|a" in map.pruned(now = OwnAdds.TTL_MS - 1))
        assertFalse("1|a" in map.pruned(now = OwnAdds.TTL_MS))
    }

    @Test
    fun registeringDropsExpiredAndCapsSize() {
        var map = emptyMap<String, Long>().registered("1|old", now = 0L)
        repeat(OwnAdds.MAX_ENTRIES + 10) {
            map = map.registered("1|h$it", now = OwnAdds.TTL_MS + it)
        }
        assertFalse("1|old" in map)
        assertEquals(OwnAdds.MAX_ENTRIES, map.size)
        // Oldest evicted first, newest kept.
        assertFalse("1|h0" in map)
        assertTrue("1|h${OwnAdds.MAX_ENTRIES + 9}" in map)
    }

    @Test
    fun keyIsCaseInsensitiveOnHash() {
        assertEquals(OwnAdds.key(2, "ABC"), OwnAdds.key(2, "abc"))
    }
}
