package com.ja2.reborn.mods

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.ja2.reborn.R
import com.ja2.reborn.databinding.DialogModsBinding
import com.ja2.reborn.databinding.ItemModEntryBinding
import java.io.File

/**
 * Lets the user enable, disable and order the mods of the `.ja2/mods` directory.
 *
 * The dialog works on a copy of the `mods` list stored in `ja2.json`. Only `Apply` hands the
 * selection over to [onApply], which updates the in-memory configuration and writes it back into
 * `ja2.json`; closing with `Cancel` throws the changes away.
 *
 * Rows are listed in load order. The engine mounts the mods so that a later entry overrides the
 * earlier ones, so the bottom row of the enabled block has the highest priority.
 */
class ModsDialog(
    private val context: Context,
    private val modsDir: File,
    private val assetManager: AssetManager?,
    private val enabledModIds: List<String>,
    private val onApply: (List<String>) -> Unit
) {
    private val binding = DialogModsBinding.inflate(LayoutInflater.from(context))
    private val enabledOrder = ArrayList<String>()

    private var scanResult: ModScanResult = ModScanResult(modsDir)
    private var dialog: AlertDialog? = null

    init {
        for (id in enabledModIds) {
            val normalized = id.trim().lowercase()
            if (normalized.isNotEmpty() && normalized !in enabledOrder) {
                enabledOrder.add(normalized)
            }
        }
    }

    /** Shows the dialog after scanning the mods directory. */
    fun show() {
        binding.modsCopyPathButton.setOnClickListener { copyModsPath() }
        binding.modsRescanButton.setOnClickListener { rescan() }
        binding.modsCancelButton.setOnClickListener { dialog?.dismiss() }
        binding.modsApplyButton.setOnClickListener {
            val selection = ArrayList(enabledOrder)
            dialog?.dismiss()
            onApply(selection)
        }

        val newDialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()
        newDialog.setOnShowListener {
            newDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val metrics = context.resources.displayMetrics
            newDialog.window?.setLayout(
                (metrics.widthPixels * MODS_DIALOG_WIDTH_FACTOR).toInt(),
                (metrics.heightPixels * MODS_DIALOG_HEIGHT_FACTOR).toInt()
            )
        }
        dialog = newDialog
        rescan()
        newDialog.show()
    }

    /** Looks for mod folders again, creating the mods directory while it is still missing. */
    private fun rescan() {
        if (!modsDir.isDirectory && !modsDir.mkdirs()) {
            Toast.makeText(context, R.string.mods_dir_create_failed, Toast.LENGTH_LONG).show()
        }
        scanResult = ModScanner.scan(modsDir, assetManager)
        render()
    }

    private fun render() {
        val (enabledMods, disabledMods) = ModScanner.splitByEnabled(scanResult.mods, enabledOrder)
        val inflater = LayoutInflater.from(context)
        val scrollPosition = binding.modsListScroll.scrollY

        binding.modsListContainer.removeAllViews()
        for ((position, mod) in enabledMods.withIndex()) {
            binding.modsListContainer.addView(
                createRow(
                    inflater = inflater,
                    mod = mod,
                    enabled = true,
                    priority = position + 1,
                    position = position,
                    enabledCount = enabledMods.size
                )
            )
        }
        for (mod in disabledMods) {
            binding.modsListContainer.addView(
                createRow(
                    inflater = inflater,
                    mod = mod,
                    enabled = false,
                    priority = 0,
                    position = NO_POSITION,
                    enabledCount = 0
                )
            )
        }

        renderHeader(enabledMods.size, enabledMods.size + disabledMods.size)
        renderEmptyState(enabledMods)
        renderWarnings(enabledMods)

        binding.modsListScroll.post { binding.modsListScroll.scrollTo(0, scrollPosition) }
    }

    private fun createRow(
        inflater: LayoutInflater,
        mod: InstalledMod,
        enabled: Boolean,
        priority: Int,
        position: Int,
        enabledCount: Int
    ): View {
        val row = ItemModEntryBinding.inflate(inflater, binding.modsListContainer, false)

        row.modNameText.text = if (mod.version != null) {
            context.getString(R.string.mods_entry_name_with_version, mod.displayName, mod.version)
        } else {
            mod.displayName
        }
        row.modIdText.text = describeMod(mod)

        val description = mod.description
        if (description != null) {
            row.modDescriptionText.text = description
            row.modDescriptionText.visibility = View.VISIBLE
        } else {
            row.modDescriptionText.visibility = View.GONE
        }

        // Removing the listener avoids a re-render while the checked state is restored.
        row.modEnabledCheckbox.setOnCheckedChangeListener(null)
        row.modEnabledCheckbox.isChecked = enabled
        row.modEnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            setModEnabled(mod.id, isChecked)
        }

        if (enabled) {
            row.modPriorityText.text = context.getString(R.string.mods_priority_label, priority)
            row.modPriorityText.visibility = View.VISIBLE
        } else {
            row.modPriorityText.visibility = View.GONE
        }
        row.modMoveUpButton.isEnabled = enabled && position > 0
        row.modMoveDownButton.isEnabled = enabled && position >= 0 && position < enabledCount - 1
        row.modMoveUpButton.setOnClickListener { moveMod(position, -1) }
        row.modMoveDownButton.setOnClickListener { moveMod(position, 1) }

        return row.root
    }

    private fun describeMod(mod: InstalledMod): String {
        val sourceLabel = when (mod.source) {
            ModSource.INSTALLED -> R.string.mods_source_installed
            ModSource.BUNDLED -> R.string.mods_source_bundled
            ModSource.MISSING -> R.string.mods_source_missing
        }
        val parts = mutableListOf(mod.id, context.getString(sourceLabel))
        if (mod.isAvailable && !mod.hasDataDirectory) {
            parts.add(context.getString(R.string.mods_problem_no_data_dir_short))
        }
        return parts.joinToString(" - ")
    }

    private fun renderHeader(enabledCount: Int, totalCount: Int) {
        binding.modsDialogPathText.text = context.getString(R.string.mods_folder_path, modsDir.absolutePath)
        if (totalCount > 0) {
            binding.modsCounterText.text = context.getString(R.string.mods_enabled_count, enabledCount, totalCount)
            binding.modsCounterText.visibility = View.VISIBLE
        } else {
            binding.modsCounterText.visibility = View.GONE
        }
    }

    private fun renderEmptyState(enabledMods: List<InstalledMod>) {
        val nothingFound = !scanResult.hasMods && enabledMods.isEmpty()
        if (nothingFound) {
            binding.modsEmptyText.text = context.getString(R.string.mods_empty_hint, modsDir.absolutePath)
            binding.modsEmptyText.visibility = View.VISIBLE
        } else {
            binding.modsEmptyText.visibility = View.GONE
        }
    }

    private fun renderWarnings(enabledMods: List<InstalledMod>) {
        val problems = mutableListOf<String>()
        for (mod in enabledMods) {
            if (!mod.isAvailable) {
                problems.add(context.getString(R.string.mods_problem_missing, mod.id))
            } else if (!mod.hasDataDirectory) {
                problems.add(context.getString(R.string.mods_problem_no_data_dir, mod.id))
            }
        }
        val ignored = scanResult.ignoredEntries
        if (ignored.isNotEmpty()) {
            problems.add(context.getString(R.string.mods_problem_ignored, ignored.joinToString(", ")))
        }

        if (problems.isEmpty()) {
            binding.modsWarningText.visibility = View.GONE
        } else {
            binding.modsWarningText.text = problems.joinToString("\n")
            binding.modsWarningText.visibility = View.VISIBLE
        }
    }

    private fun setModEnabled(id: String, enabled: Boolean) {
        val index = enabledOrder.indexOf(id)
        if (enabled && index < 0) {
            // New mods are appended at the end, which is the highest priority for the engine.
            enabledOrder.add(id)
        } else if (!enabled && index >= 0) {
            enabledOrder.removeAt(index)
        }
        render()
    }

    private fun moveMod(position: Int, delta: Int) {
        val target = position + delta
        if (position < 0 || position >= enabledOrder.size || target < 0 || target >= enabledOrder.size) {
            return
        }
        val id = enabledOrder.removeAt(position)
        enabledOrder.add(target, id)
        render()
    }

    private fun copyModsPath() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(context, modsDir.absolutePath, Toast.LENGTH_LONG).show()
            return
        }
        clipboard.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.mods_folder_path_short), modsDir.absolutePath)
        )
        Toast.makeText(context, R.string.mods_path_copied, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val NO_POSITION = -1
        const val MODS_DIALOG_WIDTH_FACTOR = 0.92f
        const val MODS_DIALOG_HEIGHT_FACTOR = 0.88f
    }
}
