package dev.yashgarg.qbit.ui.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkTagEditsTest {
    @Test
    fun `only explicit operations survive a poll and a reordered list of names`() {
        val assigned = setOf("remove", "untouched")
        val edits = BulkTagEdits().toggle("remove", assigned).toggle("add", assigned)
        val liveAssigned = setOf("remove", "new assignment")
        val liveNames = listOf("new assignment", "add", "remove", "untouched")

        assertTrue(edits.checked("new assignment", liveAssigned))
        assertFalse(edits.checked("untouched", liveAssigned))
        assertTrue(edits.checked("add", liveAssigned))
        assertFalse(edits.checked("remove", liveAssigned))
        assertEquals(listOf("add"), edits.additions(liveNames))
        assertEquals(listOf("remove"), edits.removals(liveNames))
        assertTrue(edits.removals(liveNames - "remove").isEmpty())
    }

    @Test
    fun `undoing a toggle leaves future remote changes alone`() {
        val edits = BulkTagEdits().toggle("tag", emptySet()).toggle("tag", emptySet())
        assertTrue(edits.checked("tag", setOf("tag")))
        assertTrue(edits.additions(listOf("tag")).isEmpty())
        assertTrue(edits.removals(listOf("tag")).isEmpty())
    }
}
