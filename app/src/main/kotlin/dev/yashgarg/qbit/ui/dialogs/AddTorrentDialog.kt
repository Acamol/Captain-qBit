package dev.yashgarg.qbit.ui.dialogs

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
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
import dev.yashgarg.qbit.utils.ClipboardUtil
import dev.yashgarg.qbit.utils.PermissionUtil
import dev.yashgarg.qbit.validation.LinkValidator

class AddTorrentDialog : DialogFragment() {
    private val linkValidator by lazy { LinkValidator() }

    private val availableCategories: List<String>
        get() = arguments?.getStringArrayList(ARG_CATEGORIES) ?: emptyList()

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

                setFragmentResult(
                    ADD_TORRENT_FILE_KEY,
                    bundleOf(
                        TORRENT_KEY to uris,
                        CATEGORY_KEY to category,
                        SAVE_PATH_KEY to savePath,
                        PAUSED_KEY to paused,
                        AUTO_TMM_KEY to autoTmm,
                    ),
                )
                dismiss()
            } else {
                Toast.makeText(requireContext(), "No file selected", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(requireContext(), "Permission denied!", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Torrent")
                .setView(R.layout.add_torrent_dialog)
                .setNeutralButton("Upload File", null)
                .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                .setPositiveButton("Add", null)
                .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.setOnShowListener {
            val magnetTil = dialog.findViewById<TextInputLayout>(R.id.magnet_til)
            val magnetTiet = dialog.findViewById<TextInputEditText>(R.id.magnet_tiet)
            val categoryActv = dialog.findViewById<AutoCompleteTextView>(R.id.category_actv)

            val categories = listOf("") + availableCategories
            val adapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    categories
                )
            categoryActv?.setAdapter(adapter)

            val autoTmmSwitch = dialog.findViewById<MaterialSwitch>(R.id.auto_tmm_switch)
            val savePathTil = dialog.findViewById<TextInputLayout>(R.id.save_path_til)
            autoTmmSwitch?.setOnCheckedChangeListener { _, isChecked ->
                savePathTil?.visibility = if (isChecked) View.GONE else View.VISIBLE
            }

            magnetTil?.setEndIconOnClickListener {
                val clipText = ClipboardUtil.getClipboardText(requireContext())
                magnetTiet?.setText(clipText)
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
                        dialog.findViewById<MaterialSwitch>(R.id.paused_switch)?.isChecked ?: false
                    val autoTmm = autoTmmSwitch?.isChecked ?: false

                    setFragmentResult(
                        ADD_TORRENT_KEY,
                        bundleOf(
                            TORRENT_KEY to magnetUri,
                            CATEGORY_KEY to category,
                            SAVE_PATH_KEY to savePath,
                            PAUSED_KEY to paused,
                            AUTO_TMM_KEY to autoTmm,
                        ),
                    )
                    dialog.dismiss()
                } else {
                    magnetTil?.error = "Please enter a valid link!"
                }

                magnetTiet?.doAfterTextChanged { magnetTil?.error = null }
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (PermissionUtil.canReadStorage(requireContext())) {
                    filePickerLauncher.launch(TORRENT_MIMETYPE)
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }

        return dialog
    }

    companion object {
        fun newInstance(availableCategories: List<String> = emptyList()): AddTorrentDialog =
            AddTorrentDialog().apply {
                arguments = bundleOf(ARG_CATEGORIES to ArrayList(availableCategories))
            }

        const val TAG = "AddTorrentDialogFragment"
        const val ADD_TORRENT_KEY = "add_torrent"
        const val ADD_TORRENT_FILE_KEY = "add_torrent_file"
        const val TORRENT_KEY = "torrent"
        const val CATEGORY_KEY = "category"
        const val SAVE_PATH_KEY = "save_path"
        const val PAUSED_KEY = "paused"
        const val AUTO_TMM_KEY = "auto_tmm"
        private const val ARG_CATEGORIES = "arg_categories"
        const val TORRENT_MIMETYPE = "application/x-bittorrent"
    }
}
