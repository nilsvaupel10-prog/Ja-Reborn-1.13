package com.ja2.reborn

import android.content.Context
import android.util.Log
import com.ja2.reborn.mods.ModScanner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

private const val CONFIG_DIR_NAME = ".ja2"
private const val CONFIG_FILE_NAME = "ja2.json"
private const val CONFIG_LOG_TAG = "Ja2ConfigRepository"

private val EMPTY_JSON_OBJECT: JsonObject = JsonObject(emptyMap())

/** How a `ja2.json` file could be read. */
enum class Ja2ConfigLoadState {
    /** The launcher managed keys came from the stored file. */
    LOADED,

    /** No `ja2.json` was found, the defaults are used. */
    MISSING,

    /** The file exists but could not be parsed, the defaults are used and it was backed up. */
    INVALID
}

/**
 * Result of reading `ja2.json`.
 *
 * [unmappedFields] holds every key of the stored file that the launcher does not manage itself
 * (for example `brightness`, `nosound` or values written by hand). Those fields are stored back
 * unchanged, so saving the configuration never discards data the launcher has no UI for.
 *
 * @property config the decoded configuration
 * @property unmappedFields keys of the stored file that are not part of [Ja2Json]
 * @property loadState how the stored file could be read
 */
data class Ja2ConfigSnapshot(
    val config: Ja2Json,
    val unmappedFields: JsonObject = EMPTY_JSON_OBJECT,
    val loadState: Ja2ConfigLoadState = Ja2ConfigLoadState.MISSING
) {
    /** Directory names of the enabled mods in load order. */
    val mods: List<String>
        get() = config.mods ?: emptyList()

    /** Returns a copy of this snapshot that carries [updatedConfig]. */
    fun withConfig(updatedConfig: Ja2Json): Ja2ConfigSnapshot = copy(config = updatedConfig)

    companion object {
        /** Snapshot used when no `ja2.json` exists yet. */
        fun defaults(): Ja2ConfigSnapshot = Ja2ConfigSnapshot(Ja2Json())
    }
}

/**
 * Central place that knows where the `.ja2` configuration directory lives and how `ja2.json` is
 * read and written.
 *
 * The directory is the app private files directory (`<filesDir>/.ja2`), which is exactly what the
 * native engine resolves as its "stracciatella home" on Android: the Rust side queries
 * `Context.getFilesDir()` through JNI and appends `.ja2`. Launcher and game therefore always use
 * the same `ja2.json` and the same `mods` directory.
 *
 * Reading and writing are lossless: unknown keys are preserved through [Ja2ConfigSnapshot], the
 * parser accepts comments and trailing commas (the engine puts comments into default config
 * files), and writes go through a temporary file so an interrupted save cannot leave a half
 * written `ja2.json` behind.
 */
class Ja2ConfigRepository(private val filesDir: File) {

    constructor(context: Context) : this(context.applicationContext.filesDir)

    /** Directory the native engine refers to as its home directory. */
    val configDir: File = File(filesDir, CONFIG_DIR_NAME)

    /** Configuration file shared by the launcher and the native engine. */
    val configFile: File = File(configDir, CONFIG_FILE_NAME)

    /** Directory containing the mod folders a user can enable. */
    val modsDir: File = File(configDir, ModScanner.MODS_DIR_NAME)

    /** Copy of the last `ja2.json` the launcher could not understand. */
    private val backupFile: File = File(configDir, "$CONFIG_FILE_NAME.bak")

    /**
     * Reads `ja2.json`. A missing, unreadable or malformed file yields the default configuration
     * instead of throwing, because the launcher has to stay usable. A malformed file is backed up
     * before the launcher overwrites it.
     */
    fun load(): Ja2ConfigSnapshot {
        if (!configFile.isFile) {
            return Ja2ConfigSnapshot.defaults()
        }

        val storedText = try {
            configFile.readText()
        } catch (e: IOException) {
            Log.w(CONFIG_LOG_TAG, "Could not read ${configFile.absolutePath}: ${e.message}")
            return Ja2ConfigSnapshot.defaults()
        }

        val storedObject = try {
            readingJson.parseToJsonElement(storedText) as? JsonObject
        } catch (e: Exception) {
            Log.w(CONFIG_LOG_TAG, "Could not parse ${configFile.name}: ${e.message}")
            null
        }

        if (storedObject == null) {
            backupCorruptFile()
            return Ja2ConfigSnapshot(Ja2Json(), EMPTY_JSON_OBJECT, Ja2ConfigLoadState.INVALID)
        }

        val config = try {
            readingJson.decodeFromJsonElement(Ja2Json.serializer(), storedObject)
        } catch (e: Exception) {
            Log.w(CONFIG_LOG_TAG, "Could not decode ${configFile.name}: ${e.message}")
            backupCorruptFile()
            return Ja2ConfigSnapshot(Ja2Json(), EMPTY_JSON_OBJECT, Ja2ConfigLoadState.INVALID)
        }

        return Ja2ConfigSnapshot(
            config = config,
            unmappedFields = unmappedFieldsOf(storedObject),
            loadState = Ja2ConfigLoadState.LOADED
        )
    }

    /**
     * Writes the configuration of [snapshot] to `ja2.json` and re-applies the stored values the
     * launcher does not manage.
     *
     * @throws IOException when the file cannot be written
     */
    fun save(snapshot: Ja2ConfigSnapshot) {
        ensureDirectories()
        val merged = mergeForWrite(snapshot.config, snapshot.unmappedFields)
        writeAtomically(writingJson.encodeToString(JsonObject.serializer(), merged))
    }

    /** Creates the `.ja2` and `.ja2/mods` directories while they are still missing. */
    fun ensureDirectories(): Boolean {
        var success = true
        if (!configDir.isDirectory && !configDir.mkdirs()) {
            Log.w(CONFIG_LOG_TAG, "Could not create ${configDir.absolutePath}")
            success = false
        }
        if (!modsDir.isDirectory && !modsDir.mkdirs()) {
            Log.w(CONFIG_LOG_TAG, "Could not create ${modsDir.absolutePath}")
            success = false
        }
        return success
    }

    /**
     * Builds the JSON that is written to disk: every managed key that carries a value, followed by
     * the keys the launcher does not know about.
     */
    private fun mergeForWrite(config: Ja2Json, unmappedFields: JsonObject): JsonObject {
        val managedFields = managedFieldsOf(config)
        return buildJsonObject {
            for ((key, value) in managedFields) {
                if (value !is JsonNull) {
                    put(key, value)
                }
            }
            for ((key, value) in unmappedFields) {
                if (key !in managedFields.keys && value !is JsonNull) {
                    put(key, value)
                }
            }
        }
    }

    private fun unmappedFieldsOf(storedObject: JsonObject): JsonObject {
        val managedKeys = managedFieldsOf(Ja2Json()).keys
        val unmapped = storedObject.filterKeys { key -> key !in managedKeys }
        return if (unmapped.isEmpty()) EMPTY_JSON_OBJECT else JsonObject(unmapped)
    }

    /**
     * Encodes [config] with all defaults enabled, which yields exactly the keys the launcher
     * manages - including the ones that are currently unset.
     */
    private fun managedFieldsOf(config: Ja2Json): JsonObject =
        defaultsJson.encodeToJsonElement(Ja2Json.serializer(), config) as? JsonObject ?: EMPTY_JSON_OBJECT

    private fun backupCorruptFile() {
        try {
            configFile.copyTo(backupFile, overwrite = true)
            Log.i(CONFIG_LOG_TAG, "Backed up unreadable ${configFile.name} to ${backupFile.name}")
        } catch (e: Exception) {
            Log.w(CONFIG_LOG_TAG, "Could not back up ${configFile.name}: ${e.message}")
        }
    }

    private fun writeAtomically(text: String) {
        val tempFile = File("${configFile.absolutePath}.tmp")
        try {
            tempFile.writeText(text)
            if (tempFile.renameTo(configFile)) {
                return
            }
            // Replacing an existing file is not always allowed, fall back to deleting it first.
            configFile.delete()
            if (tempFile.renameTo(configFile)) {
                return
            }
            throw IOException("Could not write ${configFile.absolutePath}")
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private companion object {
        /** Used for reading: tolerant about unknown keys, comments and trailing commas. */
        val readingJson: Json = Json {
            ignoreUnknownKeys = true
            allowComments = true
            allowTrailingComma = true
            isLenient = true
        }

        /** Used to list the managed keys, where defaults have to show up. */
        val defaultsJson: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        /** Used for writing the file: pretty printed, unset values are omitted. */
        val writingJson: Json = Json {
            prettyPrint = true
            encodeDefaults = false
        }
    }
}
