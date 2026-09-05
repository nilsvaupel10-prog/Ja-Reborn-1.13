package com.ja2.reborn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ja2.reborn.cheat.CheatOverlayDialog
import com.ja2.reborn.touch.TouchOverlayController
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLSurface
import java.io.File

open class RebornActivity : SDLActivity() {
    /** Same `.ja2` directory the launcher writes to and the native engine reads from. */
    private val configRepository: Ja2ConfigRepository by lazy { Ja2ConfigRepository(applicationContext) }

    /** Configuration of `.ja2/ja2.json`, read once with the defaults as fallback. */
    private val ja2Config: Ja2Json by lazy { configRepository.load().config }

    private var touchOverlayController: TouchOverlayController? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    /**
     * The native engine applies command line arguments *after* `ja2.json`, so a single argument
     * would win over everything the launcher configured - a `--mod` argument would even replace
     * the whole `mods` array. JA2 Reborn is only started from its launcher and never with
     * arguments, so the mod selection stored in `ja2.json` stays authoritative.
     */
    override fun getArguments(): Array<String> {
        return arrayOf()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val mouseMode = ja2Config.mouseMode ?: MouseMode.DEFAULT
        SDLSurface.setTouchscreenMouseMode(mouseMode.value)
        super.onCreate(savedInstanceState)
        SDLActivity.setTutorialLanguage(LanguageManager.getSavedLanguage(this) == LanguageManager.Language.GERMAN)

        val resolutionMode = ja2Config.resolutionMode ?: ResolutionMode.DEFAULT

        logEnabledMods()

        if (mouseMode != MouseMode.HARDWARE) {
            touchOverlayController = TouchOverlayController(
                filesDir = applicationContext.filesDir,
                activity = this,
                root = getContentView() as ViewGroup,
                surface = mSurface,
                resolutionMode = resolutionMode,
                onCheatButtonTapped = {
                    CheatOverlayDialog(this, applicationContext.filesDir).show()
                },
                onImportPreset = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "*/*"))
                    }
                    startActivityForResult(intent, REQUEST_CODE_IMPORT_PRESET)
                }
            )
            touchOverlayController?.attach()
        }
        writeGameSessionFile()
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onPause() {
        touchOverlayController?.releasePressedInputs()
        super.onPause()
    }

    override fun onDestroy() {
        deleteGameSessionFile()
        touchOverlayController?.detach()
        touchOverlayController = null
        super.onDestroy()
    }

    override fun setOrientationBis(w: Int, h: Int, resizable: Boolean, hint: String?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun getLibraries(): Array<String?>? {
        return arrayOf(
            "ja2"
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMPORT_PRESET && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                touchOverlayController?.importPresetFromUri(uri)
            }
        }
    }

    private fun writeGameSessionFile() {
        try {
            val dir = configRepository.configDir
            if (!dir.exists()) dir.mkdirs()
            File(dir, GAME_SESSION_FILE_NAME).writeText("running")
        } catch (e: Exception) {
            Log.w(TAG, "Could not write game session file: ${e.message}")
        }
    }

    private fun deleteGameSessionFile() {
        try {
            val file = File(configRepository.configDir, GAME_SESSION_FILE_NAME)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete game session file: ${e.message}")
        }
    }

    /**
     * Logs the mods the game is started with. The engine resolves them relative to the same `.ja2`
     * home directory (see `find_stracciatella_home` on the Rust side) and mounts their `data`
     * directories into the virtual file system.
     */
    private fun logEnabledMods() {
        val mods = ja2Config.mods ?: emptyList()
        if (mods.isEmpty()) {
            Log.i(TAG, "No mods enabled")
        } else {
            Log.i(TAG, "Enabled mods (lowest to highest priority): ${mods.joinToString(", ")}")
        }
    }

    companion object {
        private const val TAG = "RebornActivity"
        private const val REQUEST_CODE_IMPORT_PRESET = 1001
        private const val GAME_SESSION_FILE_NAME = "game_session"
    }
}
