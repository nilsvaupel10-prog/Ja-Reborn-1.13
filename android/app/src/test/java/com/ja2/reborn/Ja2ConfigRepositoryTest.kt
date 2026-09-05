package com.ja2.reborn

import com.ja2.reborn.mods.InstalledMod
import com.ja2.reborn.mods.ModScanner
import com.ja2.reborn.mods.ModSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers the lossless handling of `ja2.json` and the mod discovery. Both only need file access and
 * therefore run as plain JVM tests.
 */
class Ja2ConfigRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `missing configuration files fall back to the defaults`() {
        val repository = Ja2ConfigRepository(tempFolder.newFolder("files"))

        val snapshot = repository.load()

        assertEquals(Ja2ConfigLoadState.MISSING, snapshot.loadState)
        assertTrue(snapshot.mods.isEmpty())
        assertNull(snapshot.config.vanillaGameDir)
    }

    @Test
    fun `keys the launcher does not manage survive a save`() {
        val repository = Ja2ConfigRepository(tempFolder.newFolder("files"))
        repository.ensureDirectories()
        repository.configFile.writeText(
            """
            {
              // a comment, like the ones the engine writes into default files
              "game_dir": "/storage/emulated/0/JA2",
              "brightness": 0.9,
              "nosound": true
            }
            """.trimIndent()
        )

        val snapshot = repository.load()
        assertEquals(Ja2ConfigLoadState.LOADED, snapshot.loadState)
        assertEquals("/storage/emulated/0/JA2", snapshot.config.vanillaGameDir)
        assertEquals(2, snapshot.unmappedFields.size)

        repository.save(snapshot.withConfig(snapshot.config.copy(mods = listOf("wildfire-maps"))))

        val stored = Json.parseToJsonElement(repository.configFile.readText()).jsonObject
        assertEquals("""["wildfire-maps"]""", stored["mods"].toString())
        assertEquals("/storage/emulated/0/JA2", stored["game_dir"].toString().trim('"'))
        assertEquals("0.9", stored["brightness"].toString())
        assertEquals("true", stored["nosound"].toString())
    }

    @Test
    fun `mods read from ja2 json are reported to the launcher`() {
        val repository = Ja2ConfigRepository(tempFolder.newFolder("files"))
        repository.ensureDirectories()
        repository.configFile.writeText("""{"mods": ["stracciatella-gun-pack", "wildfire-maps"]}""")

        val snapshot = repository.load()

        assertEquals(listOf("stracciatella-gun-pack", "wildfire-maps"), snapshot.mods)
    }

    @Test
    fun `an empty mod selection is written as an empty array`() {
        val repository = Ja2ConfigRepository(tempFolder.newFolder("files"))

        repository.save(Ja2ConfigSnapshot(Ja2Json(mods = emptyList())))

        val stored = Json.parseToJsonElement(repository.configFile.readText()).jsonObject
        assertEquals("[]", stored["mods"].toString())
        assertFalse(stored.containsKey("game_dir"))
    }

    @Test
    fun `unreadable configuration files are reported and backed up`() {
        val repository = Ja2ConfigRepository(tempFolder.newFolder("files"))
        repository.ensureDirectories()
        repository.configFile.writeText("{ not json")

        val snapshot = repository.load()

        assertEquals(Ja2ConfigLoadState.INVALID, snapshot.loadState)
        assertTrue(snapshot.mods.isEmpty())
    }

    @Test
    fun `mod folders are discovered with their manifest and their data directory`() {
        val modsDir = File(tempFolder.newFolder("files"), ".ja2/mods")
        File(modsDir, "wildfire-maps/data").mkdirs()
        File(modsDir, "wildfire-maps/manifest.json").writeText(
            """{"name": "Wildfire Maps", "version": "1.2.0", "description": "New maps"}"""
        )
        File(modsDir, "stracciatella-gun-pack").mkdirs()
        File(modsDir, "Not A Mod").mkdirs()

        val result = ModScanner.scan(modsDir, null)

        assertEquals(listOf("stracciatella-gun-pack", "wildfire-maps"), result.mods.map { it.id })
        assertEquals("stracciatella-gun-pack", result.mods[0].displayName)
        assertFalse(result.mods[0].hasDataDirectory)
        assertEquals("Wildfire Maps", result.mods[1].displayName)
        assertEquals("1.2.0", result.mods[1].version)
        assertEquals("New maps", result.mods[1].description)
        assertEquals(ModSource.INSTALLED, result.mods[1].source)
        assertTrue(result.mods[1].hasDataDirectory)
        assertEquals(listOf("Not A Mod"), result.ignoredEntries)
    }

    @Test
    fun `enabled mods keep their order and unknown ids are reported as missing`() {
        val known = listOf(
            InstalledMod(id = "a-mod", displayName = "A Mod"),
            InstalledMod(id = "b-mod", displayName = "B Mod")
        )

        val (enabled, disabled) = ModScanner.splitByEnabled(
            allMods = known,
            enabledIds = listOf("B-Mod", "ghost-mod", "b-mod")
        )

        assertEquals(listOf("b-mod", "ghost-mod"), enabled.map { it.id })
        assertEquals(ModSource.MISSING, enabled[1].source)
        assertEquals(listOf("a-mod"), disabled.map { it.id })
    }

    @Test
    fun `mod folder names have to be usable as mod ids`() {
        assertTrue(ModScanner.isModIdValid("stracciatella-gun-pack"))
        assertFalse(ModScanner.isModIdValid("Gun Pack"))
        assertFalse(ModScanner.isModIdValid(""))
    }
}
