package dev.yashgarg.qbit.ui.server

/** Only user overrides are staged. Untouched checkboxes always follow live assignments. */
internal data class BulkTagEdits(private val changes: Map<String, Boolean> = emptyMap()) {
    fun checked(name: String, assigned: Set<String>) = changes[name] ?: (name in assigned)

    fun toggle(name: String, assigned: Set<String>): BulkTagEdits {
        val desired = !checked(name, assigned)
        return BulkTagEdits(
            if (desired == (name in assigned)) changes - name else changes + (name to desired),
        )
    }

    fun additions(available: List<String>) = available.filter { changes[it] == true }

    fun removals(available: List<String>) = available.filter { changes[it] == false }
}
