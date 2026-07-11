# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**qBittorrent Manager** is an Android app (Kotlin, minSdk 28, targetSdk 34) for remotely managing a qBittorrent instance via its Web API.

## Build Commands

```bash
# Assemble debug APK (free flavor by default)
./gradlew assembleFreeDebug

# Assemble release APK
./gradlew assembleFreeRelease

# Run unit tests
./gradlew test

# Run tests for a specific module
./gradlew :app:test

# Run a single test class
./gradlew :app:test --tests "dev.yashgarg.qbit.data.QbitRepositoryTest"

# Check code formatting
./gradlew spotlessCheck

# Auto-fix formatting (also runs automatically on git commit via pre-commit hook)
./gradlew spotlessApply

# Run lint
./gradlew :app:lint
```

## Local Environment (this machine)

`./gradlew` cannot find a JRE by default on this machine — there is no system Java. Use Android Studio's bundled JBR:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

`adb` is not on `PATH`; use the full path or add it to `PATH` for the session:

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

To build and install on the connected phone in one step (installs the debug build for the default `free` flavor — note the task name has no flavor prefix):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug
```

## Module Structure

The project uses Gradle included builds and a version catalog (`gradle/libs.versions.toml`).

- **`:app`** — Main Android application. All UI screens (Fragments + ViewModels), Room database, Hilt DI wiring, WorkManager background sync.
- **`:client-wrapper:client`** — Kotlin Multiplatform Ktor-based HTTP client for the qBittorrent Web API. Handles auth, cookie management, and real-time `MainData` syncing via polling.
- **`:client-wrapper:models`** — KMP data model classes (`Torrent`, `MainData`, `TorrentProperties`, etc.) with `kotlinx.serialization`.
- **`:ui-compose`** — Reusable Jetpack Compose components (`ListTile`, `TorrentContentTreeView`, loading states, theme).
- **`:common`** — Shared Android utilities: notification management, `ContentTreeItem` model for file trees.
- **`:bonsai-core`** — Tree view component used to display torrent file hierarchies.
- **`:benchmark`** — Macro benchmark tests (separate Android test module).
- **`:build-logic`** — Gradle convention plugins: `SpotlessPlugin` (ktfmt formatting), `GitHooksPlugin` (installs pre-commit hook), `KotlinCommonPlugin`, `KotlinAndroidPlugin`.

## Architecture

**Data flow**: `Fragment` → `ViewModel` → `QbitRepository` → `ClientManager` → `QBittorrentClient`

- **MVVM** with Hilt-injected `@HiltViewModel`s. ViewModels expose `StateFlow<ScreenState>` for UI state and `SharedFlow<String>` for one-shot status toasts.
- **`QBittorrentClient`** (`:client-wrapper:client`) is the low-level Ktor HTTP client. It exposes `Flow<MainData>`, `Flow<Torrent>`, etc. via polling/sync.
- **`ClientManagerImpl`** is a `@Singleton` that holds the `QBittorrentClient` instance, re-creating it when the Room-persisted server config changes.
- **`QbitRepository`** wraps all client calls with `runCatching {}` from the `kotlin-result` library, returning `Result<T, Throwable>` (never throws).
- **Room** (`AppDatabase`) stores server connection configs (`ServerConfig`). Schema migrations live in `AppDatabase.kt`.
- **DataStore** stores `ServerPreferences` (theme, other app settings) using a custom Proto serializer.
- **UI**: Mixed stack — traditional XML layouts with `ViewBinding` for most screens, Jetpack Compose for components in `:ui-compose`. Navigation component with Safe Args handles fragment navigation.

## Product Flavors

- **`free`** (default) — No crash reporting; `sentryDsn` is empty.
- **`nonFree`** — Sentry crash reporting enabled via `SENTRY_DSN` environment variable (Play Store build).

## Formatting

Spotless enforces **ktfmt** with `kotlinlangStyle()` for all `.kt` and `.gradle.kts` files. It runs automatically on every `git commit` via the installed pre-commit hook. Run `./gradlew spotlessApply` before committing if you want to format manually first.

## Dependency Management

All versions are centralized in `gradle/libs.versions.toml`. Reference libraries in build files as `libs.<alias>` and plugins as `libs.plugins.<alias>`. Do not add version numbers directly in `build.gradle.kts` files.

## Git Commit Conventions

- Use Conventional Commits (`feat:`, `fix:`, `refactor:`, etc.) for commit messages.
- Do not add a `Co-Authored-By` trailer or any other AI-attribution footer to commits.
