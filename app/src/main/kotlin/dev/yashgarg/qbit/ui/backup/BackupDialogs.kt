package dev.yashgarg.qbit.ui.backup

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.yashgarg.qbit.data.backup.ConfigBackup
import dev.yashgarg.qbit.data.backup.ImportMode
import dev.yashgarg.qbit.data.backup.PrefGroup
import dev.yashgarg.qbit.data.backup.availablePrefGroups
import dev.yashgarg.qbit.data.models.ServerConfig

/**
 * Backup-related dialogs shared by Settings and the first-run screen. Built programmatically with
 * [MaterialAlertDialogBuilder] to match the app's existing dialog style.
 */
object BackupDialogs {

    /**
     * Prompts for a passphrase. When [confirm] is set, a second field must match. The OK button
     * validates inline and only dismisses once the input is valid. The passphrase length is up to
     * the user, but it must be non-empty: it's the only key protecting the exported credentials, so
     * an empty one would leave the backup effectively unencrypted.
     */
    fun showPassphraseDialog(
        context: Context,
        title: String,
        confirm: Boolean,
        onOk: (String) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        val horizontal = (24 * density).toInt()
        val vertical = (8 * density).toInt()

        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(horizontal, vertical, horizontal, 0)
            }
        val passField =
            EditText(context).apply {
                hint = "Passphrase"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        container.addView(passField)
        val confirmField =
            if (confirm) {
                EditText(context)
                    .apply {
                        hint = "Confirm passphrase"
                        inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    .also(container::addView)
            } else null

        val dialog =
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val passphrase = passField.text?.toString().orEmpty()
                when {
                    passphrase.isEmpty() -> passField.error = "Enter a passphrase"
                    confirm && passphrase != confirmField?.text?.toString() ->
                        confirmField?.error = "Passphrases don't match"
                    else -> {
                        dialog.dismiss()
                        onOk(passphrase)
                    }
                }
            }
        }
        dialog.show()
    }

    /**
     * Lets the user pick which [servers], app settings, and category colors to export. All boxes
     * start checked; the OK button stays disabled until at least one server is chosen (an export
     * with no servers can't be imported). [onConfirm] receives the chosen server ids and whether to
     * include preferences and category colors.
     */
    fun showExportSelectionDialog(
        context: Context,
        servers: List<ServerConfig>,
        onConfirm:
            (
                selectedServerIds: Set<Int>,
                prefGroups: Set<PrefGroup>,
                includeCategoryColors: Boolean,
            ) -> Unit,
    ) {
        val (container, content) = selectionContainer(context)

        content.addView(sectionHeader(context, "App settings", first = true))
        val groupBoxes =
            PrefGroup.entries.map { group ->
                val box =
                    CheckBox(context).apply {
                        text = prefGroupLabel(group)
                        isChecked = true
                    }
                content.addView(box)
                group to box
            }
        val colorsBox =
            CheckBox(context).apply {
                text = "Category colors"
                isChecked = true
            }
        content.addView(colorsBox)

        content.addView(sectionHeader(context, "Servers", first = false))
        val serverBoxes = servers.map { server ->
            val box =
                CheckBox(context).apply {
                    text = server.serverName
                    isChecked = true
                }
            content.addView(box)
            server.configId to box
        }

        val dialog =
            MaterialAlertDialogBuilder(context)
                .setTitle("Export configuration")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Export", null)
                .create()

        dialog.setOnShowListener {
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            fun refresh() {
                ok.isEnabled = serverBoxes.any { it.second.isChecked }
            }
            serverBoxes.forEach { it.second.setOnCheckedChangeListener { _, _ -> refresh() } }
            refresh()
            ok.setOnClickListener {
                val selected = serverBoxes.filter { it.second.isChecked }.map { it.first }.toSet()
                val groups = groupBoxes.filter { it.second.isChecked }.map { it.first }.toSet()
                dialog.dismiss()
                onConfirm(selected, groups, colorsBox.isChecked)
            }
        }
        dialog.show()
    }

    /**
     * Lets the user choose a merge/replace [mode] and which servers, app settings, and category
     * colors from a decrypted [backup] to import. Servers whose id is in [duplicateServerIds]
     * already exist: they are shown as "(already added)" and disabled/unchecked while merging
     * (they'd be skipped), and re-enabled when replacing. Import stays disabled until at least one
     * server is chosen.
     */
    fun showImportSelectionDialog(
        context: Context,
        backup: ConfigBackup,
        duplicateServerIds: Set<Int>,
        onConfirm:
            (
                selectedServerIds: Set<Int>,
                prefGroups: Set<PrefGroup>,
                includeCategoryColors: Boolean,
                mode: ImportMode,
            ) -> Unit,
    ) {
        val (container, content) = selectionContainer(context)

        content.addView(sectionHeader(context, "Import mode", first = true))
        val modeGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val mergeButton =
            RadioButton(context).apply {
                id = View.generateViewId()
                text = "Merge with current"
            }
        val replaceButton =
            RadioButton(context).apply {
                id = View.generateViewId()
                text = "Replace everything"
            }
        modeGroup.addView(mergeButton)
        modeGroup.addView(replaceButton)
        modeGroup.check(mergeButton.id)
        content.addView(modeGroup)

        // Colors may live in their own field or, for older backups, inside preferences.
        val availableGroups = backup.availablePrefGroups()
        val hasCategoryColors =
            !(backup.categoryColors ?: backup.preferences?.categoryColors).isNullOrEmpty()
        if (availableGroups.isNotEmpty() || hasCategoryColors) {
            content.addView(sectionHeader(context, "App settings", first = false))
        }
        val groupBoxes =
            PrefGroup.entries
                .filter { it in availableGroups }
                .map { group ->
                    val box =
                        CheckBox(context).apply {
                            text = prefGroupLabel(group)
                            isChecked = true
                        }
                    content.addView(box)
                    group to box
                }
        val colorsBox =
            if (hasCategoryColors) {
                CheckBox(context)
                    .apply {
                        text = "Category colors"
                        isChecked = true
                    }
                    .also(content::addView)
            } else null

        content.addView(sectionHeader(context, "Servers", first = false))
        val serverBoxes =
            backup.servers.map { server ->
                val duplicate = server.configId in duplicateServerIds
                val box =
                    CheckBox(context).apply {
                        text =
                            if (duplicate) "${server.serverName} (already added)"
                            else server.serverName
                        isChecked = true
                    }
                content.addView(box)
                Triple(server.configId, duplicate, box)
            }

        val dialog =
            MaterialAlertDialogBuilder(context)
                .setTitle("Import configuration")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", null)
                .create()

        dialog.setOnShowListener {
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            fun merging() = modeGroup.checkedRadioButtonId == mergeButton.id
            fun refresh() {
                // On merge, duplicates would be skipped, so lock them off; on replace, everything
                // is fair game.
                serverBoxes.forEach { (_, duplicate, box) ->
                    if (duplicate) {
                        box.isEnabled = !merging()
                        box.isChecked = !merging()
                    }
                }
                ok.isEnabled = serverBoxes.any { it.third.isEnabled && it.third.isChecked }
            }
            modeGroup.setOnCheckedChangeListener { _, _ -> refresh() }
            serverBoxes.forEach { it.third.setOnCheckedChangeListener { _, _ -> refresh() } }
            refresh()
            ok.setOnClickListener {
                val selected =
                    serverBoxes
                        .filter { it.third.isEnabled && it.third.isChecked }
                        .map { it.first }
                        .toSet()
                val groups = groupBoxes.filter { it.second.isChecked }.map { it.first }.toSet()
                val mode = if (merging()) ImportMode.MERGE else ImportMode.REPLACE
                dialog.dismiss()
                onConfirm(selected, groups, colorsBox?.isChecked ?: false, mode)
            }
        }
        dialog.show()
    }

    /** User-facing label for a preference group checkbox. */
    private fun prefGroupLabel(group: PrefGroup): String =
        when (group) {
            PrefGroup.THEME -> "Theme"
            PrefGroup.NOTIFICATIONS -> "Notifications"
            PrefGroup.FILTERS -> "Filters & sorting"
        }

    /** A bold, emphasized section label separating groups of options in a selection dialog. */
    private fun sectionHeader(context: Context, text: String, first: Boolean): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.START
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
            )
            setTextColor(context.themeColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(0, (if (first) 0 else 16 * density).toInt(), 0, (4 * density).toInt())
        }
    }

    /** Resolves a theme color attribute (e.g. colorPrimary) to a color int. */
    private fun Context.themeColor(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.data
    }

    /**
     * A padded, scrollable vertical container for a selection dialog. Returns (scroll, content).
     */
    private fun selectionContainer(context: Context): Pair<ScrollView, LinearLayout> {
        val density = context.resources.displayMetrics.density
        val horizontal = (24 * density).toInt()
        val vertical = (8 * density).toInt()
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(horizontal, vertical, horizontal, 0)
            }
        val scroll = ScrollView(context).apply { addView(content) }
        return scroll to content
    }
}
