# Android Port Features

This document describes the Android-specific systems added by the JA2 Reborn Android port. It is intended as public project documentation, not as an implementation work log.

## Touch Input

The Android launcher exposes three mouse input modes:

- `Modern Controls`: uses swipes to move a virtual cursor.
- `Absolute mouse` (legacy): maps finger coordinates directly to the game cursor.
- `Touchscreen` (legacy): forwards native touch events to SDL.

Modern Controls mode supports mobile-friendly gestures:

- One-finger tap sends a left mouse click.
- Two-finger tap sends a right mouse click.
- Quick double tap sends a double click.
- Double tap and hold keeps the left mouse button pressed for drag actions.
- Bottom-panel touches are routed directly to the tactical interface for buttons, inventory movement, and portrait interaction.
- Long-press on a merc portrait selects the whole active team.

## Resolution Modes

The Android launcher provides safe resolution presets instead of asking most users to choose raw internal pixel dimensions:

- `Modern`: recommended default with readable UI.
- `High Res (More Map)`: smaller UI with more tactical map visible.
- `Retro`: fixed classic `640x480` mode.

Modern and High Res calculate aspect-correct internal resolutions from the current landscape display size. Ultrawide phones remain classified as phones even when they have high pixel height, so they do not receive tablet-style UI sizing.

Expert Settings can be enabled when a user wants manual resolution, scaling, or legacy control mode choices. In standard mode the launcher keeps safe defaults for scaling and controls.

Menus, splash screens, videos, and the map screen are presented in a centered `640x480` area when the game is running at wide Modern or High Res internal resolutions. This keeps classic screens from being stretched or horizontally squeezed.

## Touch Overlay

The original fixed Android mouse buttons were replaced by a modular in-game overlay. Overlay buttons are stored as data and can be edited without rebuilding the app.

The overlay provides:

- Floating action buttons rendered above the SDL surface.
- A fixed system button bar for layout editing and utility actions.
- Editable button size, opacity, shape, preset action, and position.
- Drag-to-position behavior when the layout is unlocked.
- Persistent layout storage in `touch_buttons.json`.
- Robust release handling for held mouse, keyboard, combo, and DPAD inputs.
- Import and export of overlay presets.
- Mode-aware action panel scaling defaults.
- An option to disable mouse-edge map scrolling while keeping keyboard and drag scrolling available.

Overlay button positions and sizes are normalized relative to the screen, so layouts survive different screen sizes and orientation changes.

## Android Scaling

The Android port uses a conservative scaling model to keep the original JA2 UI stable while making tactical controls readable on modern devices.

- The launcher recommends half of the native landscape resolution when that remains above 640x480.
- `Near Perfect with Oversampling` is the recommended default video scaling mode.
- Tactical action-panel presentation can scale from 100% to 130% without rebuilding native UI state.
- Scaled panel input is mapped back to the original tactical coordinates before it reaches the JA2 interface.
- Touch overlay defaults adapt to phone, tablet, and landscape aspect-ratio profiles while preserving user-edited layouts.

See [Android scaling plan](SCALING_PLAN.md) for the full strategy and future-work rules.

### Preset Actions

The preset catalog covers common JA2 actions:

- Mouse buttons: left, right, middle.
- Movement and stance actions: stand, crouch, prone, run, stealth, reverse/strafe.
- Combat helpers: fire mode, range cursor, target cycling, auto bandage.
- Tactical UI: map, options, end turn, blink items, pause, quick save/load.
- Merc and squad selection shortcuts.
- Sector-exit controls.
- A configurable DPAD for directional map control.

The DPAD is implemented as a single overlay button with four direction zones. It sends held directional key events and releases them reliably on pointer-up, cancel, pause, or activity teardown.

## Cheat System

The Android port includes an optional cheat system designed for mobile testing and convenience. Cheats are disabled by default and are controlled through both launcher settings and an in-game overlay dialog.

Supported toggles include:

- Master enable switch.
- God mode.
- Non-lethal player damage.
- Full medical healing.
- Unlimited ammo.
- No weapon jams.
- Unlimited action points.
- Unlimited breath.
- Reveal enemies.
- Reveal items.
- One-hit kill.
- Perfect hit chance.

The cheat configuration is stored in `cheats.json`. Runtime changes are sent to the native engine through a JNI bridge.

### Safety Rules

Gameplay hooks are intentionally scoped:

- Player-benefit cheats target `OUR_TEAM` only.
- Enemy, NPC, and militia behavior is not changed unless a feature explicitly requires it.
- Cheat state is not stored in savegames.
- Missing or unknown JSON fields fall back to safe defaults.
- One-shot and advanced cheats should be added through explicit UI and native entry points, not by reusing hidden keyboard shortcuts.

## Tutorial Overlay

The port includes an optional first-run tutorial for Android controls. It is rendered by the native game UI so it matches the existing JA2 visual style.

The tutorial system provides:

- Three slide cards for touch control basics.
- English and German text.
- Dot indicators and previous/next navigation.
- A confirmation button.
- A "do not show again" checkbox.
- A toolbar help button that can reopen the tutorial.

Tutorial visibility is persisted in `tutorial.set` under the game profile directory. The tutorial can auto-open on first tactical screen entry and can also be opened manually from the Android overlay controls.

The main menu also has a separate one-time touch-control hint panel. Its visibility is stored in `mainmenu_tutorial.set` and does not affect the tactical tutorial.

While the native tutorial is visible, the Android touch overlay is hidden and native action-panel presentation scaling is suspended so tutorial controls remain tappable.

## Mod Support

The Android launcher exposes the mod support of the upstream engine. Mods are plain folders that
contain a `data` directory with files that shadow or replace the files of the original game, plus an
optional `manifest.json` with `name`, `version`, and `description`.

The launcher discovers mods in the same locations the native `ModManager` uses:

- `<filesDir>/.ja2/mods/<mod-id>` for mods a user installed. The directory is created by the launcher.
- `mods/<mod-id>` inside the APK assets for the mods bundled with the game; those are read-only and
  need a `manifest.json`, which is also a requirement of the engine.

Only names consisting of lowercase letters, digits, and dashes are accepted, because the engine
ignores every other folder. Folders that were skipped are reported in the mod dialog.

`Manage Mods` on the launcher Data tab opens the mod dialog. It lists enabled mods first in load
order, with a check box per mod and up and down buttons for the priority. The `mods` array of
`ja2.json` is written in that order, and the last entry wins over the earlier ones. Missing mod
folders and mods without a `data` directory are marked, since the engine cannot mount them.

Reading and writing `ja2.json` is lossless. Keys the launcher has no UI for, such as `brightness`,
`nosound`, or hand written entries, are kept when the file is written. The engine resolves its
`.ja2` home directory through `Context.getFilesDir()` on Android, so launcher, mod dialog, and game
always use the same files. No command line arguments are passed to the native `SDL_main`, therefore
nothing overrides the mod selection from `ja2.json`.

## Localization

The Android launcher and overlay UI use Android string resources for English and German. The native JA2 game text continues to use the upstream Stracciatella translation data.

Android-side localization covers:

- Launcher configuration labels.
- Settings screens.
- Mouse mode and scaling labels.
- Touch overlay editor and settings dialogs.
- Preset names and action categories.
- Import/export messages.
- Cheat overlay UI.
- Crash and error messages.

The launcher includes a DE/GB language switch. The selected language is stored in app preferences and applied before launcher and game activities create their UI.

## Runtime Configuration

Android-specific runtime files are stored under the app's `.ja2` directory:

```text
ja2.json              Launcher and game configuration
mods/                 Mod folders that can be enabled in the launcher
touch_buttons.json    Touch overlay layout and actions
cheats.json           Optional cheat settings
tutorial.set          Tutorial visibility preference
mainmenu_tutorial.set Main menu hint visibility preference
crashlog-latest.txt   Latest crash report when native crash handling can write one
```

These files are user configuration and should not be committed to the repository.

## Manual Verification

Recommended manual checks before publishing a release APK:

- Start with no existing `touch_buttons.json` and verify the default overlay loads.
- Move, resize, edit, delete, and recreate overlay buttons, then restart the app.
- Test left click, right click, drag, double click, and two-finger right click in tactical view.
- Verify DPAD hold and release behavior, including app pause while pressed.
- Toggle layout lock and confirm positions persist.
- Switch launcher language between English and German and inspect launcher, settings, overlay, and cheat dialogs.
- Open the tutorial automatically on first tactical entry and manually through the help button.
- Verify the main menu touch hint appears once and respects "do not show again".
- Check Modern, High Res, and Retro resolution presets on phone and tablet-class displays.
- Verify Retro bottom-panel and merc portrait touch mapping at 640x480.
- Toggle cheats from launcher and in-game overlay, then verify player-only behavior in tactical gameplay.
- Enable, reorder, and disable mods in the launcher, then verify that `ja2.json` lists the selected folders and that a hand written key such as `brightness` survives the save.
- Build a release APK after deleting caches when native, CMake, SDL Java, or Gradle integration changes were made.

