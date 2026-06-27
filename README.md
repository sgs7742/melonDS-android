# melonDS Android (dual fork)

Personal fork of [melonDS Android](https://github.com/rafaelvcaetano/melonDS-android), based on **Beta 1.7.0**, with local customizations. The emulator core lives in [melonDS-android-lib](https://github.com/rafaelvcaetano/melonDS-android-lib).

Upstream repo: https://github.com/rafaelvcaetano/melonDS-android  
This fork: https://github.com/sgs7742/melonDS-android_dual (branch `dual-for-local` for local builds)

|Rom List|Dark Theme|Pocket Physics|Layout Editor|
|---|---|---|---|
|![Screenshot 1](./.github/images/screenshot_mobile0.png)|![Screenshot 2](./.github/images/screenshot_mobile1.png)|![Screenshot 3](./.github/images/screenshot_mobile2.png)|![Screenshot 4](./.github/images/screenshot_mobile3.png)|

## Fork changes

### Three install variants (side-by-side)

This project builds **three separate apps** that can be installed on the same device at once. Each variant has its own package name, settings, and save data.

| Flavor | Package name | App label |
|--------|--------------|-----------|
| `dual` | `me.magnum.melonds.dual` | melonDS Dual |
| `dual2` | `me.magnum.melonds.dual2` | melonDS Dual2 |
| `dual3` | `me.magnum.melonds.dual3` | melonDS Extended |

Debug builds append `.dev` to the package name (for example `me.magnum.melonds.dual2.dev`).

### Other local customizations

* Cheat backup / restore
* `.cht` cheat import and in-app cheat add / edit
* Layout editor improvements (centering, file operations, and related UI)

# What is working

* Device scanning for ROMs
* Games can boot and run
* Sound
* Input
* Mic input
* Game saves
* Save states
* Rewind
* AR cheats
* GBA ROM support
* DSi support (experimental)
* Controller support
* Customizable layouts
* Settings

# What is missing

* Wi-Fi
* OpenGL renderer
* Customizable button skins
* More display filters

# Performance

Performance is solid on 64-bit devices with thread rendering and JIT enabled, and should run at full speed on flagship devices. Performance on older devices, especially 32-bit devices, is very poor due to the lack of JIT support.

# Integration with third-party frontends

Each install variant uses its **own package name**. Launch the emulator activity with the absolute path to the ROM file.

| Variant | Package name | Activity |
|---------|--------------|----------|
| dual | `me.magnum.melonds.dual` | `me.magnum.melonds.ui.emulator.EmulatorActivity` |
| dual2 | `me.magnum.melonds.dual2` | `me.magnum.melonds.ui.emulator.EmulatorActivity` |
| dual3 | `me.magnum.melonds.dual3` | `me.magnum.melonds.ui.emulator.EmulatorActivity` |

Intent extra:

* `PATH` — absolute path to the NDS ROM (ZIP files are supported)

# Building

Requirements: **Android SDK**, **NDK** (`21.3.6528147`), and **CMake** (`3.18.1`).

## 1. Clone

```bash
git clone --recurse-submodules https://github.com/sgs7742/melonDS-android_dual.git
cd melonDS-android_dual
git switch dual-for-local
```

## 2. `local.properties`

Create `local.properties` in the project root (Android Studio can generate `sdk.dir` for you). Release builds also need signing entries:

```properties
sdk.dir=/path/to/Android/Sdk
MELONDS_KEYSTORE=/path/to/keystore.jks
MELONDS_KEYSTORE_PASSWORD=your_store_password
MELONDS_KEY_ALIAS=your_key_alias
MELONDS_KEY_PASSWORD=your_key_password
```

For local testing, the Android debug keystore is fine.

## 3. Build

GitHub release APKs for all three variants:

```bash
# Unix
./gradlew :app:assembleDualGitHubRelease :app:assembleDual2GitHubRelease :app:assembleDual3GitHubRelease

# Windows
gradlew.bat :app:assembleDualGitHubRelease :app:assembleDual2GitHubRelease :app:assembleDual3GitHubRelease
```

Build a single variant:

```bash
./gradlew :app:assembleDual2GitHubRelease
```

Build all GitHub release variants (same three APKs):

```bash
./gradlew :app:assembleGitHubRelease
```

Debug builds:

```bash
./gradlew :app:assembleDualGitHubDebug
```

The first native (C++) build can take **10+ minutes**.

## 4. Output APKs

APKs are under `app/build/outputs/apk/`:

```
app/build/outputs/apk/dualGitHub/release/app-dual-gitHub-release.apk
app/build/outputs/apk/dual2GitHub/release/app-dual2-gitHub-release.apk
app/build/outputs/apk/dual3GitHub/release/app-dual3-gitHub-release.apk
```

Play Store–flavored builds use `playStore` instead of `gitHub` (for example `assembleDual2PlayStoreRelease`).
