# Technical Debt Log

Known improvements deferred for focus or time constraints.
This is a **living document** — add entries as decisions are made to defer work,
close them when resolved.

## How to Use This Document

- **When you notice an issue that shouldn't block the current PR**: add an entry here instead of leaving a `TODO` comment in code.
- **When starting a new PR**: scan this document for items whose "target resolution" matches.
- **When closing an item**: change status to `Resolved`, add the PR number and date.

## Entry Format

Each entry has a stable ID (`TD-NNN`), never renumbered even when resolved.

| Field | Description |
|-------|-------------|
| What | Concrete description of the issue |
| Why deferred | Rationale for not fixing immediately |
| Affected | Files / modules / areas touched |
| Severity | Low / Medium / High |
| Target resolution | Which PR or window to address this |
| Status | Open / Resolved |

---

## Open Items

### TD-004: `LocationProvider.lastLocation` has no staleness check

**Introduced**: PR 08
**Status**: Open
**Severity**: Low

**What**

`LocationProvider.getCurrentLocation()` falls back to `fusedClient.lastLocation` when the fresh-fix `getCurrentLocation()` call times out. `lastLocation` has no built-in age limit — if the user opened Maps days ago and hasn't moved, the result could be a multi-day-old position.

**Why deferred**

For city-level weather, a multi-day-old location is still more useful than showing a stale city row from the previous launch. The trade-off is acceptable for the current scope. The fallback exists specifically to recover the case where FLP has no active fix (common on emulators, possible on real devices) — without it, Bug B's ViewModel-level fix silently fell back to the stale DataStore city.

**Affected**

- `:core:location/provider/LocationProvider.kt`

**Target resolution**

If users report "weather shows old city after travel" or other staleness symptoms, add a timestamp check rejecting `lastLocation` results older than a threshold (e.g., 24 hours). Discovered while verifying PR 08's Bug B fix on the emulator.

---

## Resolved Items

### TD-001: Data source classes should be interfaces

**Introduced**: PR 02
**Status**: Resolved — PR 07
**Severity**: Low

**What**

Data source classes (`WeatherRemoteDataSource`, `CityRemoteDataSource`, `WeatherLocalDataSource`, `CityLocalDataSource`, `UserPreferencesDataSource`, plus PR 05's `LocationDataSource`) were initially declared as concrete classes with `@Inject internal constructor`. This worked for DI but coupled testing fakes to concrete implementations.

**Affected**

- `:core:network/datasource/`
- `:core:database/datasource/`
- `:core:datastore/datasource/`
- `:core:location/datasource/`

**Resolution (PR 07)**

All 6 data sources refactored to `interface + internal class XxxImpl + @Binds` pattern. Each module gained a small `internal abstract class DataSourceBindModule` to bind interfaces to implementations. Test fakes now implement the interface directly, cleaner than mocking concrete classes. Module boundaries also strengthened — external consumers see only the interface, not the impl.

---

### TD-002: `getCityById` suspend overhead inside observe chain

**Introduced**: PR 04 (Stage 3 fix)
**Status**: Resolved — PR 05
**Severity**: Low

**What**

`ObserveSelectedCityUseCase` called `cityRepository.getCityById(id)` inside `Flow.map { ... }` every time `selectedCityId` emitted. This was a one-shot suspend query — it didn't re-run when the selected city's row was updated in-place (same id, updated name/coords), causing a stale City object to remain in the observe chain.

**Affected**

- `:core:domain/usecase/ObserveSelectedCityUseCase.kt`
- `:core:domain/usecase/ObserveSelectedCityWeatherUseCase.kt`

**Resolution (PR 05)**

Both use cases now use `combine(selectedCityId, cityRepository.observeSavedCities())` and resolve the city by filtering the live list: `cities.firstOrNull { it.id == id } ?: DefaultCity.TAIPEI`. Since `observeSavedCities()` is backed by a Room DAO `Flow`, any in-place upsert (including `current_location` re-detection) triggers a new emission and the City object is always fresh.

Note: Did NOT add the proposed `observeCityById(id)` DAO method. Observing the full saved-cities list and filtering by id is sufficient for current scale and avoids adding a new DAO query. A per-row `Flow<City?>` observe could be added as a future performance optimization if the city list grows large, but it is not a correctness issue.

---

### TD-003: Replace deprecated `Icons.Filled.HelpOutline` with `AutoMirrored` variant

**Introduced**: PR 02 (compile warning surfaced during PR 04 final build)
**Status**: Resolved — PR 06
**Resolution date**: 2026-04-27
**Severity**: Low

**What**

`core/designsystem/component/WeatherIcon.kt` uses `Icons.Filled.HelpOutline` for the `WeatherCondition.UNKNOWN` case. Material Icons deprecated this in favor of `Icons.AutoMirrored.Filled.HelpOutline`, which mirrors correctly under RTL locales.

**Affected**

- `:core:designsystem/component/WeatherIcon.kt`

**Resolution (PR 06)**

Replaced `Icons.Filled.HelpOutline` with `Icons.AutoMirrored.Filled.HelpOutline` in `core/designsystem/component/WeatherIcon.kt`. Build no longer emits the deprecation warning. RTL locales now render the icon correctly mirrored.

---

## Index

| ID | Title | Severity | Target | Status |
|----|-------|----------|--------|--------|
| TD-001 | Data source classes should be interfaces | Low | PR 07 | Resolved |
| TD-002 | `getCityById` suspend overhead in observe chain | Low | PR 05 | Resolved |
| TD-003 | Replace deprecated `HelpOutline` with `AutoMirrored` | Low | PR 06 | Resolved |
| TD-004 | `LocationProvider.lastLocation` has no staleness check | Low | TBD | Open |