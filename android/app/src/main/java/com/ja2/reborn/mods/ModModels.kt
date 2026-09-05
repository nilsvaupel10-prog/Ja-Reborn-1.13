package com.ja2.reborn.mods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/** Where a mod was found. */
enum class ModSource {
    /** Folder inside the `.ja2/mods` directory, installed by the user. */
    INSTALLED,

    /** Folder bundled in the APK assets, provided by the game itself. */
    BUNDLED,

    /** Enabled in `ja2.json`, but the folder is currently not present. */
    MISSING
}

/**
 * `manifest.json` of a mod folder.
 *
 * The native engine expects `name` and `version` to be present and falls back to the folder name
 * when it cannot read the manifest. The launcher mirrors that by treating every field as optional,
 * so a mod with a broken or missing manifest is still listed and can still be enabled.
 */
@Serializable
data class ModManifest(
    @SerialName("name")
    val name: String? = null,
    @SerialName("version")
    val version: String? = null,
    @SerialName("description")
    val description: String? = null
)

/**
 * A mod the launcher can offer to the user.
 *
 * @property id folder name of the mod, this is what `ja2.json` lists in its `mods` array
 * @property displayName readable name from the manifest, falling back to [id]
 * @property version version string from the manifest, `null` when unknown
 * @property description description from the manifest, `null` when absent
 * @property source where the mod was found
 * @property hasDataDirectory `false` when the mod has no `data` sub directory, which the engine
 *           needs to mount the mod into the virtual file system
 */
data class InstalledMod(
    val id: String,
    val displayName: String,
    val version: String? = null,
    val description: String? = null,
    val source: ModSource = ModSource.INSTALLED,
    val hasDataDirectory: Boolean = true
) {
    /** `true` when the engine can load this mod from disk or from the APK assets. */
    val isAvailable: Boolean
        get() = source != ModSource.MISSING
}

/**
 * Everything the launcher found while looking for mods.
 *
 * @property modsDir the scanned mods directory
 * @property mods available mods plus the enabled ones that could not be found, sorted by display name
 * @property ignoredEntries directories that were skipped because their name cannot be a mod id
 */
data class ModScanResult(
    val modsDir: File,
    val mods: List<InstalledMod> = emptyList(),
    val ignoredEntries: List<String> = emptyList()
) {
    val hasMods: Boolean
        get() = mods.isNotEmpty()
}
