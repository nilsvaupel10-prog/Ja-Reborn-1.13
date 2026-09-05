# JA2 Reborn

JA2 Reborn is an Android-focused Jagged Alliance 2 port. It is built on top of [Jagged Alliance 2 Stracciatella](https://github.com/ja2-stracciatella/ja2-stracciatella), keeps the original native game engine, and adds an Android launcher, modern build setup, touch controls, Android storage handling, and optional gameplay helpers for mobile play.

This project is a tribute to the original JA2 Stracciatella team and their long-running preservation work. Without that foundation, this Android port would not exist.

The project does not include Jagged Alliance 2 game data. You need a legally owned copy of the original game files.

## Status

JA2 Reborn is maintained as a hobby project with limited available time. There is no fixed update schedule, and new releases or improvements will only happen when I have time and interest to work on them.

Bug reports are welcome, but fixes can only be considered when the issue is reproducible for me or includes enough detail to understand and verify the problem.

Everyone is welcome to fork the project and continue experimenting with it within the terms of the [LICENSE](LICENSE).

The Android port currently supports:

- Release APK builds for all configured Android ABIs
- Android 7.0+ devices, including Android 16
- Android launcher for game data path, save path, resolution, scaling, language, and mouse mode
- Opt-in GitHub release update checker with a manual launcher update button, APK verification, and explicit install confirmation
- Safe launcher resolution presets for Modern, High Res (More Map), and Retro 640x480 play
- Expert Settings for manual resolution, scaling, and legacy control choices
- Fixed-path game data loading
- Android 11+ all-files access support
- Android 7-10 legacy storage permission fallback
- SDL statically linked into `libja2.so`
- OpenSL ES audio backend tuning for stable playback
- Modern Controls and Hardware mouse/keyboard input modes, plus legacy absolute mouse and touchscreen modes
- Modern Controls cursor movement with single-tap left click, two-finger right click, double click, and held-click drag
- Direct tactical bottom-panel touch handling for panel buttons and inventory drag/drop
- Tactical UI scaling with adaptive default action-panel and overlay sizing for phones and tablets
- Modular in-game touch overlay with editable buttons and JSON persistence
- Bundled default touch overlay preset
- Team-panel portrait touch selection and long-press multi-select
- Sector-exit overlay actions
- Optional in-game tutorial overlay
- One-time main menu touch-control hint panel
- Optional cheat system with launcher and in-game overlay controls
- Separate Map Screen touch overlay with editable buttons, mouse buttons, and toggle-key modifiers
- Map Screen touch input mode selection for Direct Touch, Touchpad Mouse, or combined input
- Shopkeeper/Vendor touch overlay support using the tactical button layout
- SVG icon set and touch overlay rework: 37 icons, shape-aware hit-testing, toggle-keys, sidestep/backstep toggle, reload preset, CTRL Examine preset, configurable direct-tap arbitration
- One-time start notice explaining that the Examine touch-overlay button can be added from the new-button menu
- One-time forced touch-layout refresh for pre-1.0.4 layouts only, with a localized reset notice
- Widescreen-aware UI scaling for Auto-Bandage, Shopkeeper, tactical messages, and NPC dialogue
- Native crash log export next to emergency savegames when a recoverable crash report can be written

## Repository Layout

```text
android/        Android app, Gradle build, launcher, SDL Java bridge
assets/         Distribution assets and bundled data
cmake/          CMake helper modules
dependencies/   Third-party source dependencies used by the native build
docs/           Build and project documentation
rust/           Rust crates used by the Stracciatella engine
src/            Native JA2 Stracciatella engine and Android JNI bridges
```

## Controls

The launcher exposes four input modes:

- `Modern Controls`: uses swipes to move a virtual cursor and taps to click.
- `Hardware mouse/keyboard`: passes physical mouse and keyboard input directly to the JA2 engine. The touch overlay is disabled in this mode.
- `Absolute mouse` (legacy): maps finger coordinates directly to the game cursor.
- `Touchscreen` (legacy): forwards native touch events.

In Modern Controls mode:

- One-finger tap sends left click.
- Two-finger tap sends right click.
- Quick double tap sends double click.
- Double tap and hold keeps the left mouse button held for drag actions.
- Tactical bottom-panel touches are routed directly to the JA2 interface for panel controls and inventory movement.
- A two-finger tap on the tactical bottom panel toggles team portraits and single-merc inventory view.

The touch overlay is available in the tactical game screen, Shopkeeper/Vendor screens, and the Map Screen. Tactical and Map Screen layouts have separate button configurations; Shopkeeper/Vendor screens use the tactical layout. The overlay can be unlocked in-game to edit button layout, actions, icon sizing, and presets, including the CTRL Examine button. 37 SVG icons provide crisp rendering at any button size, and buttons use shape-aware hit-testing for precise touch detection. Modifier keys (Shift, Ctrl, Alt) support a sticky toggle mode with visual feedback.

On the Map Screen, touch input can be switched between Direct Touch, Touchpad Mouse, or a combined mode from the overlay settings.

## Resolution Modes

The Android launcher defaults to safe resolution presets:

- `Modern`: recommended default with readable UI and aspect-correct scaling. Non-game screens, menus, and the Map Screen are presented in a centered 4:3 area. Widescreen-specific fixes keep Auto-Bandage, Shopkeeper, tactical messages, and NPC dialogue correctly positioned on wide displays.
- `High Res (More Map)`: shows more tactical map area with a smaller UI.
- `Retro`: fixed classic `640x480` presentation.

Manual resolution, scaling, and legacy control choices are available through `Expert Settings`.

## Configuration Files

Runtime configuration is stored under the app's `.ja2` directory.

Common files:

```text
ja2.json              Launcher/game configuration
mods/                 Mod folders that can be enabled in the launcher
touch_buttons.json    Touch overlay layout and settings
iconset.json          SVG icon metadata (fill, offset, scale, rotation, flip)
iconmappings.json     Game icon name to iconset entry mappings
cheats.json           Optional cheat configuration
tutorial.set          Tutorial visibility preference
mainmenu_tutorial.set Main menu hint visibility preference
touch_preset_update_notice.set Touch-layout reset notice preference
touch_overlay_feature_notice.set One-time touch-overlay feature notice preference
crashlog-latest.txt   Latest native crash report, when available
```

## Mods

The launcher manages the Virtual File System mods of the upstream engine. Mod folders are discovered
in `.ja2/mods`, and the mods bundled in the APK assets are listed as well. `Manage Mods` on the Data
tab enables, disables, and orders them, and the resulting folder names are stored in the `mods` array
of `ja2.json`:

```json
{
    "game_dir": "/storage/emulated/0/JA2",
    "mods": [
        "stracciatella-gun-pack",
        "wildfire-maps"
    ]
}
```

The last entry of that list has the highest priority, so it overrides the files of the mods above it -
the same rule the desktop launcher documents. Saving the configuration keeps every other key of
`ja2.json`, including settings that have no launcher UI such as `brightness`.

## Documentation

- [Android build instructions](docs/BUILDING_ANDROID.md)
- [Android port feature documentation](docs/ANDROID_PORT_FEATURES.md)
- [Android scaling plan](docs/SCALING_PLAN.md)
- [Release process](docs/RELEASING.md)

## Building

Android build instructions are maintained separately in [docs/BUILDING_ANDROID.md](docs/BUILDING_ANDROID.md).

Short version:

```powershell
cd android
.\gradlew.bat :app:assembleRelease
```

The first build after deleting caches, or any build after SDL Java / CMake integration changes, should use:

```powershell
.\gradlew.bat :app:assembleRelease --rerun-tasks
```

## Game Data

This port expects the user to provide original Jagged Alliance 2 data files. The Android launcher lets you enter the game data directory and save directory manually.

Storage behavior:

- Android 11+ uses all-files access for fixed-path native reads.
- Android 7-10 uses legacy runtime storage permissions.

## Upstream

This repository is based on JA2 Stracciatella. JA2 Reborn is not an official Stracciatella release. The Android port integration was prepared from the upstream base commit:

```text
6cd7bc2ab49d88e95ff58b3300d232ace048fc37
```

The latest upstream commit synchronized into `experimental` is:

```text
a3bd56e65e6adfd9f12994ddc1a523a0be784bf3
```

Local Android port work remains on `experimental` until it has been manually tested and approved for merge into `main`.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the public change history.

## License

The project source is distributed under the Strategy First Inc. Source Code License Agreement. See [LICENSE](LICENSE).

Third-party dependencies and bundled mods may include their own license files. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Jagged Alliance 2 game assets are not included and remain the property of their respective owners.
