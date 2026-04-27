# PR 07 — Tests, Documentation, and Delivery: Detailed Spec

> Companion to `docs/DEVELOPMENT_PLAN.md` § PR 07.
> **Execution mode: 3-stage with checkpoints between stages.**
>
> - Stage 1: TD-001 refactor + critical-path tests
> - Stage 2: README.md + AI_USAGE.md
> - Stage 3: Final regression + delivery cleanup
>
> This is the **last PR** of the project. After merge, the repo is the deliverable.

## Goal Recap

Bring the project from "feature complete" to "ready to deliver." Three distinct concerns:

1. **Code quality**: resolve TD-001 (data source visibility) and add tests for critical paths
2. **Documentation**: README.md (entry point for reviewer) and AI_USAGE.md (required deliverable)
3. **Delivery**: final regression sweep, clean build verification, optional release tag

**End state after this PR:**
- All data sources use `interface + internal class XxxImpl + @Binds` pattern (TD-001 closed)
- Repository, UseCase, and ViewModel critical paths have unit tests
- `./gradlew test` passes with no failures
- README.md gives a clear "how to run / what to look at" for reviewer
- AI_USAGE.md tells the honest story of AI collaboration on this project
- All previous emulator scenarios still pass (no regressions)
- Optional: git tag `v1.0.0` marking the deliverable

**What this PR does NOT include:**
- New features
- Tests for every UseCase/Repository (focus on critical path; over-testing inflates scope)
- Performance optimization
- Translation of UI to other languages

## Prerequisites

- [ ] PR 06 merged to `main`
- [ ] Local `main` is up to date
- [ ] `./gradlew clean build` passes
- [ ] All previous emulator scenarios still pass (PR 03–06 baselines)
- [ ] Branch created: `git checkout -b feat/07-tests-and-delivery`
- [ ] `CLAUDE.md` "Current PR" updated to PR 07
- [ ] Scan `docs/TECH_DEBT.md`:
  - **TD-001** — resolved in this PR
  - **TD-002** — already resolved in PR 05
  - **TD-003** — already resolved in PR 06

## Reference Documents (Must-Read)

1. **`docs/ASSIGNMENT.md`** — re-read the original assignment requirements; ensure README addresses every point
2. **`docs/ARCHITECTURE.md`** — README will reference this for "architecture overview"
3. **`docs/TECH_DEBT.md`** — TD-001 spec gives the exact refactor pattern
4. **All `docs/prs/PR0X_*.md` retrospectives** — primary source material for AI_USAGE.md
5. **`docs/CODING_CONVENTIONS.md`** — testing conventions (which test runner, naming patterns)
6. **`docs/ERROR_HANDLING.md`** — `apiCall` and `Result` extensions are testable; covers the pattern for repository tests

---

# STAGE 1: TD-001 Refactor + Critical-Path Tests

**Scope:**
- Refactor 5 data sources from concrete class → interface + impl + `@Binds`
- Write unit tests for critical paths (Repository, UseCase, ViewModel)

**Verification:** `./gradlew test build` passes; all tests green.

**Checkpoint:** Stop and wait for user approval before Stage 2.

## A. TD-001 Refactor

Follow the exact pattern from `docs/TECH_DEBT.md` § TD-001 § "Proposed change".

### 5 data sources to refactor

| Module | Source file | New files |
|---|---|---|
| `:core:network` | `WeatherRemoteDataSource` (concrete) | `WeatherRemoteDataSource` (interface) + `WeatherRemoteDataSourceImpl` |
| `:core:network` | `CityRemoteDataSource` (concrete) | `CityRemoteDataSource` (interface) + `CityRemoteDataSourceImpl` |
| `:core:database` | `WeatherLocalDataSource` (concrete) | `WeatherLocalDataSource` (interface) + `WeatherLocalDataSourceImpl` |
| `:core:database` | `CityLocalDataSource` (concrete) | `CityLocalDataSource` (interface) + `CityLocalDataSourceImpl` |
| `:core:datastore` | `UserPreferencesDataSource` (concrete) | `UserPreferencesDataSource` (interface) + `UserPreferencesDataSourceImpl` |
| `:core:location` | `LocationDataSource` (concrete) | `LocationDataSource` (interface) + `LocationDataSourceImpl` |

**Note:** PR 05's `LocationDataSource` was added with the same `@Inject internal constructor` pattern — refactor it too even though TD-001 didn't list it explicitly. Same conceptual debt.

### Refactor pattern (per data source)

Example for `WeatherRemoteDataSource`:

**Before** (current, concrete):
```kotlin
// :core:network/datasource/WeatherRemoteDataSource.kt
class WeatherRemoteDataSource @Inject internal constructor(
    private val api: OpenMeteoForecastApi,
) {
    suspend fun fetchWeather(city: City, unit: TemperatureUnit): Result<Weather, AppError> = apiCall {
        // ...
    }
}
```

**After** (interface + impl + binding):

```kotlin
// :core:network/datasource/WeatherRemoteDataSource.kt
interface WeatherRemoteDataSource {
    suspend fun fetchWeather(city: City, unit: TemperatureUnit): Result<Weather, AppError>
}
```

```kotlin
// :core:network/datasource/WeatherRemoteDataSourceImpl.kt
internal class WeatherRemoteDataSourceImpl @Inject constructor(
    private val api: OpenMeteoForecastApi,
) : WeatherRemoteDataSource {
    override suspend fun fetchWeather(city: City, unit: TemperatureUnit): Result<Weather, AppError> = apiCall {
        // same body
    }
}
```

```kotlin
// :core:network/di/DataSourceBindModule.kt (new file)
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataSourceBindModule {

    @Binds
    @Singleton
    internal abstract fun bindWeatherRemoteDataSource(
        impl: WeatherRemoteDataSourceImpl,
    ): WeatherRemoteDataSource

    @Binds
    @Singleton
    internal abstract fun bindCityRemoteDataSource(
        impl: CityRemoteDataSourceImpl,
    ): CityRemoteDataSource
}
```

Repeat for `:core:database`, `:core:datastore`, `:core:location`. Each module gets its own `DataSourceBindModule` to keep bindings local.

### Verification per module

After each module's refactor:

```bash
./gradlew :core:network:build
./gradlew :core:database:build
./gradlew :core:datastore:build
./gradlew :core:location:build

# Then full project still wires correctly:
./gradlew :app:kspDebugKotlin
./gradlew build
```

If `:app:kspDebugKotlin` fails, Hilt graph has a missing binding — usually because a `@Provides` somewhere still uses the old concrete type. Find and update.

### TECH_DEBT.md update

Move TD-001 to "Resolved Items" with PR number and date:

```markdown
## Resolved Items

### TD-001: Data source classes should be interfaces

**Resolved**: PR 07, 2026-04-XX

**What was done**

Six data sources (the original five plus `LocationDataSource` from PR 05) refactored to `interface + internal class XxxImpl + @Binds` pattern. Each module gained a small `internal abstract class DataSourceBindModule` to bind interfaces to implementations.

**Effect**

- Test fakes for repository tests now implement the interface directly — much cleaner than mocking concrete classes
- Module boundaries strengthened: external consumers can only see the interface, not the impl
- No functional change

**Files changed**

- 6 new interface files
- 6 renamed files (`Xxx.kt` → `XxxImpl.kt`)
- 4 new `DataSourceBindModule.kt` files (one per data-source-owning module)
```

## B. Critical-Path Tests

### Testing philosophy for this PR

**Don't aim for 100% coverage.** Aim for **confidence in critical paths**. The point is to prove the architecture works under pressure, not to chase a coverage metric.

**Critical paths** (must test):

1. `WeatherRepositoryImpl` — SSOT round-trip (network → Room → Flow)
2. `CityRepositoryImpl` — search short-circuit on blank query
3. `ResolveInitialCityUseCase` — three-branch fallback (location → last-selected → default)
4. `DeleteCityUseCase` — selected-city fallback
5. `ObserveSelectedCityUseCase` — combine + fallback when row missing
6. `WeatherViewModel` — three-way uiState (Loading / Success / Error), refresh trigger on city/unit change
7. `CityListViewModel` — debounce + blank-query short-circuit, search state transitions

**Skip** (not critical path):
- Trivial pass-through Repository methods (e.g., `observeSavedCities = local.observeAllCities()`)
- Pure data class behavior (City, Weather, etc.)
- DataStore wrappers (kotlinx-datastore tested upstream)
- Compose UI tests (out of scope for this PR; would need separate test infrastructure)

### Test infrastructure

Add to `gradle/libs.versions.toml` if not present:

```toml
[versions]
kotlinxCoroutinesTest = "1.8.1"  # use existing kotlinxCoroutines version
turbine = "1.1.0"
mockk = "1.13.12"

[libraries]
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
```

Each module that has tests adds:

```kotlin
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)
testImplementation(libs.mockk)
testImplementation(libs.junit)  // already in catalog
```

### Test files to create

```
core/data/src/test/kotlin/.../repository/
├── WeatherRepositoryImplTest.kt
├── CityRepositoryImplTest.kt
└── (skip UserPreferencesRepositoryImpl — pure pass-through)

core/domain/src/test/kotlin/.../usecase/
├── ResolveInitialCityUseCaseTest.kt
├── DeleteCityUseCaseTest.kt
└── ObserveSelectedCityUseCaseTest.kt

feature/weather/src/test/kotlin/.../
└── WeatherViewModelTest.kt

feature/citylist/src/test/kotlin/.../
└── CityListViewModelTest.kt
```

**That's 7 test files**, each with 3-6 test cases. Total ~30-40 test cases. Manageable in one stage.

### Test patterns

#### Repository test pattern (use `runTest` + Turbine for Flow)

```kotlin
class WeatherRepositoryImplTest {

    private val remote = mockk<WeatherRemoteDataSource>()
    private val local = mockk<WeatherLocalDataSource>(relaxed = true)
    private lateinit var repo: WeatherRepositoryImpl

    @Before
    fun setUp() {
        repo = WeatherRepositoryImpl(remote, local)
    }

    @Test
    fun `observeWeather delegates to local data source`() = runTest {
        val expected = mockWeather()
        every { local.observeWeather(any(), any()) } returns flowOf(expected)

        repo.observeWeather("taipei", TemperatureUnit.CELSIUS).test {
            assertEquals(expected, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `refreshWeather fetches and upserts on success`() = runTest {
        val city = mockCity(id = "taipei")
        val weather = mockWeather()
        coEvery { remote.fetchWeather(city, TemperatureUnit.CELSIUS) } returns Result.Success(weather)
        coEvery { local.upsertWeather(weather) } returns Result.Success(Unit)

        val result = repo.refreshWeather(city, TemperatureUnit.CELSIUS)

        assertTrue(result is Result.Success)
        coVerify { local.upsertWeather(weather) }
    }

    @Test
    fun `refreshWeather propagates network failure without writing to local`() = runTest {
        val city = mockCity()
        coEvery { remote.fetchWeather(any(), any()) } returns Result.Failure(AppError.NoNetwork)

        val result = repo.refreshWeather(city, TemperatureUnit.CELSIUS)

        assertEquals(Result.Failure(AppError.NoNetwork), result)
        coVerify(exactly = 0) { local.upsertWeather(any()) }
    }
}
```

#### UseCase test pattern

`ResolveInitialCityUseCaseTest` — three branches:

```kotlin
class ResolveInitialCityUseCaseTest {

    private val locationRepo = mockk<LocationRepository>()
    private val cityRepo = mockk<CityRepository>(relaxed = true)
    private val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
    private lateinit var useCase: ResolveInitialCityUseCase

    @Before
    fun setUp() {
        useCase = ResolveInitialCityUseCase(locationRepo, cityRepo, prefsRepo)
    }

    @Test
    fun `useLocation true and location succeeds returns location city`() = runTest {
        val locationCity = mockCity(id = "current_location")
        coEvery { locationRepo.getCurrentLocationCity() } returns Result.Success(locationCity)
        every { prefsRepo.selectedCityId } returns flowOf(null)

        val result = useCase(useLocation = true)

        assertEquals(locationCity, result)
        coVerify { cityRepo.saveCity(locationCity) }
        coVerify { prefsRepo.setSelectedCityId(locationCity.id) }
    }

    @Test
    fun `useLocation false uses last-selected city`() = runTest {
        val taipei = mockCity(id = "taipei")
        every { prefsRepo.selectedCityId } returns flowOf("taipei")
        coEvery { cityRepo.getCityById("taipei") } returns taipei

        val result = useCase(useLocation = false)

        assertEquals(taipei, result)
        coVerify(exactly = 0) { locationRepo.getCurrentLocationCity() }
    }

    @Test
    fun `last-selected missing falls back to DefaultCity TAIPEI`() = runTest {
        every { prefsRepo.selectedCityId } returns flowOf(null)

        val result = useCase(useLocation = false)

        assertEquals(DefaultCity.TAIPEI, result)
        coVerify { cityRepo.saveCity(DefaultCity.TAIPEI) }
        coVerify { prefsRepo.setSelectedCityId(DefaultCity.TAIPEI.id) }
    }

    @Test
    fun `useLocation true but location fails falls through to last-selected`() = runTest {
        coEvery { locationRepo.getCurrentLocationCity() } returns Result.Failure(AppError.LocationTimeout)
        val tokyo = mockCity(id = "tokyo")
        every { prefsRepo.selectedCityId } returns flowOf("tokyo")
        coEvery { cityRepo.getCityById("tokyo") } returns tokyo

        val result = useCase(useLocation = true)

        assertEquals(tokyo, result)
    }
}
```

`DeleteCityUseCaseTest` — selected vs non-selected branches:

```kotlin
@Test
fun `deleting non-selected city does not change selectedCityId`() = runTest {
    every { prefsRepo.selectedCityId } returns flowOf("taipei")
    coEvery { cityRepo.deleteCity("tokyo") } returns Result.Success(Unit)

    useCase("tokyo")

    coVerify(exactly = 0) { cityRepo.saveCity(DefaultCity.TAIPEI) }
    coVerify(exactly = 0) { prefsRepo.setSelectedCityId(any()) }
}

@Test
fun `deleting selected city falls back to DefaultCity TAIPEI`() = runTest {
    every { prefsRepo.selectedCityId } returns flowOf("tokyo")
    coEvery { cityRepo.deleteCity("tokyo") } returns Result.Success(Unit)
    coEvery { cityRepo.saveCity(DefaultCity.TAIPEI) } returns Result.Success(Unit)

    useCase("tokyo")

    coVerify { cityRepo.saveCity(DefaultCity.TAIPEI) }
    coVerify { prefsRepo.setSelectedCityId(DefaultCity.TAIPEI.id) }
}
```

#### ViewModel test pattern

`WeatherViewModelTest` — uiState transitions:

```kotlin
class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()  // helper rule, see below

    @Test
    fun `weather null with no error emits Loading`() = runTest {
        // setup mocks: observeSelectedCityWeather returns flow emitting (city, null)
        //              _lastRefreshError stays null
        // verify uiState.first() == WeatherUiState.Loading
    }

    @Test
    fun `weather null with refresh error emits Error`() = runTest {
        // setup mocks: weather null, refresh fails
        // verify uiState eventually emits WeatherUiState.Error
    }

    @Test
    fun `weather present emits Success with isStale=false when no error`() = runTest {
        // ...
    }

    @Test
    fun `weather present with stale flag when refresh fails after caching`() = runTest {
        // ...
    }
}
```

`MainDispatcherRule` is a small JUnit rule that swaps `Dispatchers.Main` with a `TestDispatcher`. Standard pattern — copy from any reference (e.g., Now in Android `MainDispatcherRule.kt`).

### Run all tests

```bash
./gradlew test
```

Expected: all green. If a test fails, **don't disable it** — figure out whether the test or the implementation is wrong. If implementation, fix it (likely a bug surfaced by testing). If test, fix the test.

## Stage 1 Verification

```bash
./gradlew clean build test
./gradlew :app:kspDebugKotlin  # Hilt graph still wires
```

```bash
# Verify TD-001 actually got refactored (not just renamed)
grep -rn "@Inject internal constructor" core/network/ core/database/ core/datastore/ core/location/
# Should return nothing — all data sources now use interfaces
```

```bash
# Verify no test file is empty placeholder
find core/ feature/ -name "*Test.kt" -size -100c
# Should return nothing
```

## Stage 1 Commits

```
refactor: extract WeatherRemoteDataSource into interface + impl + @Binds
refactor: extract CityRemoteDataSource into interface + impl + @Binds
refactor: extract WeatherLocalDataSource into interface + impl + @Binds
refactor: extract CityLocalDataSource into interface + impl + @Binds
refactor: extract UserPreferencesDataSource into interface + impl + @Binds
refactor: extract LocationDataSource into interface + impl + @Binds
docs: mark TD-001 resolved in TECH_DEBT
test: add WeatherRepositoryImpl tests
test: add CityRepositoryImpl tests
test: add ResolveInitialCityUseCase tests with three-branch fallback coverage
test: add DeleteCityUseCase tests including selected-city fallback
test: add ObserveSelectedCityUseCase tests
test: add WeatherViewModel tests for uiState transitions
test: add CityListViewModel tests for debounce and search states
```

Or condensed if some are tightly related (e.g., all 6 refactors as one commit). Use judgment.

### STOP HERE

Report Stage 1 results, wait for user to say "proceed to Stage 2".

---

# STAGE 2: README.md + AI_USAGE.md

**Scope:** The two most important deliverable docs.

**Verification:** Visual review by user — both docs render correctly on GitHub, content is accurate and complete.

**Checkpoint:** Stop after this stage; user reads both docs.

## A. README.md

### Goal

The reviewer's first contact with the repo. Must answer in under 2 minutes:

1. What is this app?
2. How do I run it?
3. What's the architecture?
4. What's notable about the implementation?

### Structure

Place at repo root (`README.md`). Suggested template:

```markdown
# Weather Forecast

An Android weather app demonstrating modern Android architecture: Kotlin, Coroutines, Jetpack Compose, Clean Architecture, multi-module Gradle setup, and full SSOT data flow.

## Demo

[Insert screenshots here — see "Screenshots" section below]

## Tech Stack

- **Language**: Kotlin (target 21+)
- **UI**: Jetpack Compose, Material 3
- **Architecture**: Clean Architecture (12 modules) + MVVM + UDF
- **DI**: Hilt
- **Async**: Kotlin Coroutines + Flow
- **Network**: Retrofit + kotlinx.serialization
- **Persistence**: Room + DataStore Preferences
- **Location**: Google Play Services Location + Geocoder
- **Build**: Gradle 9 (Kotlin DSL) + AGP 9 + version catalog + convention plugins
- **Testing**: JUnit + MockK + Turbine + kotlinx-coroutines-test

## Quick Start

### Prerequisites

- Android Studio Ladybug or later
- JDK 21
- Android SDK 35

### Build & Run

```bash
git clone <repo-url>
cd weather-forecast
./gradlew :app:installDebug
```

App should launch on a connected device/emulator. First launch shows Taipei weather (network-fetched), with a banner suggesting location permission for auto-detection.

### Run Tests

```bash
./gradlew test
```

## Features

- 🌤 Current day weather + 7-day forecast
- 🌐 Multi-city support (search via Open-Meteo geocoding API)
- 📍 Auto-detect current location (with permission)
- 💾 Offline support — cached weather displays with stale-data banner
- 🌡️ Toggle between °C / °F
- 🔄 Pull-to-refresh
- 🔁 Persistent city selection across app restarts

## Architecture

12 Gradle modules organized by Clean Architecture layers:

```
:app                     ← entry point, NavHost, Hilt setup
:feature:weather         ← weather screen
:feature:citylist        ← city list, search, switching
:core:domain             ← Repository interfaces, UseCases (the architectural seam)
:core:data               ← Repository implementations
:core:network            ← Retrofit + Open-Meteo API
:core:database           ← Room + entities + DAOs
:core:datastore          ← user preferences (selected city, unit)
:core:location           ← FusedLocationProvider + Geocoder
:core:designsystem       ← theme, shared Composables
:core:common             ← Result, AppError, Dispatcher qualifier
:core:model              ← pure Kotlin domain models
```

**Dependency direction**: `feature` → `core:domain` (interfaces only) ← `core:data` (implementations). Feature modules never import data sources directly.

**Single Source of Truth**: Room is the SSOT for all UI-observed data. Network calls update Room; UI observes Room via Flow.

For detail: see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Project Structure

```
weather-forecast/
├── app/                      # Entry point
├── core/                     # Cross-cutting layers
│   ├── common/
│   ├── data/
│   ├── database/
│   ├── datastore/
│   ├── designsystem/
│   ├── domain/
│   ├── location/
│   ├── model/
│   └── network/
├── feature/                  # Feature modules
│   ├── citylist/
│   └── weather/
├── build-logic/              # Gradle convention plugins
├── docs/                     # Project documentation
│   ├── ARCHITECTURE.md
│   ├── CODING_CONVENTIONS.md
│   ├── DEVELOPMENT_PLAN.md
│   ├── ERROR_HANDLING.md
│   ├── MODULE_STRUCTURE.md
│   ├── TECH_DEBT.md
│   ├── TECH_DECISIONS.md
│   └── prs/                  # Per-PR specs and retrospectives
├── gradle/libs.versions.toml # Version catalog
├── AI_USAGE.md               # AI collaboration log
└── README.md                 # You are here
```

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — layering, dependency direction, SSOT
- [`docs/MODULE_STRUCTURE.md`](docs/MODULE_STRUCTURE.md) — per-module package structure
- [`docs/ERROR_HANDLING.md`](docs/ERROR_HANDLING.md) — `Result<T, AppError>` pattern
- [`docs/CODING_CONVENTIONS.md`](docs/CODING_CONVENTIONS.md) — naming, conventions
- [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) — 7-PR delivery plan
- [`AI_USAGE.md`](AI_USAGE.md) — how AI was used in this project

## Screenshots

[Add 3-5 screenshots — current weather, city list, search, offline state, location permission banner]

## API

This app uses [Open-Meteo](https://open-meteo.com/) — free, no API key required.

- Forecast endpoint: `https://api.open-meteo.com/v1/forecast`
- Geocoding endpoint: `https://geocoding-api.open-meteo.com/v1/search`

## License

[your choice — MIT is common for portfolio projects]
```

### Screenshots

Capture 3-5 screenshots from the emulator covering:

1. **Weather screen — online**: Taipei or current location's weather, full forecast visible
2. **City list**: Saved cities with `current_location` icon and selection indicator
3. **Search**: Mid-search with results visible
4. **Offline state**: Stale-data banner visible
5. **Location permission banner** (optional): The "Enable location for auto-detect" UI

Save to `docs/screenshots/`. Reference from README:

```markdown
| Weather | City List | Search |
|---|---|---|
| ![weather](docs/screenshots/01-weather.png) | ![citylist](docs/screenshots/02-citylist.png) | ![search](docs/screenshots/03-search.png) |
```

If you don't have time for clean screenshots, **omit the section** rather than ship blurry ones. README without screenshots is fine; bad screenshots are worse than none.

## B. AI_USAGE.md

This is the **second most important deliverable** (after the working app). It tells the reviewer **how you used AI**, not just **that** you used AI. Be specific, be honest, include both wins and failures.

### Goal

Show that you used AI as a **collaborator with judgment**, not as a code generator with autocomplete. Reviewers reading AI_USAGE.md should come away thinking: "this candidate knows when to trust AI and when to push back."

### Required structure

Place at repo root (`AI_USAGE.md`). The reviewer expects this file by name.

```markdown
# AI Usage in This Project

This document is an honest account of how AI tools were used during the development of this Weather Forecast app. Following the assignment's request for transparency about AI usage.

## Tools Used

| Tool | Purpose | Model |
|---|---|---|
| Claude (claude.ai web chat) | Architecture decisions, PR specs, code reviews | Opus 4.7 |
| Claude Code (CLI) | Per-PR implementation, refactoring, bug investigation | Sonnet 4.6 / Opus 4.6 (varied by PR complexity) |
| Android Studio Code Completion | Boilerplate, imports, simple fills | (built-in, model varied) |

## High-Level Workflow

The project was built across 7 PRs (`PR01` through `PR07`), each with:

1. **Architecture/spec phase** (web chat with Opus): produce a detailed spec document (`docs/prs/PR0N_*.md`) before any code is written
2. **Implementation phase** (Claude Code in terminal): the spec is given to Claude Code, which executes the work in 2–3 stages with checkpoints between stages
3. **Verification phase** (human + emulator): I test the changes manually on the emulator and review code in the GitHub Files-changed view
4. **Retrospective phase** (back to web chat): document what went well, what went wrong, what I'd change

This split deliberately puts **architectural judgment** in the slower / more thorough channel (web chat) and **execution** in the faster channel (CLI).

## How AI Helped

### Architecture & Documentation
Generated the eight architecture documents (`ARCHITECTURE.md`, `MODULE_STRUCTURE.md`, `CODING_CONVENTIONS.md`, etc.) from initial conversations about the assignment requirements. These served as the project's "constitution" — Claude Code reads them at the start of every session.

### Per-PR Specifications
Each PR has a detailed spec in `docs/prs/`. The spec is the most leveraged artifact in the workflow — when the spec is precise, Claude Code's output is precise.

Example: the PR 03 spec was 1285 lines covering 3 stages, with full code skeletons for `WeatherRepositoryImpl`, `WeatherViewModel`, and `WeatherScreen`. Claude Code consumed this and produced the first vertical slice in roughly 3 hours, with one architectural bug found and fixed mid-PR (see "Notable mistakes" below).

### Refactoring
Routine but tedious work — extracting strings to `strings.xml`, refactoring 6 data sources from concrete classes to interfaces (TD-001) — was given to Claude Code with minimal supervision. These tasks are mechanically scoped; AI accelerates them with low risk.

### Bug Investigation
When emulator scenarios failed, Claude Code was asked to investigate before fixing. Several bugs were diagnosed correctly:
- PR 03: `WeatherViewModel` `combine` chain missed the "cache empty + refresh failed" path → Loading forever
- PR 04: `selectedCityId` change triggered re-observe but not refresh → switching cities showed stale data
- PR 05: `LaunchedEffect(permissionState.status)` re-triggered on every recomposition → user's manual city selection was overwritten

### Test Generation
Repository, UseCase, and ViewModel tests in PR 07 were generated from patterns. The first test file was reviewed carefully; subsequent files used the same pattern with minimal modification.

## Notable Mistakes (And What I Did About Them)

I include this section because **AI is not infallible**, and a candid review of where it fell short is more useful than a polished one-sided story.

### Mistake 1: Spec misjudgment in PR 04

**What happened**: The PR 04 spec asserted that "observing `selectedCityId` will self-heal weather data via the existing SSOT chain." This was wrong — `observeWeather` reads from Room; if Room has no row for the new city, no fetch is triggered automatically.

**How it surfaced**: Tested manually on the emulator. Switching cities showed Loading forever for cities without cache.

**How it was resolved**: Claude Code identified the gap, proposed (incorrectly) to inject `WeatherRepository` into `SelectCityUseCase`. I rejected that — it would mix domain boundaries. We discussed and settled on adding `ObserveSelectedCityUseCase` and collecting it in `WeatherViewModel.init` to trigger refresh on every city change. This kept the refresh trigger in the weather domain, not city UseCases.

**Lesson**: AI's first proposal isn't always the best. The spec writer (me, with AI's help) is allowed to be wrong; pushing back to find a cleaner solution saved a future architectural smell.

### Mistake 2: Over-eager dependency injection in PR 03

**What happened**: Claude Code, given the PR 03 spec, decided unilaterally to upgrade Hilt from `2.55` to `2.59.2` because the PR 03 work needed `@HiltAndroidApp` (transform-based annotation, not just KSP).

**How it surfaced**: I noticed it during the Stage 3 commit list review.

**How it was resolved**: It was a defensible choice — Hilt 2.55 wasn't compatible with AGP 9 for transform-based annotations. The version was kept. **But** I added rule #8 to `CLAUDE.md`: "Flag version changes before applying." Subsequent PRs (04, 05, 06) saw Claude Code stop and ask before any version bump.

**Lesson**: Without an explicit rule, AI tends to take initiative on tooling questions. An explicit rule changes its behavior immediately and consistently.

### Mistake 3: Race condition revealed by PR 05

**What happened**: PR 05 added location auto-detect. The mechanism: detect coordinates → reverse-geocode → upsert City row with `id = "current_location"` → `setSelectedCityId("current_location")`. Existing `ObserveSelectedCityUseCase` (added in PR 04) listened to `selectedCityId` and looked up the City via `getCityById` once.

**The bug**: After re-detection in a different location, the row's content changed (lat/lng/name) but `id` didn't. `getCityById` returned the *previous* fetch's City because nothing told it to re-query. Result: weather screen showed Osaka while the device was in Taipei.

**How it surfaced**: User testing on emulator with `Set Location` → Taipei. App showed Osaka weather (left over from a previous Tokyo→Osaka test). Confusion.

**How it was resolved**: Claude Code diagnosed correctly. The fix was to switch from `getCityById(id)` (one-shot lookup) to `combine(selectedCityId, observeSavedCities())` (reactive lookup). Row content changes now propagate.

**This was originally TD-002**, logged in PR 04 with the prediction "if location auto-detect re-emits selectedCityId on every location update, promote to a Flow-based DAO query." The prediction was right; it just landed harder than expected.

**Lesson**: Logged technical debt is a real prediction system. TD-002 went from "Low / re-evaluate" to "user-facing critical bug" within one PR. The TECH_DEBT log helped: when the bug happened, looking back at TD-002 made the diagnosis 5x faster.

## What AI Didn't Do (And Why)

To balance the picture:

- **AI didn't decide the architecture.** I chose Clean Architecture + 12 modules + Open-Meteo before the first AI conversation. AI documented and refined; it didn't generate from blank.
- **AI didn't write the assignment.** All requirements came from the human-authored `docs/ASSIGNMENT.md`.
- **AI didn't pick package names, color choices, or copy text** without my input. UX decisions stayed human-driven.
- **AI didn't manage Git.** Each PR was created, reviewed, and merged manually on GitHub. Branch hygiene was my responsibility.
- **AI didn't run the emulator scenarios.** Every "this PR is done" required me sitting at the emulator, running the listed scenarios. Several PRs failed this step on the first try (see "Notable mistakes").

## Practices That Worked Well

### Living docs that AI reads

`CLAUDE.md` at repo root tells Claude Code:
- The architectural principles (non-negotiable)
- The current PR
- Where to find detailed conventions
- Specific rules learned over the project (e.g., "flag version changes before applying")

This file evolved with the project. When Claude Code's behavior wasn't quite right, the fix was usually adding a rule to `CLAUDE.md`, not arguing with the model.

### TECH_DEBT.md as a prediction log

When AI proposed a workaround that wasn't ideal, I logged it as a TD entry instead of letting it disappear. This created a documented prediction ("this will become a problem in PR 05/06") that made later diagnosis fast.

3 entries logged → 3 entries resolved. 100% close rate.

### Per-stage checkpoints

Each PR had 2–3 stages. Claude Code stopped between stages and waited for approval. This gave me natural review points without getting buried in a single huge diff.

### Retrospectives for AI_USAGE material

Each PR spec ends with a "Post-PR Retrospective" section. Filling it after each merge created a free first draft of this very document.

## Honest Self-Assessment

**Estimated time saved by AI**: ~50–60% on implementation, much higher on documentation. Without AI, this would not have been a 7-PR project — it would have been a 3-PR project with worse docs.

**Estimated quality improvement from AI**: Mixed. Architecture patterns and code style are more consistent than I'd produce alone. But three of the project's bugs (mistakes 1–3 above) came from AI proposing a flawed design that I trusted at first; without my review, they would have shipped.

**The takeaway**: AI made the **floor** of code quality higher (no broken loops, consistent style) and the **ceiling** roughly the same (still bounded by my judgment). The biggest leverage was in the *workflow* (per-PR specs, checkpoints, TECH_DEBT) — practices I would carry to a non-AI project too.
```

### Tone notes for AI_USAGE.md

- **Specific examples beat abstract claims.** "AI helped me write code" is forgettable; "Claude Code's first proposal for the city selection bug was to inject WeatherRepository into SelectCityUseCase, which I rejected because it mixed domain boundaries" is memorable.
- **Honest about failures.** Reviewers respect candor more than polish. Mistakes 1–3 above are real; document yours similarly.
- **Don't oversell.** "AI made me 10x productive" is a red flag. "AI saved 50% time on implementation, but introduced 3 bugs I caught manually" is credible.
- **Voice is yours, not AI's.** This document should sound like you wrote it (with AI help), not like AI wrote it. Edit accordingly.

## Stage 2 Verification

Manual review by user. Checklist:

- [ ] README.md renders cleanly on GitHub
- [ ] All links in README work (relative paths to docs/, AI_USAGE.md, etc.)
- [ ] AI_USAGE.md has all required sections (Tools Used, How AI Helped, Notable Mistakes, etc.)
- [ ] Mistakes section has at least 2 specific, named examples
- [ ] No placeholder `[insert ...]` text left in either file
- [ ] Screenshots, if included, are current and high-quality (or section is omitted)

## Stage 2 Commits

```
docs: write README.md
docs: write AI_USAGE.md
docs: add screenshots to README  (optional)
```

### STOP HERE

Report Stage 2 results, wait for user to say "proceed to Stage 3".

---

# STAGE 3: Final Regression + Delivery Cleanup

**Scope:** Last lap before delivery. No new features.

## A. Final regression test pass

Run **every emulator scenario** from PR 03–06 once more. This is the last time you'll do this; make it count.

### Combined scenario list

**From PR 03 — Weather basics:**
1. Online: Taipei weather displays correctly (current + 7-day)
2. Offline with cache: stale banner + cached data
3. Cold-start offline: Error screen + Retry button works

**From PR 04 — City list:**
4. Search "Tokyo" with debounce → tap → weather updates
5. Saved cities persist across cold starts
6. Switch between saved cities
7. Delete non-selected city
8. Delete currently-selected city → fallback Taipei
9. Empty search results show "No matches"
10. Clear search field returns to saved list

**From PR 05 — Location:**
11. Fresh install no permission → Taipei + banner
12. Grant permission → current city loads
13. Permission persists across launches
14. Deny permission → banner stays, app works
15. Location services off → distinct banner
16. Re-detection: change emulator location → relaunch → only ONE current_location row

**From PR 06 — Polish:**
17. Pull-to-refresh: works, no race
18. °C / °F toggle: persists, triggers refetch
19. Search inline error banner: saved cities still tappable
20. CityList: current_location row has location icon
21. UNKNOWN weather condition: AutoMirrored HelpOutline renders

**21 scenarios.** Allow ~30–45 minutes for full pass.

If any scenario regresses, **stop and investigate**. Do not ship a regression.

## B. Final clean build

```bash
./gradlew clean build test
./gradlew :app:installDebug
./gradlew :app:assembleRelease
```

The `assembleRelease` step is new — release variant exposes minification issues, missing rules, etc. that debug variant masks. If this fails, fix before delivery.

## C. Documentation cross-check

```bash
# Make sure no doc references a non-existent path
grep -rn "docs/prs" docs/ CLAUDE.md README.md
# All references should match files that actually exist
```

```bash
# Confirm CLAUDE.md "Current PR" reflects PR07 = current
grep -A 5 "Current PR" CLAUDE.md
```

```bash
# All PRs marked done in DEVELOPMENT_PLAN.md
grep "✅\|🚧\|⏳" docs/DEVELOPMENT_PLAN.md
```

After PR 07 merges, you'll do one final commit on a small branch (or directly on main if you allow it) to mark PR 07 as ✅ in `CLAUDE.md` and `DEVELOPMENT_PLAN.md`. Don't do it inside PR 07 itself — that's a chicken-and-egg loop.

## D. Optional: Release tag

After PR 07 merges, on `main`:

```bash
git checkout main
git pull
git tag -a v1.0.0 -m "Initial deliverable"
git push origin v1.0.0
```

GitHub will show "v1.0.0" in the releases section. Polished touch for delivery.

## E. Reviewer-ready checklist

Run through this list before declaring delivery:

- [ ] `./gradlew clean build test` passes
- [ ] `./gradlew :app:installDebug` works on a clean emulator
- [ ] All 21 emulator scenarios pass
- [ ] README.md is the first thing visible at repo root
- [ ] AI_USAGE.md is at repo root
- [ ] No `TODO` / `FIXME` comments in production code (test code is OK)
  - `grep -rn "TODO\|FIXME" --include="*.kt" core/ feature/ app/ | grep -v "Test.kt"`
- [ ] All TECH_DEBT items either resolved or have a clear rationale for why they remain
- [ ] No commented-out code blocks left in
- [ ] `docs/ASSIGNMENT.md` requirements are all addressed (cross-check item by item)
- [ ] PR 07 merged to main
- [ ] Optional: v1.0.0 tag pushed

## Stage 3 Commits

Likely just 1 final commit:

```
docs: final regression notes in PR07 retrospective
```

Or no commits at all if everything passes silently.

---

## Final PR Verification

```bash
./gradlew clean build test
./gradlew :app:installDebug
```

```bash
# Boundary final sweep
grep -rn "import com.example.weatherforecast.core.network\|import com.example.weatherforecast.core.database\|import com.example.weatherforecast.core.datastore\|import com.example.weatherforecast.core.location\|import com.example.weatherforecast.core.data" feature/
# Should return nothing
```

```bash
# Check test count
./gradlew test 2>&1 | grep -E "tests? completed"
# Expect 30+ tests, 0 failures
```

## Expected total commits

15–25 across the 3 stages.

## Post-PR Retrospective (fill after merge)

- Total time taken:
- Stage 1 — TD-001 refactor: any cascade beyond the 6 data sources?
- Stage 1 — Tests: which test was hardest to write? (Often reveals a design smell.)
- Stage 2 — README/AI_USAGE: time spent? Honest section worked or felt awkward?
- Stage 3 — Regression: any scenario fail? If so, was it a regression or pre-existing?
- Anything you wish you'd done in PR 01–06 that this PR exposed?

---

## Claude CLI: How to Work on This PR

1. **Read this entire spec + the 6 reference docs before writing any code.** Especially `docs/ASSIGNMENT.md` — README must address every requirement.

2. **Execute in 3 stages with hard checkpoints.**

3. **Within a stage, autonomous execution is fine.**

4. **Commit frequently but sensibly.** 5–10 commits per stage is normal here (tests + refactors are naturally split).

5. **If a build or test failure blocks progress for more than 2 attempts:** stop and describe.

6. **Do not:**
   - Add features not in this spec
   - Refactor architecture (only TD-001 cleanup is in scope)
   - Skip emulator scenarios in Stage 3 because tests pass — emulator is the truth
   - Write tests for the sake of coverage — only critical paths

7. **Version changes:** Per CLAUDE.md rule 8, flag any `libs.versions.toml` upgrade. Adding test deps (turbine, mockk, kotlinx-coroutines-test) is fine without flagging — those are additions, not upgrades.

8. **For tests specifically:**
   - Use `runTest` from `kotlinx-coroutines-test`
   - Use Turbine for `Flow` assertions
   - Use MockK (not Mockito) — already standard in the catalog if present, add if not
   - Test ONE behavior per test case
   - Test names: backtick form, descriptive: `` `weather null with refresh error emits Error` ``
   - Don't test trivial pass-through methods

9. **For README.md and AI_USAGE.md:**
   - Voice is the user's, not the AI's. After draft, the user will revise heavily — that's expected
   - Do NOT add fake "how AI helped" stories — only document what genuinely happened, sourced from the per-PR retrospectives in `docs/prs/`
   - Mistakes section is required and must include real examples from PRs 03/04/05 retrospectives

10. **When something seems out-of-scope:** flag it. PR 07 is the catch-all for finishing, not for new ideas.

## Post-PR Retrospective

**Status**: Done — 2026-04-XX

### Stage 1 — TD-001 refactor + tests

TD-001 refactor extended cleanly to 6 data sources (the original 5 plus PR05's `LocationDataSource`). No cascading effects beyond the affected modules. The Hilt graph stayed intact through the refactor — `:app:kspDebugKotlin` passed on first try after each module's binding update.

7 test files, 36 tests total. Three test-side issues encountered and resolved in-test (no production code changed):
- `emptyList()` type inference required explicit type parameter
- StateFlow with `WhileSubscribed` upstream needed `.test {}` subscription to start emitting
- Conditional branches in some tests handle non-deterministic emit ordering between StateFlow combine and debounce — works, but ideally would use `StandardTestDispatcher` + explicit `runCurrent()` for fully deterministic flow assertions. Future improvement.

### Stage 2 — README + AI_USAGE

README — straightforward; no surprises.

AI_USAGE.md — first draft had AI tone in roughly 4 places (modal verbs, "real leverage" rhetoric, oppositional sentence structures). Rewrote those to match my actual writing voice. The Mistakes section initially included a mis-described mistake (Hilt silent upgrade) that on review wasn't really silent — it was a flagged-and-investigated decision. Replaced with the actual PR03 mistake (missing Error path in `WeatherViewModel`).

### Stage 3 — Final regression

Automated checks: all green (clean build, release build, 36 tests, boundary, no TODO).

Manual emulator regression: 3 bugs surfaced that were not caught by previous PRs' scenario coverage:

1. **Default city ID collision** — `DefaultCity.TAIPEI.id = "default_taipei"` vs geocoding API's Taipei id `"<numeric>"`. Searching Taipei creates a duplicate row alongside the default-installed one. The "default" row cannot be deleted because `DeleteCityUseCase` re-inserts it as fallback. Visible to user as "two Taipeis, one undeletable."

2. **Location permission re-resolve trigger** — when permission is granted and the emulator's location changes between launches, the app shows the previously-cached city, not the new location. The `wasUsable → nowUsable` transition guard in `WeatherViewModel.onLocationPermissionChanged` doesn't fire when permission state itself didn't change.

3. **Permission banner ineffective after deny** — Android's `launchPermissionRequest()` becomes a silent no-op after the user denies twice. The banner's onTap doesn't fall through to opening system Settings, so user perceives the banner as broken.

These will be addressed in a separate hotfix PR (PR 08), keeping PR 07 focused on its stated scope (TD-001 + tests + delivery docs). All three bugs were introduced in earlier PRs (03/04/05) — the final regression's value was exactly catching them before delivery.
