# PR 01 — Scaffolding: Detailed Spec

> Companion to `docs/DEVELOPMENT_PLAN.md` § PR 01.
> This document provides the step-by-step execution plan for the first PR.

## Goal Recap

Establish the Gradle build infrastructure (version catalog + convention plugins + module skeleton) so that subsequent PRs can focus on code rather than build configuration.

**End state:** `./gradlew build` passes. App launches on emulator. Hello Compose displays (AS default). 12 empty modules exist, all using the new convention plugins.

## Prerequisites

Before starting this PR:

- [ ] Android Studio project exists, was created from "Empty Activity (Compose)" template.
- [ ] AS default "Hello Compose" screen successfully runs on an emulator at least once.
- [ ] Initial commit on `main` contains the AS-generated project.
- [ ] Git remote set to the personal GitHub account (verify with `git remote -v`).
- [ ] `CLAUDE.md` and `docs/*.md` all committed to `main`.

## Execution Order Philosophy

**Build infrastructure has forward-only dependencies.** Getting the order wrong causes cascading failures. The correct order:

```
1. Version catalog        (libs.versions.toml)
    ↓
2. Settings configuration  (settings.gradle.kts, enableFeaturePreview)
    ↓
3. Convention plugins      (build-logic/)
    ↓
4. Empty module skeleton   (12 modules, minimal build.gradle.kts)
    ↓
5. :app migration          (use convention plugin)
    ↓
6. Verify build            (./gradlew build)
```

Each step must succeed before moving to the next.

---

## Step 1: Version Catalog

### What

Create `gradle/libs.versions.toml` with all dependencies anticipated across the 7 PRs.

### Why now

Convention plugins will reference these aliases. Without the catalog, plugin definitions won't compile.

### File to create

`gradle/libs.versions.toml` — full content specified in `CLAUDE.md` § "Version Catalog" earlier in our conversation. Summary of critical sections:

- **`[versions]`**: AGP 8.7+, Kotlin 2.1+, composeBom 2024.12+, Hilt 2.53+, Retrofit 2.11+, Room 2.6+, DataStore 1.1+, kotlinx-serialization 1.7+, Coroutines 1.9+, play-services-location 21.3+, Compose Compiler matching Kotlin.

- **`[libraries]`**: All androidx, Compose, Hilt, network (Retrofit, OkHttp, kotlinx-serialization), Room, DataStore, play-services-location, Coil, testing libraries (JUnit4, MockK, Turbine, kotlinx-coroutines-test, truth).

- **`[plugins]`**:
  - Standard plugins: `android-application`, `android-library`, `kotlin-android`, `kotlin-jvm`, `kotlin-serialization`, `compose-compiler`, `ksp`, `hilt`
  - Our convention plugin aliases: `weatherapp-android-application`, `weatherapp-android-library`, `weatherapp-android-feature`, `weatherapp-android-hilt`, `weatherapp-jvm-library`. Each has `version = "unspecified"`.

### Verification

After creating the file:
```bash
./gradlew help
```

- ✅ Success: Gradle parses without error (even though nothing uses the catalog yet).
- ❌ Failure: "Invalid catalog definition" → re-check TOML syntax, indentation, quotes.

### Common pitfalls

| Issue | Cause | Fix |
|-------|-------|-----|
| `Plugin not found` for `weatherapp.*` | Convention plugins don't exist yet | Expected — Step 3 fixes this |
| TOML parse error | Tabs instead of spaces, or missing quotes | TOML is whitespace-sensitive; use 2-space indent |
| Wrong Kotlin/Compose Compiler combination | Kotlin 2.1.0 needs Compose Compiler 2.1.0 | Keep versions in lockstep |

---

## Step 2: Settings Configuration

### What

Update `settings.gradle.kts` to:
1. Enable type-safe project accessors.
2. Declare `build-logic` as an included build.
3. Include all 12 modules (even though they're still empty).

### File to modify

`settings.gradle.kts` at repo root:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "WeatherForecast"

include(":app")

// Core modules
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:location")
include(":core:domain")
include(":core:data")

// Feature modules
include(":feature:weather")
include(":feature:citylist")
```

### Verification

```bash
./gradlew projects
```

- ✅ Success: Lists root project and all 12 sub-projects (`:app`, `:core:model`, … `:feature:citylist`).
- ❌ Failure: "Project directory does not exist" → Expected for this step; Step 4 creates directories. Temporarily comment out missing include lines, or jump to Step 4 first if preferred.

### Common pitfalls

| Issue | Fix |
|-------|-----|
| `Project directory 'core/model' does not exist` | Expected at this step. Either create directories now or skip to Step 4 and revisit. |
| `includeBuild("build-logic")` fails | `build-logic/` must exist as a directory with its own `settings.gradle.kts`. Step 3 creates this. |

**Recommendation:** Execute Steps 2–4 together as a group, or temporarily comment out the `include` lines until the directories exist.

---

## Step 3: Convention Plugins (`build-logic/`)

### What

Create the `build-logic/` included build containing five convention plugins. These plugins encapsulate Gradle configuration shared across modules.

### Directory structure to create

```
build-logic/
├── settings.gradle.kts
├── build.gradle.kts              (root — empty or stub)
└── convention/
    ├── build.gradle.kts          (the actual Kotlin Gradle plugin project)
    └── src/main/kotlin/
        ├── AndroidApplicationConventionPlugin.kt
        ├── AndroidLibraryConventionPlugin.kt
        ├── AndroidFeatureConventionPlugin.kt
        ├── AndroidHiltConventionPlugin.kt
        ├── JvmLibraryConventionPlugin.kt
        └── (supporting) KotlinAndroid.kt, AndroidCompose.kt, etc.
```

### Files to create

**`build-logic/settings.gradle.kts`:**
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    // Read the same version catalog as the main build
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

**`build-logic/convention/build.gradle.kts`:**
```kotlin
plugins {
    `kotlin-dsl`
}

group = "com.example.weatherforecast.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)       // see note below
    compileOnly(libs.kotlin.gradle.plugin)        // see note below
    compileOnly(libs.compose.gradle.plugin)       // see note below
    compileOnly(libs.ksp.gradle.plugin)           // see note below
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "weatherapp.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "weatherapp.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "weatherapp.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "weatherapp.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "weatherapp.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
```

**Note on plugin classpath dependencies:** The `libs.android.gradle.plugin` etc. aliases refer to entries like `android-gradle-plugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }` in the catalog. Add them if missing.

### Convention plugin contents

Each plugin class applies base configuration. Claude CLI should generate them following the Now in Android pattern. Key snippets:

**`AndroidLibraryConventionPlugin.kt`** (representative):
```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 36
            }
        }
    }
}
```

Where `configureKotlinAndroid` is a shared helper in `KotlinAndroid.kt`:
```kotlin
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = 36
        defaultConfig { minSdk = 29 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    configureKotlin()
}

private fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
}
```

**`AndroidFeatureConventionPlugin.kt`** applies `weatherapp.android.library` + Compose + Hilt + ViewModel + Navigation, and adds dependencies on `:core:designsystem`, `:core:model`, `:core:common`, `:core:domain`.

**`AndroidHiltConventionPlugin.kt`** applies `com.google.devtools.ksp` + `dagger.hilt.android.plugin` and adds `ksp(libs.hilt.compiler)`.

**`JvmLibraryConventionPlugin.kt`** applies `org.jetbrains.kotlin.jvm` with `jvmTarget = 17`.

**`AndroidApplicationConventionPlugin.kt`** mirrors `AndroidLibrary` but uses `ApplicationExtension` and `com.android.application`.

### Verification

```bash
./gradlew build-logic:convention:build
```

- ✅ Success: build-logic compiles; convention plugins are registered.
- ❌ Failure: Usually classpath issues. Check `compileOnly` dependencies in `build-logic/convention/build.gradle.kts`.

### Common pitfalls

| Issue | Fix |
|-------|-----|
| `Could not resolve com.android.tools.build:gradle` | Missing library alias in catalog. Add: `android-gradle-plugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }` |
| `kotlin-dsl` plugin version warning | Usually benign. Keep Gradle wrapper version current (8.10+). |
| Convention plugin class not found | `implementationClass` in `gradlePlugin { register }` must match the file's class name exactly (no package prefix since files are in `src/main/kotlin/` root). |

---

## Step 4: Empty Module Skeleton

### What

Create directory structure for all 12 modules, each with a minimal `build.gradle.kts` using the relevant convention plugin.

### For each module

Create:
```
<module-path>/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml           (Android modules only)
    └── kotlin/
        └── .gitkeep                   (force git to track empty dir)
```

### Module-specific `build.gradle.kts` skeletons

**`:core:model/build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.weatherapp.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
```

**`:core:common/build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.weatherapp.jvm.library)
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
}
```

**`:core:designsystem/build.gradle.kts`:** Android library + Compose.

**`:core:network/build.gradle.kts`:** Android library + Hilt + kotlinx-serialization. Dependencies: `projects.core.common`, `projects.core.model`, retrofit, okhttp, kotlinx-serialization.

**`:core:database/build.gradle.kts`:** Android library + Hilt + KSP. Dependencies: `projects.core.common`, `projects.core.model`, Room.

**`:core:datastore/build.gradle.kts`:** Android library + Hilt. Dependencies: `projects.core.common`, `projects.core.model`, DataStore Preferences.

**`:core:location/build.gradle.kts`:** Android library + Hilt. Dependencies: `projects.core.common`, `projects.core.model`, play-services-location.

**`:core:domain/build.gradle.kts`:** Android library + Hilt (for `@Inject` on UseCases). Dependencies: `projects.core.common`, `projects.core.model`.

**`:core:data/build.gradle.kts`:** Android library + Hilt. Dependencies: `projects.core.domain`, all other core modules.

**`:feature:weather/build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.feature)
}

android {
    namespace = "com.example.weatherforecast.feature.weather"
}
```

**`:feature:citylist/build.gradle.kts`:** Same shape as `:feature:weather`.

### Android Manifest

For Android library modules, each requires a minimal `AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

### Verification

```bash
./gradlew projects
```

- ✅ All 12 modules listed.

```bash
./gradlew :core:model:build
```

- ✅ First pure-JVM module builds.

```bash
./gradlew :core:network:build
```

- ✅ First Android library module builds (should find nothing to compile — empty source — but succeed).

### Common pitfalls

| Issue | Fix |
|-------|-----|
| `Namespace not specified` | Android library modules need `android { namespace = "..." }`. Feature modules use the convention plugin for defaults but still need the namespace per module. |
| Empty source set causes warnings | Expected. `.gitkeep` in `src/main/kotlin/` is enough. |
| `projects.core.model` not resolving | Ensure `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` is in `settings.gradle.kts`. |

---

## Step 5: Migrate `:app` to Convention Plugin

### What

Update `:app/build.gradle.kts` to use `weatherapp.android.application` instead of inline configuration.

### Before (AS default, simplified)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.weatherforecast"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.weatherforecast"
        minSdk = 29
        targetSdk = 36
        // ...
    }
    // ...
}
```

### After

```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.application)
}

android {
    namespace = "com.example.weatherforecast"
    defaultConfig {
        applicationId = "com.example.weatherforecast"
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

The convention plugin handles Compose + Kotlin + compile/target SDK. Only app-specific bits (applicationId, version) stay in `:app/build.gradle.kts`.

### Verification

```bash
./gradlew :app:build
./gradlew :app:installDebug
```

- ✅ Success: App installs on emulator.

### Common pitfalls

| Issue | Fix |
|-------|-----|
| Duplicate plugin application | Convention plugin applies Compose / Kotlin plugins already — don't re-apply in `:app/build.gradle.kts`. |
| `namespace` missing | Must be explicit in `:app/build.gradle.kts` — convention plugin doesn't set it (each module has its own namespace). |

---

## Step 6: Full Build Verification

### Commands

```bash
# Full clean build — catches any hidden issues
./gradlew clean build

# Specifically build and install the app
./gradlew :app:installDebug

# Launch on emulator, verify Hello Compose appears
```

### All DoD Checks

- [ ] `./gradlew clean build` completes with BUILD SUCCESSFUL.
- [ ] `./gradlew :app:assembleDebug` produces `:app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Emulator shows Hello Compose (AS default `Greeting("Android")`).
- [ ] Android Studio's Project panel shows all 12 modules in the Android view.
- [ ] Every `build.gradle.kts` uses `alias(libs.plugins.*)` — no direct plugin ID strings, no hardcoded versions.
- [ ] `:feature:*` modules apply `weatherapp.android.feature` (one line).
- [ ] `:core:*` modules apply the appropriate convention plugin.
- [ ] `settings.gradle.kts` includes `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`.
- [ ] `build-logic/convention/` contains 5 convention plugin classes, all registered in `build-logic/convention/build.gradle.kts`.

### Quick grep checks

```bash
# Should return nothing (no hardcoded dependency versions)
grep -rE 'implementation\("[^)]*:[0-9]+\.[0-9]+' --include="build.gradle.kts"

# Should return nothing (no project(":...") — should be projects.xxx)
grep -r 'project(":' --include="build.gradle.kts"
```

Both greps returning nothing confirms clean conventions.

---

## Commit Strategy

Break this PR's work into 4–6 focused commits for a clean history:

```
chore: add version catalog with anticipated dependencies
chore: set up build-logic with convention plugins
chore: create empty module skeleton for 12 modules
chore: migrate :app to use application convention plugin
chore: verify clean build and update .gitignore if needed
```

Each commit compiles at minimum; later commits may be needed to fully succeed the full build. Keep unrelated changes out.

---

## Going to Next PR

Once DoD is fully checked:

1. Open PR on GitHub with description referencing `docs/DEVELOPMENT_PLAN.md § PR 01`.
2. Self-review.
3. Merge to `main`.
4. Delete feature branch.
5. Update `CLAUDE.md`'s "Current PR" section for PR 02.
6. Start `docs/prs/PR02_CORE_FOUNDATIONS.md` authoring (I'll produce it after you finish PR 01).

---

## Claude CLI: How to Work on This PR

When starting a session for this PR:

1. Read `CLAUDE.md`, then this file top to bottom before writing anything.
2. Execute steps **in order**. Do not jump ahead.
3. After each Step, run its verification command. If it fails, debug before moving on.
4. Prefer **small, additive changes**: add one module's `build.gradle.kts` at a time, not all 12 at once, so you can rebuild incrementally.
5. **Do not add any application logic** (no Hilt app, no custom activity, no theme changes). This PR is pure infrastructure.
6. If a convention plugin appears to need more than the snippets shown above, consult Now in Android's `build-logic/convention/src/main/kotlin/` directory as reference.
7. Use this file itself as a checklist — every heading corresponds to a task.

## Post-PR Retrospective (fill in after merge)

After merging PR 01, add a brief note here covering:

- What took longer than expected?
- Any DoD item you wanted to cut but shouldn't?
- Any convention plugin design you'd revisit in PR 02?

This becomes input for refining future PR specs.
