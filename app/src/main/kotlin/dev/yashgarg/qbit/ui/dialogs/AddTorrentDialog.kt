package dev.yashgarg.qbit.ui.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.utils.ClipboardUtil
import dev.yashgarg.qbit.validation.LinkValidator

class AddTorrentDialog : DialogFragment() {
    private val linkValidator by lazy { LinkValidator() }

    private val availableCategories: List<String>
        get() = arguments?.getStringArrayList(ARG_CATEGORIES) ?: emptyList()

    private val defaultAutoTmm: Boolean
        get() = arguments?.getBoolean(ARG_DEFAULT_AUTO_TMM, false) ?: false

    private val defaultPaused: Boolean
        get() = arguments?.getBoolean(ARG_DEFAULT_PAUSED, false) ?: false

    private val defaultCategory: String
        get() = arguments?.getString(ARG_DEFAULT_CATEGORY).orEmpty()

    private val prefillUrl: String?
        get() = arguments?.getString(ARG_PREFILL_URL)

    private val prefillFileUri: String?
        get() = arguments?.getString(ARG_PREFILL_FILE_URI)

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!uris.isNullOrEmpty()) {
                val dialog = requireDialog()
                val category =
                    dialog
                        .findViewById<AutoCompleteTextView>(R.id.category_actv)
                        ?.text
                        ?.toString()
                        ?.takeIf { it.isNotBlank() }
                val savePath =
                    dialog
                        .findViewById<TextInputEditText>(R.id.save_path_tiet)
                        ?.text
                        ?.toString()
                        ?.takeIf { it.isNotBlank() }
                val paused =
                    dialog.findViewById<MaterialSwitch>(R.id.paused_switch)?.isChecked ?: false
                val autoTmm =
                    dialog.findViewById<MaterialSwitch>(R.id.auto_tmm_switch)?.isChecked ?: false
                val saveCategoryDefault =
                    dialog.findViewById<MaterialSwitch>(R.id.save_category_switch)?.isChecked
                        ?: false

                setFragmentResult(
                    ADD_TORRENT_FILE_KEY,
                    bundleOf(
                        TORRENT_KEY to uris,
                        CATEGORY_KEY to category,
                        SAVE_PATH_KEY to savePath,
                        PAUSED_KEY to paused,
                        AUTO_TMM_KEY to autoTmm,
                        SAVE_CATEGORY_DEFAULT_KEY to saveCategoryDefault,
                    ),
                )
                dismiss()
            } else {
                Toast.makeText(
                        requireContext(),
                        getString(CommonR.string.no_file_selected),
                        Toast.LENGTH_SHORT
                    )
                    .show()
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(CommonR.string.add_torrent_title))
                .setView(R.layout.add_torrent_dialog)
                .setNeutralButton(getString(CommonR.string.upload_file), null)
                .setNegativeButton(getString(CommonR.string.cancel)) { d, _ -> d.dismiss() }
                .setPositiveButton(getString(CommonR.string.add), null)
                .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        dialog.setOnShowListener {
            val magnetTil = dialog.findViewById<TextInputLayout>(R.id.magnet_til)
            val magnetTiet = dialog.findViewById<TextInputEditText>(R.id.magnet_tiet)
            val categoryActv = dialog.findViewById<AutoCompleteTextView>(R.id.category_actv)

            // Surface the saved default category first so it's the top suggestion, then prefill it.
            val orderedCategories =
                if (defaultCategory.isNotBlank() && availableCategories.contains(defaultCategory)) {
                    listOf(defaultCategory) + availableCategories.filter { it != defaultCategory }
                } else {
                    availableCategories
                }
            val categories = listOf("") + orderedCategories
            val adapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    categories
                )
            categoryActv?.setAdapter(adapter)
            if (defaultCategory.isNotBlank()) {
                categoryActv?.setText(defaultCategory, false)
            }

            val saveCategorySwitch = dialog.findViewById<MaterialSwitch>(R.id.save_category_switch)
            saveCategorySwitch?.isChecked = defaultCategory.isNotBlank()

            val autoTmmSwitch = dialog.findViewById<MaterialSwitch>(R.id.auto_tmm_switch)
            val pausedSwitch = dialog.findViewById<MaterialSwitch>(R.id.paused_switch)
            val savePathTil = dialog.findViewById<TextInputLayout>(R.id.save_path_til)
            autoTmmSwitch?.setOnCheckedChangeListener { _, isChecked ->
                savePathTil?.visibility = if (isChecked) View.GONE else View.VISIBLE
            }
            autoTmmSwitch?.isChecked = defaultAutoTmm
            pausedSwitch?.isChecked = defaultPaused

            when {
                prefillUrl != null -> magnetTiet?.setText(prefillUrl)
                prefillFileUri != null -> {
                    magnetTil?.hint = getString(CommonR.string.selected_file)
                    magnetTil?.isEndIconVisible = false
                    val filename =
                        Uri.parse(prefillFileUri).lastPathSegment
                            ?: getString(CommonR.string.torrent_file_fallback_name)
                    magnetTiet?.setText(filename)
                    magnetTiet?.isFocusable = false
                    magnetTiet?.isClickable = false
                }
                else -> {
                    magnetTil?.setEndIconOnClickListener {
                        val clipText = ClipboardUtil.getClipboardText(requireContext())
                        magnetTiet?.setText(clipText)
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (prefillFileUri != null) {
                    val category = categoryActv?.text?.toString()?.takeIf { it.isNotBlank() }
                    val savePath =
                        dialog
                            .findViewById<TextInputEditText>(R.id.save_path_tiet)
                            ?.text
                            ?.toString()
                            ?.takeIf { it.isNotBlank() }
                    val paused =
                        dialog.findViewById<MaterialSwitch>(R.id.paused_switch)?.isChecked ?: false
                    val autoTmm = autoTmmSwitch?.isChecked ?: false
                    val saveCategoryDefault = saveCategorySwitch?.isChecked ?: false
                    setFragmentResult(
                        ADD_TORRENT_FILE_KEY,
                        bundleOf(
                            TORRENT_KEY to arrayListOf(Uri.parse(prefillFileUri)),
                            CATEGORY_KEY to category,
                            SAVE_PATH_KEY to savePath,
                            PAUSED_KEY to paused,
                            AUTO_TMM_KEY to autoTmm,
                            SAVE_CATEGORY_DEFAULT_KEY to saveCategoryDefault,
                        ),
                    )
                    dialog.dismiss()
                } else {
                    val magnetUri = magnetTiet?.text.toString()
                    if (!magnetTiet?.text.isNullOrEmpty() && linkValidator.isValid(magnetUri)) {
                        val category = categoryActv?.text?.toString()?.takeIf { it.isNotBlank() }
                        val savePath =
                            dialog
                                .findViewById<TextInputEditText>(R.id.save_path_tiet)
                                ?.text
                                ?.toString()
                                ?.takeIf { it.isNotBlank() }
                        val paused =
                            dialog.findViewById<MaterialSwitch>(R.id.paused_switch)?.isChecked
                                ?: false
                        val autoTmm = autoTmmSwitch?.isChecked ?: false
                        val saveCategoryDefault = saveCategorySwitch?.isChecked ?: false

                        setFragmentResult(
                            ADD_TORRENT_KEY,
                            bundleOf(
                                TORRENT_KEY to magnetUri,
                                CATEGORY_KEY to category,
                                SAVE_PATH_KEY to savePath,
                                PAUSED_KEY to paused,
                                AUTO_TMM_KEY to autoTmm,
                                SAVE_CATEGORY_DEFAULT_KEY to saveCategoryDefault,
                            ),
                        )
                        dialog.dismiss()
                    } else {
                        magnetTil?.error = getString(CommonR.string.invalid_magnet_link)
                    }

                    magnetTiet?.doAfterTextChanged { magnetTil?.error = null }
                }
            }

            val uploadButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (prefillFileUri != null) {
                uploadButton.visibility = View.GONE
            } else {
                // The Storage Access Framework picker grants read access to the chosen file, so
                // no storage permission is needed.
                uploadButton.setOnClickListener { filePickerLauncher.launch(TORRENT_MIMETYPE) }
            }
        }

        return dialog
    }

    companion object {
        fun newInstance(
            availableCategories: List<String> = emptyList(),
            defaultAutoTmm: Boolean = false,
            defaultPaused: Boolean = false,
            defaultCategory: String = "",
            prefillUrl: String? = null,
            prefillFileUri: String? = null,
        ): AddTorrentDialog =
            AddTorrentDialog().apply {
                arguments =
                    bundleOf(
                        ARG_CATEGORIES to ArrayList(availableCategories),
                        ARG_DEFAULT_AUTO_TMM to defaultAutoTmm,
                        ARG_DEFAULT_PAUSED to defaultPaused,
                        ARG_DEFAULT_CATEGORY to defaultCategory,
                        ARG_PREFILL_URL to prefillUrl,
                        ARG_PREFILL_FILE_URI to prefillFileUri,
                    )
            }

        const val TAG = "AddTorrentDialogFragment"
        const val ADD_TORRENT_KEY = "add_torrent"
        const val ADD_TORRENT_FILE_KEY = "add_torrent_file"
        const val TORRENT_KEY = "torrent"
        const val CATEGORY_KEY = "category"
        const val SAVE_PATH_KEY = "save_path"
        const val PAUSED_KEY = "paused"
        const val AUTO_TMM_KEY = "auto_tmm"
        const val SAVE_CATEGORY_DEFAULT_KEY = "save_category_default"
        private const val ARG_CATEGORIES = "arg_categories"
        private const val ARG_DEFAULT_AUTO_TMM = "arg_default_auto_tmm"
        private const val ARG_DEFAULT_PAUSED = "arg_default_paused"
        private const val ARG_DEFAULT_CATEGORY = "arg_default_category"
        private const val ARG_PREFILL_URL = "arg_prefill_url"
        private const val ARG_PREFILL_FILE_URI = "arg_prefill_file_uri"
        const val TORRENT_MIMETYPE = "application/x-bittorrent"
    }
}
