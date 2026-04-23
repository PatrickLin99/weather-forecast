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

## Resolved Items

_(Nothing resolved yet.)_

---

## Index

| ID | Title | Severity | Target | Status |
|----|-------|----------|--------|--------|
| TD-001 | Data source classes should be interfaces | Low | PR 07 | Open |
