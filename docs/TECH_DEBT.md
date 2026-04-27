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

### TD-001: Data source classes should be interfaces

**Introduced**: PR 03 (Stage 1)
**Status**: Open
**Severity**: Low

**What**

Data sources in `:core:network`, `:core:database`, and `:core:datastore` are currently concrete `class` declarations with `@Inject internal constructor(...)`. The idiomatic Clean Architecture pattern is:

- `interface XxxDataSource` (public, in the same module)
- `internal class XxxDataSourceImpl @Inject constructor(...)` (package-private impl)
- `@Binds` binding in the module's Hilt module

**Affected**

- `:core:network/datasource/WeatherRemoteDataSource`
- `:core:network/datasource/CityRemoteDataSource`
- `:core:database/datasource/WeatherLocalDataSource`
- `:core:database/datasource/CityLocalDataSource`
- `:core:datastore/UserPreferencesDataSource`

**Why deferred**

Discovered during PR 03 Stage 1 when `:core:data` needed to consume data sources defined in PR 02 as `internal class`. Applied minimum-viable fix (`@Inject internal constructor` pattern) to unblock PR 03 without expanding its scope.

**Impact of deferral**

- Functionality: unaffected
- Module boundaries: preserved (feature modules still cannot see data sources)
- Build: works
- Testability: slightly worse — harder to provide test fakes for concrete classes than for interfaces. MockK still works but requires `mockk<WeatherRemoteDataSource>()` calls that feel heavier than interface-based fakes.

**Target resolution**: PR 07 (tests + docs)

**Rationale for timing**

Writing Repository unit tests in PR 07 will create a clear motivation for this refactor ("interface is easier to fake than class"). The change will naturally fit that PR's narrative. If PR 07 is time-constrained, fall back to a standalone `refactor/data-source-interfaces` PR after PR 04.

**Proposed change (when resolving)**

For each data source, split into interface + impl + binding. Example for `WeatherRemoteDataSource`:

```kotlin
// :core:network/datasource/WeatherRemoteDataSource.kt
interface WeatherRemoteDataSource {
    suspend fun fetchWeather(city: City, unit: TemperatureUnit): Result<Weather, AppError>
}

// :core:network/datasource/WeatherRemoteDataSourceImpl.kt
internal class WeatherRemoteDataSourceImpl @Inject constructor(
    private val api: OpenMeteoForecastApi,
) : WeatherRemoteDataSource {
    override suspend fun fetchWeather(...) = apiCall { ... }
}

// :core:network/di/DataSourceBindModule.kt
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataSourceBindModule {
    @Binds
    internal abstract fun bindWeatherRemoteDataSource(
        impl: WeatherRemoteDataSourceImpl,
    ): WeatherRemoteDataSource
}
```

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

## Resolved Items

| ID | Title | Resolved in |
|----|-------|-------------|
| TD-002 | `getCityById` suspend overhead in observe chain | PR 05 |
| TD-003 | Replace deprecated `HelpOutline` with `AutoMirrored` | PR 06 |

---

## Index

| ID | Title | Severity | Target | Status |
|----|-------|----------|--------|--------|
| TD-001 | Data source classes should be interfaces | Low | PR 07 | Open |
| TD-002 | `getCityById` suspend overhead in observe chain | Low | PR 05 | Resolved |
| TD-003 | Replace deprecated `HelpOutline` with `AutoMirrored` | Low | PR 06 | Resolved |