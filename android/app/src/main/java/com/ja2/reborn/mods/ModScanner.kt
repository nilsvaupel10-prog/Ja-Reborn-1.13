package com.ja2.reborn.mods

import android.content.res.AssetManager
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Discovers the mods a user can enable.
 *
 * Two places are scanned, exactly like the native `ModManager` does:
 *
 * - `.ja2/mods` inside the app files directory, which is where users put their own mods.
 * - `mods` inside the APK assets, which holds the mods bundled with the game.
 *
 * A folder in `.ja2/mods` wins over an asset folder with the same name, and only directory names
 * consisting of lowercase letters, digits and dashes are accepted - the engine ignores everything
 * else, so the launcher must not offer it either.
 */
object ModScanner {
    private const val TAG = "ModScanner"

    /** Directory name of the mods inside the `.ja2` home directory and inside the APK assets. */
    const val MODS_DIR_NAME = "mods"

    /** File with the readable name, the version and the description of a mod. */
    const val MANIFEST_FILE_NAME = "manifest.json"

    /** Sub directory of a mod that the engine mounts into the virtual file system. */
    const val DATA_DIR_NAME = "data"

    /** Mod folder names accepted by the engine: lowercase letters, digits and dashes only. */
    private val MOD_ID_PATTERN: Regex = Regex("^[a-z0-9\\-]+$")

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        allowComments = true
        isLenient = true
    }

    /** `true` when [id] can be used as a mod folder name. */
    fun isModIdValid(id: String): Boolean = MOD_ID_PATTERN.matches(id)

    /**
     * Scans [modsDir] and, when [assetManager] is given, the bundled mods of the APK assets.
     * The returned list is sorted by display name and contains no duplicates.
     */
    fun scan(modsDir: File, assetManager: AssetManager? = null): ModScanResult {
        val byId = LinkedHashMap<String, InstalledMod>()
        val ignored = ArrayList<String>()

        if (assetManager != null) {
            for (mod in scanAssets(assetManager)) {
                byId[mod.id] = mod
            }
        }
        for (mod in scanDirectory(modsDir, ignored)) {
            byId[mod.id] = mod
        }

        val mods = byId.values.sortedBy { entry -> entry.displayName.lowercase() }
        return ModScanResult(modsDir = modsDir, mods = mods, ignoredEntries = ignored)
    }

    /** Scans the mod folders below [modsDir], ignoring everything the engine could not load. */
    fun scanDirectory(modsDir: File, ignoredEntries: MutableList<String>? = null): List<InstalledMod> {
        val children = modsDir.listFiles() ?: return emptyList()
        if (children.isEmpty()) {
            return emptyList()
        }

        val found = ArrayList<InstalledMod>(children.size)
        for (child in children.sortedBy { entry -> entry.name.lowercase() }) {
            if (!child.isDirectory) {
                continue
            }
            val id = child.name
            if (!isModIdValid(id)) {
                ignoredEntries?.add(id)
                Log.w(TAG, "Ignoring mod folder '$id': mod names must be lowercase letters, digits and dashes")
                continue
            }
            val manifest = readManifest(File(child, MANIFEST_FILE_NAME))
            found.add(
                InstalledMod(
                    id = id,
                    displayName = manifest?.name?.takeIf { value -> value.isNotBlank() } ?: id,
                    version = manifest?.version?.takeIf { value -> value.isNotBlank() },
                    description = manifest?.description?.takeIf { value -> value.isNotBlank() },
                    source = ModSource.INSTALLED,
                    hasDataDirectory = hasDataDirectory(child)
                )
            )
        }
        return found
    }

    /**
     * Scans the mods that are bundled in the APK assets and therefore read-only. Like the engine,
     * this only accepts folders that describe themselves with a `manifest.json`.
     */
    fun scanAssets(assetManager: AssetManager): List<InstalledMod> {
        val entries: Array<String> = try {
            assetManager.list(MODS_DIR_NAME) ?: emptyArray()
        } catch (e: IOException) {
            Log.w(TAG, "Could not list the bundled mods directory: ${e.message}")
            emptyArray()
        }

        val found = ArrayList<InstalledMod>(entries.size)
        for (id in entries.sortedBy { entry -> entry.lowercase() }) {
            // The asset listing mixes files and directories, files never contain a manifest.
            if (!isModIdValid(id)) {
                continue
            }
            val modPath = "$MODS_DIR_NAME/$id"
            val manifest = readAssetManifest(assetManager, "$modPath/$MANIFEST_FILE_NAME")
            if (manifest == null) {
                // Without a manifest the engine would not load this bundle, so neither does the launcher.
                Log.w(TAG, "Ignoring bundled mod '$id': no readable $MANIFEST_FILE_NAME")
                continue
            }
            found.add(
                InstalledMod(
                    id = id,
                    displayName = manifest.name?.takeIf { value -> value.isNotBlank() } ?: id,
                    version = manifest.version?.takeIf { value -> value.isNotBlank() },
                    description = manifest.description?.takeIf { value -> value.isNotBlank() },
                    source = ModSource.BUNDLED,
                    hasDataDirectory = assetHasEntry(assetManager, modPath, DATA_DIR_NAME)
                )
            )
        }
        return found
    }

    /**
     * Splits [allMods] into the enabled mods, ordered like [enabledIds], and the rest. Enabled ids
     * without a matching folder are reported as [ModSource.MISSING] entries so the user can see
     * and fix them instead of losing them silently.
     */
    fun splitByEnabled(
        allMods: List<InstalledMod>,
        enabledIds: List<String>
    ): Pair<List<InstalledMod>, List<InstalledMod>> {
        val byId = LinkedHashMap<String, InstalledMod>()
        for (mod in allMods) {
            byId[mod.id.lowercase()] = mod
        }

        val wanted = enabledIds.mapNotNull { id -> id.trim().lowercase().takeIf { it.isNotEmpty() } }
        val enabled = ArrayList<InstalledMod>(wanted.size)
        val seen = HashSet<String>(wanted.size)
        for (id in wanted) {
            if (!seen.add(id)) {
                continue
            }
            enabled.add(byId[id] ?: InstalledMod(id = id, displayName = id, source = ModSource.MISSING))
        }

        val disabled = allMods.filterNot { mod -> seen.contains(mod.id.lowercase()) }
        return Pair(enabled, disabled)
    }

    private fun hasDataDirectory(modDir: File): Boolean {
        val children = modDir.listFiles() ?: return false
        return children.any { child ->
            child.isDirectory && child.name.equals(DATA_DIR_NAME, ignoreCase = true)
        }
    }

    private fun readManifest(manifestFile: File): ModManifest? {
        if (!manifestFile.isFile) {
            return null
        }
        return try {
            jsonFormat.decodeFromString(ModManifest.serializer(), manifestFile.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Could not read mod manifest ${manifestFile.path}: ${e.message}")
            null
        }
    }

    private fun readAssetManifest(assetManager: AssetManager, path: String): ModManifest? {
        return try {
            val text = assetManager.open(path).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
            jsonFormat.decodeFromString(ModManifest.serializer(), text)
        } catch (e: Exception) {
            null
        }
    }

    private fun assetHasEntry(assetManager: AssetManager, path: String, name: String): Boolean {
        val entries = try {
            assetManager.list(path) ?: emptyArray()
        } catch (e: IOException) {
            return false
        }
        return entries.any { entry -> entry.equals(name, ignoreCase = true) }
    }
}
