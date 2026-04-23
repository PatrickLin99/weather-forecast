# Weather Forecast App - Claude Collaboration Guide

> This file is automatically read by Claude CLI and provides context for AI-assisted development.
> Human readers: this is the quickest way to understand the project's rules and conventions.

## Project Overview

An Android weather forecast application demonstrating modern Android architecture:
- Displays current day and weekly weather forecast
- Supports city list management with search
- Uses device location when available, with sensible fallbacks

**Tech stack:** Kotlin · Coroutines · Jetpack Compose · Hilt · Retrofit · Room · DataStore · Flow

## Core Architectural Principles

These are **non-negotiable**. Any generated code must respect them.

### 1. Clean Architecture with strict dependency direction

```
feature/* ──► core:domain (interfaces) ──► (no further outward deps)
                    ▲
core:data ──────────┘ implements interfaces
           ──► core:network, core:database, core:datastore, core:location
```

- **feature modules MUST NOT import** `core:network`, `core:database`, `core:datastore`, or `core:location` directly. They depend only on `core:domain` (interfaces) + `core:model` + `core:common` + `core:designsystem`.
- **core:model MUST NOT depend on Android framework classes.**
- **core:data is the only module** that knows about all data sources.
- Dependency inversion: features depend on abstractions; Hilt wires in implementations at runtime.

### 2. Single Source of Truth (SSOT)

- Room is the single source of truth for all UI-observed data.
- Repositories expose `Flow<T>` backed by Room DAO queries.
- Network calls update Room; UI never observes network directly.
- Offline = read from Room. Online = network updates Room → Flow emits → UI updates.

### 3. Unidirectional Data Flow (UDF)

- ViewModels expose `StateFlow<UiState>` only.
- UI sends events via ViewModel functions.
- UiState is a `sealed interface` with `Loading` / `Success` / `Error` variants.

### 4. MVVM + Compose

- `@HiltViewModel` with constructor injection of UseCases (or Repositories when UseCase adds no value).
- Composables use `collectAsStateWithLifecycle()` to observe state.
- No business logic in Composables.

### 5. Navigation: Callback injection

- Each feature module exposes `NavGraphBuilder.xxxScreen(onNavigateToY: () -> Unit)` extension functions.
- `NavController` lives only in `:app`.
- Feature modules **do not depend on each other**.
- Cross-feature coordination uses **shared state via DataStore Flow**, not direct calls.

## Module Structure

```
:app                     Entry point, NavHost, Hilt setup
:core:model              Pure Kotlin domain models (no Android deps)
:core:common             Result type, AppError, dispatchers, shared utilities
:core:designsystem       Theme, Typography, shared Composables
:core:network            Retrofit, OkHttp, Open-Meteo API, DTOs, mappers
:core:database           Room database, entities, DAOs
:core:datastore          Preferences DataStore (user settings)
:core:location           FusedLocationProvider + Geocoder wrapper
:core:domain             Repository interfaces, UseCases (Android library for Hilt)
:core:data               Repository implementations, data source composition
:feature:weather         Today + weekly forecast screen
:feature:citylist        City list, search, selection
```

See `docs/MODULE_STRUCTURE.md` for detailed package structure of each module.

## Key Conventions

### Error handling
- Use custom `Result<T, AppError>` from `core:common`, **never** Kotlin's built-in `kotlin.Result`.
- `AppError` is a sealed class (see `docs/ERROR_HANDLING.md`).
- Network layer uses a central `apiCall { }` helper to map exceptions to `AppError`.
- UI layer maps `AppError` to user-facing strings in `@Composable` helpers.

### Coroutines & Flow
- Repository methods that return reactive data: `fun observeX(): Flow<T>`
- Repository methods that perform actions: `suspend fun refreshX(): Result<Unit, AppError>`
- ViewModels use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)` for UI state.
- Always use injected `CoroutineDispatcher` (qualified by `@Dispatcher(IO/Default/Main)`), never hardcode `Dispatchers.IO`.

### Dependency Injection
- Hilt only. No Koin, no manual DI.
- Each core module has its own `di/` package with `@Module @InstallIn(SingletonComponent::class)`.
- Bind interfaces → implementations with `@Binds`, not `@Provides`, when possible.

### API Layer
- Base URL: `https://api.open-meteo.com/v1/` (forecast) and `https://geocoding-api.open-meteo.com/v1/` (geocoding)
- No API key required.
- Use `kotlinx.serialization` (not Moshi/Gson).
- DTOs live in `:core:network`, never leak outside the module. Always map to domain models before crossing the module boundary.

### UseCase guideline
- Only create a UseCase when it adds real value (composition, transformation, fallback logic, multi-repository orchestration).
- If a UseCase is `suspend operator fun invoke() = repository.doIt()` — skip it, call the repository directly from ViewModel.

### Version catalog
- All dependencies go through `gradle/libs.versions.toml`.
- Use convention plugins in `build-logic/` to share module configuration.
- Never hardcode versions in module-level `build.gradle.kts`.

## Development Workflow

### PR strategy

| PR | Scope | Emulator result | Status |
|----|-------|-----------------|-------|
| 01 | Scaffolding: build-logic, version catalog, empty module skeleton | Hello Compose (AS default) | ✅ |
| 02 | Core foundations: model, common, network, database, datastore, designsystem | Hello Compose (no UI change) | ⏳ |
| 03 | First vertical: domain, data, feature:weather (Taipei hardcoded, no location yet) | First real weather screen | ⏳ |
| 04 | feature:citylist + search + city switching | Switch cities | ⏳ |
| 05 | core:location + geocoding integration | Auto-detect current location | ⏳ |
| 06 | Polish: pull-to-refresh, °C/°F toggle, refined error UX | Full UX | ⏳ |
| 07 | Tests + README + AI_USAGE.md | Final deliverable | ⏳ |

### Current PR

**PR #02 — Core Foundations**
- Branch: `feat/02-core-foundations`
- Scope: 6 core modules (model, common, network, database, datastore, designsystem)
- Spec: see `docs/prs/PR02_CORE_FOUNDATIONS.md`
- Execution: Single-pass (no per-Step pauses). Final verification via `./gradlew build`.

### Definition of Done (per PR)

- [ ] Code compiles without warnings
- [ ] App launches without crash on emulator
- [ ] Relevant tests (if applicable — see rule #7 below)
- [ ] No new Lint errors
- [ ] Commit messages follow Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`)
- [ ] PR description explains the "why", not just the "what"

## Instructions for Claude CLI

When I ask you to work on this project:

1. **Read the relevant docs first.** Before generating code, check:
   - `docs/ARCHITECTURE.md` for layering questions
   - `docs/MODULE_STRUCTURE.md` for where a file should go
   - `docs/CODING_CONVENTIONS.md` for style questions
   - `docs/ERROR_HANDLING.md` when touching error flows
   - `docs/TECH_DECISIONS.md` to understand *why* something is designed a certain way
   - `docs/prs/PRxx_*.md` for the current PR's detailed spec

2. **Respect module boundaries.** If generating code that crosses modules, verify the dependency direction is allowed. When in doubt, ask.

3. **Prefer minimal, precise changes.** Don't refactor unrelated code. Don't add files "for completeness". Scope matters.

4. **Match existing patterns.** If similar code exists in the project, follow its style (naming, structure, error handling).

5. **Flag architectural concerns.** If an instruction seems to violate a principle above, raise the concern before coding.

6. **Use Traditional Chinese for explanations**, but keep technical terms in English
   (e.g., "use `flatMapLatest` to handle city switching"). Code, identifiers,
   and comments stay in English.

7. **Test files are optional per PR.** If the scope naturally includes testable units
   (Repository, UseCase, ViewModel), including tests is welcome. PR #07 serves as the
   catch-all to ensure comprehensive test coverage by the end.

8. **Flag version changes before applying.** If a build error or compatibility
   issue requires upgrading a dependency version in `libs.versions.toml`,
   stop and confirm with the user before proceeding. Include: the current
   version, target version, reason for upgrade, and any breaking change
   implications. Exceptions: patch-level bumps (x.y.Z) within the same
   minor may be applied directly if needed to fix build errors, with a
   brief note.

## Technical Debt

Known deferred improvements are tracked in `docs/TECH_DEBT.md`.

Before starting each PR, scan `TECH_DEBT.md` for items whose **target resolution** matches the current PR. If found:
- Include the refactor within the PR (if scope allows)
- Use a separate commit: `refactor: resolve TD-NNN <short description>`
- Update the entry's status to `Resolved` with PR number and date

When introducing new deferred decisions during a PR:
- Add a new entry to `TECH_DEBT.md` with a stable ID (next `TD-NNN`)
- Explain *why* it's deferred (not just *what*)
- Identify target resolution PR

## References

- [Now in Android](https://github.com/android/nowinandroid) — primary reference for modularization and convention plugins
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Open-Meteo API docs](https://open-meteo.com/en/docs)
