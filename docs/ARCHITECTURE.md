# Architecture

This document describes the overall architecture of the Weather Forecast app.
Audience: developers (human or AI) working on the codebase.

## Guiding Principles

1. **Clean Architecture** — strict separation of concerns across three layers
2. **Single Source of Truth (SSOT)** — Room is the truth for UI-visible data
3. **Unidirectional Data Flow (UDF)** — state flows down, events flow up
4. **Dependency Inversion** — high-level modules depend on abstractions, not concretions
5. **Modularization by layer + by feature** — clear dependency graph enables parallel work and build-cache benefits

## Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Presentation Layer                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Composable Screens                                      │  │
│  │    ▲ state                                               │  │
│  │    │                                                     │  │
│  │  @HiltViewModel ViewModels                               │  │
│  │    - exposes StateFlow<UiState>                          │  │
│  │    - handles user events                                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│          Located in: :feature:weather, :feature:citylist        │
└────────────────────────────┬────────────────────────────────────┘
                             │ depends on
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Domain Layer                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Repository interfaces   (WeatherRepository, etc.)       │  │
│  │  UseCases                (when they add real value)      │  │
│  │  Domain Models           (Weather, City, Forecast)       │  │
│  └──────────────────────────────────────────────────────────┘  │
│          Located in: :core:domain, :core:model                  │
└────────────────────────────┬────────────────────────────────────┘
                             │ implemented by
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Data Layer                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Repository implementations                              │  │
│  │    orchestrate:                                          │  │
│  │      ├─ RemoteDataSource  ← :core:network (Retrofit)     │  │
│  │      ├─ LocalDataSource   ← :core:database (Room)        │  │
│  │      ├─ PreferencesStore  ← :core:datastore              │  │
│  │      └─ LocationProvider  ← :core:location               │  │
│  └──────────────────────────────────────────────────────────┘  │
│          Located in: :core:data                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Module Dependency Graph

```
                           ┌───────┐
                           │ :app  │
                           └───┬───┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
     ┌────────────────┐  ┌──────────┐  ┌─────────────────┐
     │ :feature:      │  │:feature: │  │   :core:data    │
     │ weather        │  │citylist  │  │                 │
     └───────┬────────┘  └────┬─────┘  └───────┬─────────┘
             │                │                │
             └────────┬───────┘                │
                      ▼                        │
              ┌───────────────┐                │
              │ :core:domain  │◄───────────────┘
              └───────┬───────┘                │ (core:data implements
                      │                        │  domain interfaces)
                      ▼                        ▼
              ┌───────────────┐        ┌───────────────────┐
              │ :core:model   │        │ :core:network     │
              └───────────────┘        │ :core:database    │
                      ▲                │ :core:datastore   │
                      │                │ :core:location    │
                      │                └─────────┬─────────┘
                      │                          │
                      └──────────────────────────┘
                         (all use core:model)

              ┌───────────────┐        ┌───────────────────┐
              │ :core:common  │        │:core:designsystem │
              └───────────────┘        └───────────────────┘
              (used by any module        (used by features
               that needs Result/Error    and :app for Theme)
               or Dispatchers)
```

**Key rules:**

- **Arrows point from dependent → dependency.**
- **`:feature:*` modules never import data-source modules directly.** They depend on `:core:domain` and receive implementations via Hilt.
- **`:core:data` is the only module** that composes all data sources.
- **`:app` wires everything together** via the Hilt graph and `NavHost`.

## Dependency Inversion

Features depend on abstractions (Repository interfaces in `:core:domain`), and those abstractions are implemented in `:core:data`. Hilt resolves the binding at runtime, so the feature compiles against the interface alone and never needs to see the concrete implementation or its transitive dependencies (Retrofit, Room, etc.).

### Layout

```
:feature:weather  ──────►  :core:domain
                           interface WeatherRepository    ◄─── abstraction
                                         ▲
                                         │ implements
                                         │
                           :core:data
                           class WeatherRepositoryImpl    ◄─── concretion
                              uses  ↓
                           :core:network (Retrofit)
                           :core:database (Room)
                           :core:datastore
```

- **Compile-time dependency:** feature → domain (interface only).
- **Runtime wiring:** Hilt injects `WeatherRepositoryImpl` when a feature asks for `WeatherRepository`.
- **Feature classpath does not include Retrofit or Room types.**

The feature knows *what* it needs (a `WeatherRepository`), not *how* it's implemented.

### What this buys us

| Benefit | How it works |
|---------|--------------|
| API swappable | Change only `:core:data` + `:core:network`. Features untouched. |
| Fast, pure-Kotlin tests | ViewModel tests use fake `WeatherRepository` implementations. No Robolectric, no Retrofit mocking. |
| Faster incremental builds | Changes to `:core:network` do not trigger feature module rebuilds. |
| Boundaries enforced by the compiler | An accidental feature → network import fails to compile — architectural drift is caught immediately. |

## Single Source of Truth (SSOT)

Room is the single source of truth for any data the UI observes. Network is a side effect that updates Room.

### Flow diagram

```
     ┌──────────────────┐
     │   Composable     │
     │  collect state   │
     └────────▲─────────┘
              │ StateFlow<UiState>
     ┌────────┴─────────┐
     │    ViewModel     │
     │  maps Flow<T>    │
     │  → UiState       │
     └────────▲─────────┘
              │ Flow<Weather>
     ┌────────┴─────────┐
     │   Repository     │
     │   .observeX()    │◄────────┐
     └────────▲─────────┘         │ emits when Room changes
              │                   │
              │ reads from        │ upserts into
              ▼                   │
     ┌──────────────────┐         │
     │      Room        │─────────┘
     │   (SSOT here)    │
     └────────▲─────────┘
              │ upsert
              │
     ┌────────┴─────────┐
     │  RemoteDataSrc   │
     │   (Retrofit)     │
     │   fetch & map    │
     └──────────────────┘
              ▲
              │ triggered by: first load, pull-to-refresh,
              │  or user action
```

### Concrete behavior

**Online, first launch:**
1. ViewModel calls `repository.observeWeather(city)` → returns `Flow<Weather>` from Room (empty first).
2. ViewModel simultaneously calls `repository.refreshWeather(city)` → network fetch → mapper → Room upsert.
3. Room emits new value → Flow emits → UI updates to Success state.

**Offline, returning user:**
1. ViewModel calls `repository.observeWeather(city)` → returns cached Weather from Room immediately.
2. ViewModel calls `repository.refreshWeather(city)` → fails with `AppError.NoNetwork`.
3. UI shows cached data with a "stale data" banner.

**Pull-to-refresh:**
1. User swipes down.
2. ViewModel calls `repository.refreshWeather(city)` → same as above.
3. If success: Flow emits new values automatically.
4. If failure: UI shows transient snackbar, keeps showing cached data.

## Unidirectional Data Flow (UDF)

### State down, events up

```
              ┌────────────┐
              │  UiState   │ (sealed interface)
              │  Loading   │
              │  Success   │
              │  Error     │
              └─────┬──────┘
                    │ StateFlow
                    ▼
         ┌────────────────────┐
         │   Composable       │
         │   observes &       │
         │   renders          │
         └──────────┬─────────┘
                    │ user action (click, swipe...)
                    ▼
         ┌────────────────────┐
         │   ViewModel        │
         │   - onCityChanged()│
         │   - onRefresh()    │
         │   - onUnitToggle() │
         └──────────┬─────────┘
                    │
                    ▼
         ┌────────────────────┐
         │   UseCase/Repo     │
         └────────────────────┘
```

- State flows **down**: Repository → ViewModel → Composable
- Events flow **up**: Composable → ViewModel (via method calls)
- Composables are **stateless** (beyond transient UI state like text field input or animation)

### UiState shape

```kotlin
sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Success(
        val weather: Weather,
        val city: City,
        val isStale: Boolean = false,           // offline, showing cached
        val transientMessage: String? = null,   // snackbar for refresh failures
    ) : WeatherUiState
    data class Error(
        val error: AppError,
        val canRetry: Boolean,
    ) : WeatherUiState
}
```

Error handling details: see `docs/ERROR_HANDLING.md`.

## Navigation Architecture

Feature modules **do not depend on each other**. Navigation is coordinated by `:app` through callback injection.

### How it works

Each feature exposes a `NavGraphBuilder` extension function that accepts callbacks for navigation events:

```kotlin
// :feature:weather/navigation/WeatherNavigation.kt
@Serializable data object WeatherRoute

fun NavGraphBuilder.weatherScreen(
    onNavigateToCityList: () -> Unit,
) {
    composable<WeatherRoute> {
        WeatherScreen(onNavigateToCityList = onNavigateToCityList)
    }
}
```

```kotlin
// :feature:citylist/navigation/CityListNavigation.kt
@Serializable data object CityListRoute

fun NavGraphBuilder.cityListScreen(
    onNavigateBack: () -> Unit,
) {
    composable<CityListRoute> {
        CityListScreen(onNavigateBack = onNavigateBack)
    }
}
```

`:app` composes them:

```kotlin
NavHost(navController, startDestination = WeatherRoute) {
    weatherScreen(
        onNavigateToCityList = { navController.navigate(CityListRoute) }
    )
    cityListScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### Why callback injection

- `NavController` is a UI-layer object and stays confined to `:app`, preserving layer boundaries.
- Features remain independently compilable and testable — no shared navigation abstraction to coordinate.
- This is the convention used by [Now in Android](https://github.com/android/nowinandroid), Google's official modularization reference.

### Cross-feature coordination via DataStore

When `feature:citylist` sets a new selected city, `feature:weather` doesn't need a direct call:

```
User taps city in CityList
    ↓
CityListViewModel.selectCity()
    ↓
userPreferencesRepository.setSelectedCityId(cityId)
    ↓ (writes to DataStore)
    ↓
WeatherViewModel (which observes selectedCityId Flow) reacts automatically
    ↓
New city's weather loads
```

This is a form of **event-based decoupling**: shared state via a single source (DataStore) replaces direct feature-to-feature calls.

## Dependency Injection Strategy

- **Hilt** is the only DI framework.
- Each core module provides its own `@Module` in a `di/` package, `@InstallIn(SingletonComponent::class)`.
- `:core:data` binds interfaces → implementations with `@Binds`.
- ViewModels use `@HiltViewModel` with constructor injection.
- Composables obtain ViewModels via `hiltViewModel()` from `hilt-navigation-compose`.

### Module ownership of Hilt bindings

| Module | Provides |
|--------|----------|
| `:core:common` | `CoroutineDispatcher` (qualified `@Dispatcher(IO/Default/Main)`) |
| `:core:network` | Retrofit, OkHttp, `OpenMeteoApi`, `GeocodingApi` |
| `:core:database` | `WeatherDatabase`, all DAOs |
| `:core:datastore` | `DataStore<Preferences>` |
| `:core:location` | `FusedLocationProviderClient`, Geocoder wrapper |
| `:core:data` | `@Binds` all Repository interfaces to their implementations |
| `:app` | `@HiltAndroidApp` application; no new bindings typically |

All modules are `SingletonComponent` — no request/screen-scoped bindings needed.

## Testing Strategy

| Layer | What to test | Tools |
|-------|--------------|-------|
| Repository | SSOT behavior: fake data sources, verify Flow emissions and Room writes | JUnit4, MockK, Turbine, coroutines-test |
| UseCase | Logic (especially `ResolveInitialCityUseCase` fallback chain) | JUnit4, MockK |
| ViewModel | UiState transitions on user actions | JUnit4, Turbine, MainDispatcherRule |
| UI | **Out of scope.** Not required for this project. | — |

Tests live in `src/test/` of each respective module. No instrumented tests.

## Error Handling (Summary)

- All errors are modeled as `AppError` (sealed class in `:core:common`).
- Repositories return `Result<T, AppError>` (custom type, not Kotlin's built-in).
- UI maps `AppError` to user-facing messages.
- Cached data is preferred over error screens when available.
- No code path may result in a crash or blank screen.

Details: see `docs/ERROR_HANDLING.md`.

## Key Technology Decisions (Summary)

| Area | Choice | Rationale |
|------|--------|-----------|
| API | Open-Meteo | Free, no key, zero-friction for reviewers |
| Serialization | kotlinx.serialization | Kotlin-first, no annotation processing |
| Persistence | Room + DataStore Preferences | Room for structured data, DataStore for settings |
| Concurrency | Kotlin Coroutines + Flow | Android-standard; composes well with Compose |
| DI | Hilt | Google-recommended for Android |
| Navigation | Navigation Compose with type-safe routes | Official modern approach |
| Build | Version Catalog + Convention Plugins | Matches Now in Android; scales cleanly |

Details and alternatives considered: see `docs/TECH_DECISIONS.md`.

## References

- [Guide to app architecture (Google)](https://developer.android.com/topic/architecture)
- [Guide to Android app modularization (Google)](https://developer.android.com/topic/modularization)
- [Now in Android sample](https://github.com/android/nowinandroid)
- [Dependency Inversion Principle (SOLID)](https://en.wikipedia.org/wiki/Dependency_inversion_principle)
