package dev.yashgarg.qbit.ui.server

import android.app.AlertDialog
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR

/** Bulk-action pickers and tag/category/sort management dialogs shown from [ServerFragment]. */
class ServerActionDialogs(private val fragment: Fragment, private val viewModel: ServerViewModel) {
    private fun requireContext() = fragment.requireContext()

    fun showBulkCategoryPicker(hashes: List<String>) {
        val state = viewModel.uiState.value
        val options = listOf("") + state.availableCategories
        val labels = options.map { it.ifBlank { "None" } }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set category")
            .setSingleChoiceItems(labels, -1) { dialog, which ->
                viewModel.bulkSetCategory(hashes, options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showBulkTagsPicker(hashes: List<String>) {
        val state = viewModel.uiState.value
        val tags = state.availableTags
        if (tags.isEmpty()) {
            showCreateTagForSelectionDialog(hashes)
            return
        }
        val torrentTags =
            hashes.mapNotNull { state.data?.torrents?.get(it) }.flatMap { it.tags }.toSet()
        val initialChecked = BooleanArray(tags.size) { torrentTags.contains(tags[it]) }
        val checked = initialChecked.copyOf()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set tags")
            .setMultiChoiceItems(tags.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                val toAdd = tags.filterIndexed { i, _ -> checked[i] && !initialChecked[i] }
                val toRemove = tags.filterIndexed { i, _ -> !checked[i] && initialChecked[i] }
                if (toAdd.isNotEmpty()) viewModel.bulkAddTags(hashes, toAdd)
                if (toRemove.isNotEmpty()) viewModel.bulkRemoveTags(hashes, toRemove)
            }
            .setNeutralButton("New tag…") { _, _ -> showCreateTagForSelectionDialog(hashes) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateTagForSelectionDialog(hashes: List<String>) {
        val view = fragment.layoutInflater.inflate(R.layout.dialog_text_input, null, false)
        val til = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val tiet = view.findViewById<TextInputEditText>(R.id.text_input_edit)
        til?.hint = "Tag name"

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("New tag")
                .setView(view)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = tiet?.text?.toString()?.trim()
                when {
                    name.isNullOrBlank() -> til?.error = "Name cannot be empty"
                    viewModel.uiState.value.availableTags.contains(name) ->
                        til?.error = "Tag already exists"
                    else -> {
                        viewModel.bulkAddTags(hashes, listOf(name))
                        dialog.dismiss()
                    }
                }
            }
            tiet?.doAfterTextChanged { til?.error = null }
        }
        dialog.show()
    }

    fun showManageTagsDialog() {
        val tags = viewModel.uiState.value.availableTags
        val marked = BooleanArray(tags.size) { false }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage tags")
            .setMultiChoiceItems(tags.toTypedArray(), marked) { _, which, isChecked ->
                marked[which] = isChecked
            }
            .setPositiveButton("Delete") { _, _ ->
                val toDelete = tags.filterIndexed { i, _ -> marked[i] }
                if (toDelete.isNotEmpty()) viewModel.deleteTags(toDelete)
                else Toast.makeText(requireContext(), "Nothing selected", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("New tag…") { _, _ -> showCreateNewTagDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCreateNewTagDialog() {
        val view = fragment.layoutInflater.inflate(R.layout.dialog_text_input, null, false)
        val til = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val tiet = view.findViewById<TextInputEditText>(R.id.text_input_edit)
        til?.hint = "Tag name"

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("New tag")
                .setView(view)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = tiet?.text?.toString()?.trim()
                when {
                    name.isNullOrBlank() -> til?.error = "Name cannot be empty"
                    viewModel.uiState.value.availableTags.contains(name) ->
                        til?.error = "Tag already exists"
                    else -> {
                        viewModel.createTag(name)
                        dialog.dismiss()
                    }
                }
            }
            tiet?.doAfterTextChanged { til?.error = null }
        }
        dialog.show()
    }

    fun showManageCategoriesDialog() {
        val categories = viewModel.uiState.value.availableCategories
        val marked = BooleanArray(categories.size) { false }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage categories")
            .setMultiChoiceItems(categories.toTypedArray(), marked) { _, which, isChecked ->
                marked[which] = isChecked
            }
            .setPositiveButton("Delete") { _, _ ->
                val toDelete = categories.filterIndexed { i, _ -> marked[i] }
                if (toDelete.isNotEmpty()) viewModel.deleteCategories(toDelete)
                else Toast.makeText(requireContext(), "Nothing selected", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("New category…") { _, _ -> showCreateNewCategoryDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCreateNewCategoryDialog() {
        val view = fragment.layoutInflater.inflate(R.layout.dialog_text_input, null, false)
        val til = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val tiet = view.findViewById<TextInputEditText>(R.id.text_input_edit)
        til?.hint = "Category name"

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("New category")
                .setView(view)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = tiet?.text?.toString()?.trim()
                when {
                    name.isNullOrBlank() -> til?.error = "Name cannot be empty"
                    viewModel.uiState.value.availableCategories.contains(name) ->
                        til?.error = "Category already exists"
                    else -> {
                        viewModel.createCategory(name)
                        dialog.dismiss()
                    }
                }
            }
            tiet?.doAfterTextChanged { til?.error = null }
        }
        dialog.show()
    }

    /**
     * Shown on long-press of a category row in the drawer. Only offers officially-supported
     * qBittorrent operations: editing the category's save path (there's no rename endpoint - the
     * name is the identifier) and deleting it.
     */
    fun showCategoryLongPressDialog(name: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(name)
            .setPositiveButton(getString(CommonR.string.edit)) { _, _ ->
                showEditCategorySavePathDialog(name)
            }
            .setNeutralButton(getString(CommonR.string.delete)) { _, _ ->
                viewModel.deleteCategories(listOf(name))
            }
            .setNegativeButton(getString(CommonR.string.cancel), null)
            .show()
    }

    private fun showEditCategorySavePathDialog(name: String) {
        val currentSavePath =
            viewModel.uiState.value.data?.categories?.get(name)?.savePath.orEmpty()
        val view = fragment.layoutInflater.inflate(R.layout.dialog_text_input, null, false)
        val til = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val tiet = view.findViewById<TextInputEditText>(R.id.text_input_edit)
        til?.hint = getString(CommonR.string.save_path_hint)
        tiet?.setText(currentSavePath)

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(CommonR.string.edit_category_title))
                .setView(view)
                .setPositiveButton(getString(CommonR.string.edit), null)
                .setNegativeButton(getString(CommonR.string.cancel), null)
                .create()

        dialog.setOnShowListener {
            tiet?.setSelection(tiet.text?.length ?: 0)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val savePath = tiet?.text?.toString()?.trim().orEmpty()
                viewModel.editCategorySavePath(name, savePath)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /**
     * Shown on long-press of a tag row in the drawer. Delete only - qBittorrent has no endpoint to
     * edit or rename an existing tag.
     */
    fun showTagLongPressDialog(name: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(name)
            .setPositiveButton(getString(CommonR.string.delete)) { _, _ ->
                viewModel.deleteTags(listOf(name))
            }
            .setNegativeButton(getString(CommonR.string.cancel), null)
            .show()
    }

    private fun getString(resId: Int) = fragment.getString(resId)

    fun showSortPicker() {
        val state = viewModel.uiState.value
        val options = SortOption.entries
        val labels =
            options.map { option ->
                when {
                    option != state.sortOption -> option.label
                    state.sortDirection == SortDirection.ASC -> "↑ ${option.label}"
                    else -> "↓ ${option.label}"
                }
            }
        val checkedIndex = options.indexOf(state.sortOption)

        val dirLabel =
            if (state.sortDirection == SortDirection.ASC) "↑ Ascending" else "↓ Descending"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort by")
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                viewModel.setSort(options[which])
                dialog.dismiss()
            }
            .setNeutralButton(dirLabel) { _, _ -> viewModel.toggleSortDirection() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
