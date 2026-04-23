# Development Plan

The roadmap from empty repo to delivered app.
Audience: you (the developer) and Claude CLI, during every PR.

## How to Use This Document

- Before starting a new PR, read its section end-to-end.
- During the PR, the "Definition of Done" checklist is the acceptance gate.
- If scope creeps, check "Out of Scope" — if the new work is there, defer it.
- If an unexpected need arises, update this document *first*, then implement.

## PR Strategy Summary (X')

Seven sequential PRs. Each one compiles and runs. PR3 is the first visible change.

```
PR01 ─► PR02 ─► PR03 ─► PR04 ─► PR05 ─► PR06 ─► PR07
 │       │       │       │       │       │       │
 │       │       │       │       │       │       └─► Tests + docs (delivery)
 │       │       │       │       │       └────────► Polish (pull-to-refresh, units, error UX)
 │       │       │       │       └────────────────► Location integration
 │       │       │       └────────────────────────► City list + search + switch
 │       │       └────────────────────────────────► First weather screen (Taipei hardcoded)
 │       └────────────────────────────────────────► Core foundations (all core:* except domain/data)
 └────────────────────────────────────────────────► Scaffolding (build-logic, catalog, module skeleton)
```

Key properties:
- **PR01–PR02** establish the foundation; UI stays as the AS default.
- **PR03** is the inflection point — real weather appears.
- **PR04–PR06** add features and polish.
- **PR07** finalizes testing and documentation for delivery.

---

## PR 01 — Scaffolding

**Branch:** `feat/01-scaffolding`

**Goal:** Establish the build infrastructure so future PRs can focus on code, not Gradle.

### Scope

- Create `gradle/libs.versions.toml` with all anticipated dependencies (versions, libraries, plugins).
- Create `build-logic/` module containing convention plugins:
  - `AndroidApplicationConventionPlugin`
  - `AndroidLibraryConventionPlugin`
  - `AndroidFeatureConventionPlugin`
  - `AndroidHiltConventionPlugin`
  - `JvmLibraryConventionPlugin`
- Enable type-safe project accessors in `settings.gradle.kts`.
- Create **empty** module directories with skeleton `build.gradle.kts` for all 12 modules.
- `settings.gradle.kts` includes all modules.
- Update `:app/build.gradle.kts` to use the new convention plugin.
- `AndroidManifest.xml` remains the AS default (no Hilt app, no custom activity yet).

### Out of Scope

- No Hilt `@HiltAndroidApp` yet.
- No actual code inside core / feature modules (just empty `build.gradle.kts` + placeholder `.gitkeep` if needed).
- No navigation setup.
- No theme customization.

### Definition of Done

- [ ] `./gradlew build` completes successfully.
- [ ] `./gradlew :app:assembleDebug` produces a runnable APK.
- [ ] App launches on emulator and shows the AS default "Hello Compose" screen.
- [ ] All 12 modules are listed in `settings.gradle.kts` and visible in Android Studio's project view.
- [ ] Version Catalog used everywhere — no hardcoded versions in any `build.gradle.kts`.
- [ ] Convention plugins applied correctly (feature modules use `weatherapp.android.feature`, etc.).

### Emulator result

AS default Hello Compose screen, unchanged.

### Claude CLI notes

- Reference [Now in Android](https://github.com/android/nowinandroid) convention plugins as the structural template.
- Prefer minimal plugin content — just enough to stop duplication. We'll add more as needs arise.
- Module `build.gradle.kts` files at this stage may be only 5–10 lines each.

---

## PR 02 — Core Foundations

**Branch:** `feat/02-core-foundations`

**Goal:** Fill in the non-feature core modules so PR03 can integrate them into a working screen.

**Depends on:** PR01.

### Scope

This PR implements six core modules. Size-wise it is larger than others, but the modules are independent enough that Claude CLI can tackle them one at a time.

- `:core:model` — all domain data classes (`City`, `Weather`, `DailyForecast`, `HourlyForecast`, `TemperatureUnit`, `WeatherCondition`).
- `:core:common` — `Result<T, E>`, `AppError` sealed hierarchy (all 13 subtypes), `Dispatcher` qualifier + `DispatcherModule`, `DefaultCity.TAIPEI`.
- `:core:network`
  - Retrofit + OkHttp + kotlinx-serialization setup
  - `OpenMeteoForecastApi`, `OpenMeteoGeocodingApi`
  - All DTOs (`ForecastResponseDto`, `CurrentWeatherDto`, `DailyForecastDto`, `HourlyForecastDto`, `GeocodingResponseDto`)
  - Mappers (`WeatherDtoMapper`, `CityDtoMapper`, `WeatherCodeMapper`)
  - `apiCall { }` helper
  - `WeatherRemoteDataSource`, `CityRemoteDataSource`
  - `NetworkModule`
- `:core:database`
  - `WeatherDatabase`, all entities, all DAOs
  - `Converters` for Instant / WeatherCondition
  - `CityLocalDataSource`, `WeatherLocalDataSource`
  - Entity ↔ domain mappers
  - `DatabaseModule`
- `:core:datastore`
  - `UserPreferencesDataSource`
  - `PreferencesKeys`
  - `DataStoreModule`
- `:core:designsystem`
  - `WeatherAppTheme`, Color, Type, Shape
  - Shared components (`WeatherIcon`, `LoadingIndicator`, `ErrorState`, `EmptyState`, `TemperatureText`)
  - Icon resources for 7 `WeatherCondition` values

### Out of Scope

- `:core:domain` — deferred to PR03 (tightly coupled with repository impl).
- `:core:data` — deferred to PR03.
- `:core:location` — deferred to PR05.
- No feature modules.
- No `MainActivity` changes or NavHost yet.
- `HourlyForecastEntity` schema is included, but no DAO methods for hourly (reserved for future).

### Definition of Done

- [ ] `./gradlew build` succeeds.
- [ ] `./gradlew :core:network:assembleDebug :core:database:assembleDebug :core:datastore:assembleDebug` all succeed independently.
- [ ] Compile-time verification: no feature module depends on these yet (can be verified in PR03 — here just ensure the modules build alone).
- [ ] Unit tests for `apiCall { }` helper (verifies exception-to-`AppError` mapping) pass.
- [ ] Room schema exported to `schemas/` directory (add `room.schemaLocation` arg to `DatabaseModule`'s KSP config).
- [ ] App still launches and shows Hello Compose (no UI change expected).
- [ ] No `kotlin.Result` usage anywhere (grep check).
- [ ] No hardcoded `Dispatchers.IO` anywhere (grep check).

### Emulator result

AS default Hello Compose screen, unchanged. (Verification is mostly via `./gradlew build` and inspection, not runtime behavior.)

### Claude CLI notes

- This PR is a natural place to generate a lot of boilerplate. Review each file — don't merge blindly.
- Recommend splitting into sub-tasks: one session per `:core:*` module, in order: `model → common → network → database → datastore → designsystem`.
- The Open-Meteo API returns WMO weather codes (0–99). `WeatherCodeMapper` should group them into the 7 `WeatherCondition` enum values. Full WMO reference: https://open-meteo.com/en/docs (scroll to "Weather variable documentation").
- Favor simple icon assets (Material Symbols from `androidx.compose.material.icons.extended`) over custom artwork for this PR.

---

## PR 03 — First Vertical: Weather Screen

**Branch:** `feat/03-weather-vertical`

**Goal:** Make real weather appear on screen for Taipei. First end-to-end integration of all layers.

**Depends on:** PR02.

### Scope

- `:core:domain`
  - `WeatherRepository` interface
  - `CityRepository` interface
  - `UserPreferencesRepository` interface
  - `ResolveInitialCityUseCase` (simplified — only the "last selected → DefaultCity" fallback; location comes in PR05)
  - `ObserveSelectedCityWeatherUseCase`
  - `RefreshWeatherUseCase`
- `:core:data`
  - `WeatherRepositoryImpl`, `CityRepositoryImpl`, `UserPreferencesRepositoryImpl`
  - `RepositoryModule` with `@Binds`
- `:feature:weather`
  - `WeatherRoute`, `weatherScreen(onNavigateToCityList)` (the callback is a no-op or TODO for now)
  - `WeatherViewModel`
  - `WeatherUiState` sealed interface
  - `WeatherScreen`, `WeatherContent` (stateless)
  - Components: `CurrentWeatherHeader`, `WeatherDetailsRow`, `DailyForecastList`, `StaleDataBanner`
  - `PreviewData` with sample Weather/City
- `:app`
  - `WeatherApp` (`@HiltAndroidApp`)
  - `MainActivity` (`@AndroidEntryPoint`)
  - `WeatherAppNavHost` — single destination: `WeatherRoute`
  - Wire up Hilt + Theme

### Out of Scope

- No city list screen. Tap on city name / menu is a no-op.
- No location features — Taipei is the starting city no matter what.
- No unit toggle in UI — temperature shows in Celsius always.
- No pull-to-refresh — refresh happens once on screen entry.
- No `toUserMessage()` for all errors yet — minimal set is fine (NoNetwork, Unexpected cover most).

### Definition of Done

- [ ] Fresh install → app opens → sees Taipei current weather + 7-day forecast.
- [ ] Airplane mode → app opens → sees Taipei from cached Room data (after a prior successful fetch) + stale banner.
- [ ] Airplane mode + fresh install → app opens → shows `WeatherUiState.Error`.
- [ ] No crash in any of the above states.
- [ ] ViewModel tests for happy path + no-network path.
- [ ] Feature module's `build.gradle.kts` does **not** declare dependency on `:core:network`, `:core:database`, `:core:datastore`, or `:core:location`.

### Emulator result

Real Taipei weather displayed. Temperature, condition, weekly forecast all visible.

### Claude CLI notes

- `ResolveInitialCityUseCase` is deliberately simplified in this PR. Full fallback chain (with location) arrives in PR05 — don't preemptively add location-related branches.
- `CurrentLocationCity` upsert logic is not needed here (no location source yet).
- The navigation callback `onNavigateToCityList` is defined but unwired; `:app` can pass an empty lambda or a `Log.d` placeholder (remove before commit).

---

## PR 04 — City List and Search

**Branch:** `feat/04-citylist`

**Goal:** Enable users to search and switch between cities.

**Depends on:** PR03.

### Scope

- `:feature:citylist`
  - `CityListRoute`, `cityListScreen(onNavigateBack)`
  - `CityListViewModel`, `CityListUiState`
  - `CityListScreen`, components (`CitySearchBar`, `SavedCityItem`, `SearchResultItem`)
  - Search flow: `onSearchQueryChanged` → debounce(300ms) → `searchCities`
  - Saved list flow: `observeSavedCities()`
  - Tapping a saved city: `userPreferencesRepository.setSelectedCityId(cityId)` → `onNavigateBack()`
  - Tapping a search result: save city → set selected → `onNavigateBack()`
  - Swipe-to-dismiss or delete button on saved cities (simple implementation).
- `:app` — wire up navigation:
  - `WeatherScreen`'s `onNavigateToCityList` → `navController.navigate(CityListRoute)`
  - `CityListScreen`'s `onNavigateBack` → `navController.popBackStack()`
- `:feature:weather`
  - `WeatherScreen` top bar: tappable city name → triggers `onNavigateToCityList`.

### Out of Scope

- No current-location detection (still PR05).
- No drag-to-reorder saved cities.
- No grouping / sectioning of city list.
- No favorites or pinning beyond "selected city".

### Definition of Done

- [ ] Tap city name on weather screen → city list opens.
- [ ] Type in search → candidate cities appear after ~300ms debounce.
- [ ] Tap search result → city is saved → return to weather screen → weather reflects new city.
- [ ] Saved cities persist across app restarts (via Room).
- [ ] Selected city persists across app restarts (via DataStore).
- [ ] Delete a saved city → disappears from list.
- [ ] Deleting the currently selected city resets selection to `DefaultCity.TAIPEI`.
- [ ] ViewModel tests for search + select + delete paths.

### Emulator result

Two-screen app: weather main + city list. Full city switching works.

### Claude CLI notes

- Geocoding API search is rate-limited per Open-Meteo's fair-use policy. Debounce is not optional.
- Search results include latitude/longitude — store those in the `City` object so weather queries can proceed without re-geocoding.
- The edge case "user deletes currently selected city" needs explicit handling — don't leave `selectedCityId` pointing to a non-existent row.

---

## PR 05 — Location Integration

**Branch:** `feat/05-location`

**Goal:** Auto-detect the user's current city when location permission is granted.

**Depends on:** PR04.

### Scope

- `:core:location`
  - `LocationProvider` (wraps `FusedLocationProviderClient`, 5-second timeout)
  - `GeocoderWrapper` (handles API 33+ async and legacy sync)
  - `LocationDataSource` (combines the two → returns `City` with `isCurrentLocation = true`)
  - `LocationModule`
- `:core:domain`
  - `LocationRepository` interface
- `:core:data`
  - `LocationRepositoryImpl`
- `:core:domain/usecase`
  - Expand `ResolveInitialCityUseCase` to include location branch:
    1. If permission + GPS → try location → on success, upsert as `CurrentLocationCity` and return
    2. Otherwise fallback to previously selected city
    3. Otherwise `DefaultCity.TAIPEI`
- `:feature:weather`
  - Add a subtle top-bar affordance / banner: "Enable location for auto-detect" when permission is not granted. Tapping prompts the system permission.
  - Use `accompanist-permissions` or the Compose-native permission API (whichever is cleaner with current Android APIs).
- `:app`
  - Declare `ACCESS_COARSE_LOCATION` in `AndroidManifest.xml` (COARSE is sufficient — we don't need fine precision for a weather app).

### Out of Scope

- Background location — never requested.
- Precise (fine) location permission.
- Location updates while app is open (single fetch per session is enough).
- Settings screen to toggle location use (PR06 may revisit).

### Definition of Done

- [ ] Fresh install with no location permission → shows Taipei (or last selected), top banner prompts for permission.
- [ ] Granting permission → next app open → shows current-location city.
- [ ] Denying permission → banner persists but app works normally with Taipei / last selected.
- [ ] Location turned off at system level → shows `LocationDisabled` banner (with "Open Settings" action optional).
- [ ] Location city is stored in Room with `isCurrentLocation = true` flag, reused (not duplicated) on subsequent auto-detections in the same physical area.
- [ ] UseCase tests for all fallback branches.

### Emulator result

Auto-detects location (use emulator's "Set Location" option to test). Banner flow works.

### Claude CLI notes

- `FusedLocationProviderClient.lastLocation` may return null on first app install (no cached location). If null after 5s, return `AppError.LocationTimeout`.
- `Geocoder.getFromLocation` is deprecated on API 33+. Use the new async listener form for 33+, fall back to the sync form wrapped in `withContext(ioDispatcher)` for older APIs.
- The permission UX was decided to be **"in-context"** — the app does not prompt on first launch. User taps the banner to trigger the system dialog.

---

## PR 06 — Polish: Refresh, Units, Error UX

**Branch:** `feat/06-polish`

**Goal:** Bring UX quality up to production standard.

**Depends on:** PR05.

### Scope

- **Pull-to-refresh** on weather screen (Material 3 `PullToRefreshBox`).
- **Temperature unit toggle**:
  - Menu or simple toggle on weather screen.
  - Setting stored in DataStore (`TemperatureUnit`).
  - Open-Meteo API called with corresponding `temperature_unit` parameter.
  - Saved as user preference — persists across sessions.
- **Error UX refinement**:
  - Full `toUserMessage()` mapping for every `AppError` subtype.
  - Snackbar for transient errors (e.g., refresh failed but cache shown).
  - Full-screen error state with retry button for cold-start failures.
  - Stale banner wording improved ("Last updated 5m ago, offline").
- **String resource extraction**: move all user-facing strings to `res/values/strings.xml`.

### Out of Scope

- Multi-language (no `values-zh/` etc. in this PR — PR07 may discuss).
- Widget, complications, or other surfaces.
- Notifications.
- Custom animations beyond what Material components provide.

### Definition of Done

- [ ] Pull down on weather screen → spinner → refresh completes.
- [ ] Refresh while offline → snackbar shows "Offline — displaying cached data".
- [ ] Toggle °C / °F → temperature updates across all displayed values (current, daily min/max).
- [ ] Every `AppError` subtype has a defined user-facing message (grep `toUserMessage`).
- [ ] All hardcoded UI strings are now in `strings.xml`.
- [ ] Full-screen error state is visually polished, not just raw text.

### Emulator result

UX-complete app. Pull-to-refresh works, unit toggle works, errors look intentional.

### Claude CLI notes

- `PullToRefreshBox` is in Material 3 (requires `material3` 1.3.0+). Verify `libs.versions.toml` reflects this.
- For unit conversion, don't do math — pass `temperature_unit=fahrenheit` to the API and store the resulting values directly. This avoids precision drift and matches what Open-Meteo returns.

---

## PR 07 — Tests and Documentation

**Branch:** `feat/07-tests-and-docs`

**Goal:** Final polish for delivery. Comprehensive test coverage and reviewer-ready documentation.

**Depends on:** PR06.

### Scope

- **Tests**:
  - Repository tests (WeatherRepositoryImpl, CityRepositoryImpl) — SSOT behavior.
  - UseCase tests (especially `ResolveInitialCityUseCase` full fallback chain).
  - ViewModel tests for both features — UiState transitions, error handling.
  - Fill in any test gaps left in prior PRs.
  - Fake implementations collected in `src/test/kotlin/.../fake/` per module.
- **README.md**:
  - Project overview with screenshots (capture from emulator after PR06).
  - Architecture summary (link to `docs/ARCHITECTURE.md` for details).
  - Module overview diagram.
  - How to run (should be: clone, open in AS, run — no API key needed).
  - Testing guide.
  - Technology stack list.
- **AI_USAGE.md**:
  - Which AI tools were used for what.
  - The prompting strategy (design discussion → document generation → PR-by-PR implementation).
  - What AI-generated content was kept vs rewritten.
  - Honest reflection on AI's contribution.
- **Screenshots** in `docs/screenshots/`:
  - Weather screen (day + weekly forecast)
  - City list + search
  - Error state
  - Offline / stale state

### Out of Scope

- No new features.
- No code refactoring beyond what's needed for testability.
- No CI setup (decided earlier — out of scope).

### Definition of Done

- [ ] `./gradlew test` passes (all unit tests green).
- [ ] Test coverage includes: every Repository, every UseCase, both ViewModels.
- [ ] README.md opens and renders correctly on GitHub with screenshots.
- [ ] AI_USAGE.md is honest and informative.
- [ ] Repo cloned fresh → `./gradlew build` → app runs — verified on a clean checkout.
- [ ] All PRs merged to `main`.

### Emulator result

Same as PR06 — no behavior change. Delivery artifacts are the focus.

### Claude CLI notes

- Tests written here should not be "100% coverage" theater. Cover the important paths: happy, error, edge cases discussed in each module's design.
- For README screenshots, the `screenshots/` folder belongs at the repo root or in `docs/`, not inside `:app/src/main/res/`.

---

## Cross-PR Conventions

### Starting a new PR

1. Ensure `main` is up to date: `git checkout main && git pull`.
2. Create the branch: `git checkout -b feat/NN-name`.
3. Open `CLAUDE.md` and update the "Current PR" section.
4. Open this document to re-read the PR's section.
5. (For PR 03 onward, if a detailed spec exists at `docs/prs/PRxx_*.md`, read that too.)

### Finishing a PR

1. All DoD checkboxes met.
2. Commits follow Conventional Commits (no scope, per project convention).
3. Push: `git push -u origin feat/NN-name`.
4. Open PR on GitHub. PR description template:
   ```
   ## What
   <one paragraph>

   ## Why
   <link back to this plan's PR section; highlight any deviations>

   ## Verification
   <what you did to verify DoD>

   ## Notes
   <anything future PRs should know>
   ```
5. Self-review, merge to main.
6. Update `CLAUDE.md` — clear the "Current PR" section or point to the next one.

### Handling Unexpected Scope

If during a PR you discover work that wasn't planned:

- **If trivial (< 30 min) and directly required** → do it inline, note it in the PR description.
- **If larger** → stop, evaluate: can it wait? If yes, create a TODO comment and defer to the PR it belongs to.
- **If essential and can't wait** → update this document *first* (move the scope into the current PR's section), then implement.

Never silently expand scope. The document is the source of truth — update it before the code.

### When Priorities Change

If external constraints shift (running out of time, new requirement surfaces):

- Trim scope from later PRs first. Earlier PRs form the foundation; don't weaken them.
- Preserve priority order: Core architecture > Weather display > City list > Search > Location > Unit toggle > Error UX > Tests.
- If cutting a PR entirely: merge as much as is complete, skip or defer the rest, document clearly in README what's incomplete and why.

---

## Risk Log

Known risks and mitigations, updated as we learn:

| Risk | Mitigation |
|------|------------|
| PR02 is large and could balloon | Keep modules minimal; defer anything not strictly needed to PR03+. |
| Convention plugin setup in PR01 hits Gradle oddities | Budget extra time; reference Now in Android exactly when stuck. |
| Geocoder API behavior differs between API levels | `GeocoderWrapper` isolates the difference in one place. Test on both emulators if possible. |
| Open-Meteo schema changes | DTOs are `internal`; a schema change is bounded to `:core:network`. |
| Location permission UX feels clunky | Decided to use in-context prompt (banner, not full dialog on launch). Revisit in PR06 if feedback is poor. |

---

## Next Step After This Document

`docs/prs/PR01_SCAFFOLDING.md` — the detailed spec for the first PR.

That document turns this plan's PR01 summary into a file-by-file recipe Claude CLI can execute on.
