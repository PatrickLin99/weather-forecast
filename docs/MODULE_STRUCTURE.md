# Module Structure

Detailed package structure and responsibilities for every module.
Use this document to decide **where a new file belongs**.

> Naming note: All package names use the base `com.example.weatherforecast`,
> with module-specific sub-packages. This document abbreviates to
> `...weatherforecast.{module}.{sub}` for readability.

## Overview Table

| Module | Type | Purpose |
|--------|------|---------|
| `:app` | Android Application | Entry point, NavHost, Hilt app |
| `:core:model` | Kotlin JVM Library | Pure domain models |
| `:core:common` | Kotlin JVM Library | Result, AppError, Dispatchers |
| `:core:designsystem` | Android Library | Theme, typography, shared Composables |
| `:core:network` | Android Library | Retrofit, Open-Meteo APIs, DTOs |
| `:core:database` | Android Library | Room database, DAOs, entities |
| `:core:datastore` | Android Library | Preferences DataStore |
| `:core:location` | Android Library | FusedLocationProvider, Geocoder |
| `:core:domain` | Android Library | Repository interfaces, UseCases |
| `:core:data` | Android Library | Repository implementations |
| `:feature:weather` | Android Library | Today + weekly forecast screen |
| `:feature:citylist` | Android Library | City list, search, selection |

> `:core:domain` is an Android Library (not pure JVM) because Hilt annotations
> require the Android toolchain. See `docs/TECH_DECISIONS.md` for rationale.

---

## :core:model

**Purpose:** Pure Kotlin domain models. The shared vocabulary of the app.

**Dependencies:** none (plus `kotlinx-serialization` for `@Serializable` if needed on Navigation routes).

**Rules:**
- **No Android imports.** Not even `androidx.annotation`.
- **No framework imports.** No Retrofit, no Room annotations, no Hilt.
- Only data classes, sealed classes, enums.
- This module is depended on by almost everything, so keep it minimal and stable.

**Package tree:**
```
com.example.weatherforecast.core.model/
├── City.kt
├── Weather.kt
├── DailyForecast.kt
├── HourlyForecast.kt          // reserved for future use, empty list by default
├── TemperatureUnit.kt
└── WeatherCondition.kt
```

**Key types:**

| Type | Description |
|------|-------------|
| `City` | id, name, country, latitude, longitude, isCurrentLocation |
| `Weather` | current temperature, feelsLike, humidity, windSpeed, condition, daily, hourly |
| `DailyForecast` | date, tempMin, tempMax, condition, sunrise, sunset, precipitationProbability |
| `HourlyForecast` | time, temperature, condition (domain model only — not wired into UI yet) |
| `TemperatureUnit` | enum: `CELSIUS`, `FAHRENHEIT` |
| `WeatherCondition` | enum mapped from Open-Meteo WMO codes: `CLEAR`, `CLOUDY`, `RAIN`, `SNOW`, `THUNDERSTORM`, `FOG`, `UNKNOWN` |

---

## :core:common

**Purpose:** Cross-cutting utilities used by every other module.

**Dependencies:** `:core:model`, `kotlinx-coroutines-core`.

**Package tree:**
```
com.example.weatherforecast.core.common/
├── result/
│   ├── Result.kt              // sealed class Result<out T, out E>
│   └── ResultExtensions.kt    // map, flatMap, onSuccess, onFailure, etc.
├── error/
│   └── AppError.kt            // sealed class — all error types
├── dispatcher/
│   ├── Dispatcher.kt          // enum: IO, Default, Main
│   └── DispatcherQualifier.kt // @Dispatcher(IO) annotation
├── constant/
│   └── DefaultCity.kt         // Taipei fallback
└── di/
    └── DispatcherModule.kt    // Hilt @Module providing dispatchers
```

**Key types:**

| Type | Description |
|------|-------------|
| `Result<T, E>` | Custom sealed type: `Success(data: T)` / `Failure(error: E)` |
| `AppError` | Sealed class — see `docs/ERROR_HANDLING.md` for full hierarchy |
| `@Dispatcher(Dispatcher.IO)` | Hilt qualifier annotation for injecting dispatchers |
| `DefaultCity.TAIPEI` | Fallback city constant (name: Taipei, lat: 25.0330, lon: 121.5654) |

**Hilt bindings:**
- `DispatcherModule` provides `@Dispatcher(IO/Default/Main) CoroutineDispatcher`.

**Notes:**
- `Result` is an ordinary class with `isSuccess` / `isFailure` / `getOrNull` / `errorOrNull` — **not** Kotlin's built-in `kotlin.Result`.
- Never use `kotlin.Result` anywhere in this project.

---

## :core:designsystem

**Purpose:** Material 3 theme and shared Composables used across features.

**Dependencies:** `:core:model` (for types referenced in Composable previews), Compose BOM, Material 3, Coil.

**Package tree:**
```
com.example.weatherforecast.core.designsystem/
├── theme/
│   ├── Color.kt
│   ├── Theme.kt               // WeatherAppTheme { ... }
│   ├── Type.kt
│   └── Shape.kt
├── component/
│   ├── WeatherIcon.kt         // maps WeatherCondition → icon resource
│   ├── LoadingIndicator.kt
│   ├── ErrorState.kt          // full-screen error with retry
│   ├── EmptyState.kt
│   └── TemperatureText.kt     // formats with unit symbol
└── icon/
    └── WeatherAppIcons.kt     // central access to icon resources
```

**Key types:**

| Type | Description |
|------|-------------|
| `WeatherAppTheme` | Root Composable wrapping MaterialTheme |
| `WeatherIcon(condition, modifier)` | Displays icon for given `WeatherCondition` |
| `ErrorState(message, onRetry, modifier)` | Reusable error UI |
| `TemperatureText(value, unit, style)` | Consistent temperature display |

**Notes:**
- **No business logic.** Components receive all data as parameters.
- **No ViewModel references.** Pure presentational.
- Dark theme is **not** required for this project — keep Theme.kt minimal.

---

## :core:network

**Purpose:** HTTP layer. Open-Meteo API clients and DTOs.

**Dependencies:** `:core:model`, `:core:common`, Retrofit, OkHttp, kotlinx-serialization.

**Package tree:**
```
com.example.weatherforecast.core.network/
├── api/
│   ├── OpenMeteoForecastApi.kt    // /v1/forecast
│   └── OpenMeteoGeocodingApi.kt   // /v1/search
├── dto/
│   ├── ForecastResponseDto.kt
│   ├── CurrentWeatherDto.kt
│   ├── DailyForecastDto.kt
│   ├── HourlyForecastDto.kt
│   └── GeocodingResponseDto.kt
├── mapper/
│   ├── WeatherDtoMapper.kt         // DTO → domain Weather
│   ├── CityDtoMapper.kt            // DTO → domain City
│   └── WeatherCodeMapper.kt        // WMO code (Int) → WeatherCondition
├── datasource/
│   ├── WeatherRemoteDataSource.kt      // wraps OpenMeteoForecastApi
│   └── CityRemoteDataSource.kt         // wraps OpenMeteoGeocodingApi
├── util/
│   └── ApiCall.kt                  // apiCall { } helper — maps exceptions to AppError
└── di/
    └── NetworkModule.kt            // provides Retrofit, OkHttp, API services
```

**Key types:**

| Type | Description |
|------|-------------|
| `OpenMeteoForecastApi` | Retrofit interface for weather forecast |
| `OpenMeteoGeocodingApi` | Retrofit interface for city search |
| `WeatherRemoteDataSource` | High-level wrapper; returns `Result<Weather, AppError>` |
| `CityRemoteDataSource` | High-level wrapper; returns `Result<List<City>, AppError>` |
| `apiCall { ... }` | Inline helper wrapping suspend API calls with try/catch → `Result` |

**Hilt bindings:**
- `NetworkModule` provides: `Retrofit`, `OkHttpClient`, `OpenMeteoForecastApi`, `OpenMeteoGeocodingApi`, `Json` (kotlinx-serialization).

**Notes:**
- **DTOs never leak outside this module.** Data sources always map to domain models before returning.
- `@Serializable` is applied to DTOs using `kotlinx-serialization` (not Moshi, not Gson).
- `apiCall { }` is the central error-mapping point — converts `IOException` / `HttpException` / `SocketTimeoutException` / etc. into specific `AppError` subtypes.

---

## :core:database

**Purpose:** Room persistence layer.

**Dependencies:** `:core:model`, `:core:common`, Room runtime + ktx.

**Package tree:**
```
com.example.weatherforecast.core.database/
├── WeatherDatabase.kt           // @Database class
├── entity/
│   ├── CityEntity.kt
│   ├── CurrentWeatherEntity.kt
│   ├── DailyForecastEntity.kt
│   └── HourlyForecastEntity.kt  // reserved; schema included from start
├── dao/
│   ├── CityDao.kt
│   ├── WeatherDao.kt            // both current + daily + hourly
│   └── UserPreferencesDao.kt    // if used; else DataStore handles it
├── converter/
│   └── Converters.kt            // TypeConverter for Instant, WeatherCondition
├── mapper/
│   ├── CityEntityMapper.kt      // Entity ↔ domain City
│   └── WeatherEntityMapper.kt   // Entity ↔ domain Weather
├── datasource/
│   ├── CityLocalDataSource.kt   // wraps CityDao
│   └── WeatherLocalDataSource.kt// wraps WeatherDao
└── di/
    └── DatabaseModule.kt        // provides WeatherDatabase, DAOs
```

**Key types:**

| Type | Description |
|------|-------------|
| `WeatherDatabase` | Room database, version 1, includes all entities |
| `CityEntity` | id, name, country, lat, lon, isCurrentLocation, addedAt |
| `CurrentWeatherEntity` | cityId (PK), temperature, feelsLike, windSpeed, humidity, conditionCode, updatedAt |
| `DailyForecastEntity` | cityId + date (composite PK), tempMin, tempMax, conditionCode, sunrise, sunset |
| `WeatherLocalDataSource` | `observeWeather(cityId): Flow<Weather?>`, `upsertWeather(weather)` |
| `CityLocalDataSource` | `observeCities(): Flow<List<City>>`, `insertCity(city)`, `getCityById(id)` |

**Hilt bindings:**
- `DatabaseModule` provides: `WeatherDatabase` (Singleton), `CityDao`, `WeatherDao`.

**Notes:**
- Entities stay internal to this module (though Room requires them to be non-private).
- **Data sources always return domain models** (`Weather`, `City`), not entities.
- Use `upsert` (INSERT OR REPLACE) for write operations to simplify refresh logic.
- Foreign key: `CurrentWeatherEntity.cityId` → `CityEntity.id` with `CASCADE` on delete.

---

## :core:datastore

**Purpose:** User preferences (selected city, temperature unit).

**Dependencies:** `:core:model`, `:core:common`, androidx.datastore:datastore-preferences.

**Package tree:**
```
com.example.weatherforecast.core.datastore/
├── UserPreferencesDataSource.kt   // wraps DataStore<Preferences>
├── key/
│   └── PreferencesKeys.kt         // centralized key definitions
└── di/
    └── DataStoreModule.kt         // provides DataStore<Preferences>
```

**Key types:**

| Type | Description |
|------|-------------|
| `UserPreferencesDataSource` | Reads/writes: `selectedCityId: Flow<String?>`, `temperatureUnit: Flow<TemperatureUnit>` |
| `PreferencesKeys` | `SELECTED_CITY_ID` (stringPreferencesKey), `TEMPERATURE_UNIT` (stringPreferencesKey) |

**Hilt bindings:**
- `DataStoreModule` provides: `DataStore<Preferences>` (Singleton, file name `user_preferences.preferences_pb`).

**Notes:**
- DataStore is exposed as `Flow` throughout.
- Never expose `Preferences` or `DataStore` types outside this module — only the domain values.

---

## :core:location

**Purpose:** Wraps device location and geocoding APIs.

**Dependencies:** `:core:model`, `:core:common`, play-services-location, kotlinx-coroutines-android.

**Package tree:**
```
com.example.weatherforecast.core.location/
├── LocationProvider.kt             // wraps FusedLocationProviderClient
├── GeocoderWrapper.kt              // wraps android.location.Geocoder
├── datasource/
│   └── LocationDataSource.kt       // combines provider + geocoder → returns City
└── di/
    └── LocationModule.kt           // provides FusedLocationProviderClient
```

**Key types:**

| Type | Description |
|------|-------------|
| `LocationProvider.getCurrentLocation(): Result<Coordinates, AppError>` | Suspend function; respects permission state; timeout 5s |
| `GeocoderWrapper.reverseGeocode(lat, lon): Result<City, AppError>` | API 33+ uses async callback; older uses Dispatchers.IO |
| `LocationDataSource.getCurrentCity(): Result<City, AppError>` | Orchestrates both; returns `City(isCurrentLocation = true)` |

**Hilt bindings:**
- `LocationModule` provides: `FusedLocationProviderClient`.

**Notes:**
- **Permission checking is the caller's responsibility** (typically the ViewModel via a Compose permission API). This module assumes permission is already granted; if not, it returns `AppError.LocationPermissionDenied`.
- `GeocoderWrapper` handles API-level differences (33+ async listener vs. pre-33 sync IO call).
- All operations return `Result`, never throw.

---

## :core:domain

**Purpose:** Business contracts. Repository interfaces and UseCases.

**Dependencies:** `:core:model`, `:core:common`, Hilt (annotations only), kotlinx-coroutines-core.

**Package tree:**
```
com.example.weatherforecast.core.domain/
├── repository/
│   ├── WeatherRepository.kt
│   ├── CityRepository.kt
│   ├── LocationRepository.kt
│   └── UserPreferencesRepository.kt
└── usecase/
    ├── ResolveInitialCityUseCase.kt
    ├── ObserveSelectedCityWeatherUseCase.kt
    └── RefreshWeatherUseCase.kt
```

**Key interfaces:**

```kotlin
interface WeatherRepository {
    fun observeWeather(cityId: String): Flow<Weather?>
    suspend fun refreshWeather(city: City): Result<Unit, AppError>
}

interface CityRepository {
    fun observeSavedCities(): Flow<List<City>>
    suspend fun searchCities(query: String): Result<List<City>, AppError>
    suspend fun saveCity(city: City): Result<Unit, AppError>
    suspend fun deleteCity(cityId: String): Result<Unit, AppError>
    suspend fun getCityById(cityId: String): City?
    suspend fun upsertCurrentLocationCity(city: City): Result<Unit, AppError>
}

interface LocationRepository {
    suspend fun getCurrentCity(): Result<City, AppError>
}

interface UserPreferencesRepository {
    val selectedCityId: Flow<String?>
    val temperatureUnit: Flow<TemperatureUnit>
    suspend fun setSelectedCityId(cityId: String)
    suspend fun setTemperatureUnit(unit: TemperatureUnit)
}
```

**Key UseCases:**

| UseCase | Why it exists |
|---------|---------------|
| `ResolveInitialCityUseCase` | Orchestrates the fallback chain: location → last selected → DefaultCity.TAIPEI |
| `ObserveSelectedCityWeatherUseCase` | Combines `selectedCityId` flow with weather flow via `flatMapLatest` |
| `RefreshWeatherUseCase` | Triggers refresh for the currently selected city; used by pull-to-refresh |

**Notes:**
- Repositories that are "just passthrough" (e.g., `SearchCityUseCase` = call repository once) are **not** wrapped in UseCases. ViewModels call repositories directly in those cases.
- UseCase classes are `@Inject constructor(...)` — Hilt will inject them into ViewModels.

---

## :core:data

**Purpose:** Repository implementations. Orchestrates all data sources.

**Dependencies:** `:core:domain`, `:core:network`, `:core:database`, `:core:datastore`, `:core:location`, `:core:model`, `:core:common`.

**Package tree:**
```
com.example.weatherforecast.core.data/
├── repository/
│   ├── WeatherRepositoryImpl.kt
│   ├── CityRepositoryImpl.kt
│   ├── LocationRepositoryImpl.kt
│   └── UserPreferencesRepositoryImpl.kt
└── di/
    └── RepositoryModule.kt         // @Binds interface → impl
```

**Key types:**

| Type | Description |
|------|-------------|
| `WeatherRepositoryImpl` | Reads from `WeatherLocalDataSource` (Flow), refresh calls `WeatherRemoteDataSource` → Room upsert |
| `CityRepositoryImpl` | Combines `CityLocalDataSource` + `CityRemoteDataSource` (for search) |
| `LocationRepositoryImpl` | Thin wrapper around `LocationDataSource` from `:core:location` |
| `UserPreferencesRepositoryImpl` | Wraps `UserPreferencesDataSource` from `:core:datastore` |

**Hilt bindings:**
- `RepositoryModule` uses `@Binds` to bind each `*Impl` to its interface. All `@Singleton`.

**Notes:**
- This is the **only** module that knows about all data sources.
- Implementation classes should be `internal` where Kotlin allows — they're never referenced by name from outside.
- Keep repository methods thin: orchestrate data sources, map results, return. Complex logic belongs in UseCases.

---

## :feature:weather

**Purpose:** Today + weekly forecast screen.

**Dependencies:** `:core:domain`, `:core:model`, `:core:common`, `:core:designsystem`, Compose, Hilt ViewModel.

**Package tree:**
```
com.example.weatherforecast.feature.weather/
├── navigation/
│   └── WeatherNavigation.kt        // NavGraphBuilder.weatherScreen(...)
├── WeatherScreen.kt                // top-level Composable
├── WeatherViewModel.kt             // @HiltViewModel
├── WeatherUiState.kt               // sealed interface
├── component/
│   ├── CurrentWeatherHeader.kt     // big temperature + condition
│   ├── WeatherDetailsRow.kt        // feels-like, humidity, wind
│   ├── DailyForecastList.kt        // 7-day list
│   └── StaleDataBanner.kt          // offline indicator
└── ui/
    └── PreviewData.kt               // @Preview sample data
```

**Key types:**

| Type | Description |
|------|-------------|
| `WeatherRoute` | `@Serializable data object WeatherRoute` — Navigation 2.8+ type-safe route |
| `weatherScreen(onNavigateToCityList)` | NavGraphBuilder extension — the feature's entry point |
| `WeatherViewModel` | Exposes `uiState: StateFlow<WeatherUiState>`; handles `onRefresh()`, `onUnitToggle()` |
| `WeatherUiState` | Sealed: `Loading` / `Success(weather, city, isStale, transientMessage)` / `Error(error, canRetry)` |

**Notes:**
- ViewModel depends only on `:core:domain` types (Repositories, UseCases).
- Composables import only from `:core:designsystem` and this module's own `component/`.
- No direct import of `:core:data`, `:core:network`, `:core:database`, `:core:datastore`, or `:core:location`.

---

## :feature:citylist

**Purpose:** City list, search, and selection.

**Dependencies:** same shape as `:feature:weather`.

**Package tree:**
```
com.example.weatherforecast.feature.citylist/
├── navigation/
│   └── CityListNavigation.kt       // NavGraphBuilder.cityListScreen(...)
├── CityListScreen.kt
├── CityListViewModel.kt
├── CityListUiState.kt
└── component/
    ├── CitySearchBar.kt
    ├── SavedCityItem.kt
    └── SearchResultItem.kt
```

**Key types:**

| Type | Description |
|------|-------------|
| `CityListRoute` | `@Serializable data object CityListRoute` |
| `cityListScreen(onNavigateBack)` | NavGraphBuilder extension |
| `CityListViewModel` | Exposes `uiState`; handles `onSearchQueryChanged()`, `onCitySelected()`, `onCityAdded()`, `onCityDeleted()` |
| `CityListUiState` | Contains: `savedCities`, `searchQuery`, `searchResults` (as its own sub-state), `error` |

**Notes:**
- Search input is `debounce`-d (300ms) before hitting the repository.
- Tapping a city triggers `userPreferencesRepository.setSelectedCityId()` and `onNavigateBack()`.

---

## :app

**Purpose:** Application entry point. Composes all features into the running app.

**Dependencies:** `:feature:weather`, `:feature:citylist`, `:core:data` (to activate the Hilt binding module), `:core:designsystem`, `:core:common`, `:core:model`, Navigation Compose, Hilt, Activity Compose.

**Package tree:**
```
com.example.weatherforecast/
├── WeatherApp.kt                   // @HiltAndroidApp Application
├── MainActivity.kt                 // @AndroidEntryPoint ComponentActivity
└── navigation/
    └── WeatherAppNavHost.kt        // NavHost composing all features
```

**Key types:**

| Type | Description |
|------|-------------|
| `WeatherApp` | `@HiltAndroidApp class WeatherApp : Application()` |
| `MainActivity` | Hosts the Compose root; applies `WeatherAppTheme` |
| `WeatherAppNavHost` | Single NavHost with `startDestination = WeatherRoute`; wires feature callbacks to `navController` |

**Notes:**
- `:app` is the **only** place `NavController` is referenced.
- `:app` depends on `:core:data` solely so Hilt can discover `RepositoryModule`. No code in `:app` should reference classes from `:core:data` directly.

---

## Cross-Module Conventions

### Naming consistency

| Concept | Convention |
|---------|------------|
| Repository interface | `XxxRepository` in `:core:domain` |
| Repository implementation | `XxxRepositoryImpl` in `:core:data` |
| Remote data source | `XxxRemoteDataSource` in `:core:network` |
| Local data source | `XxxLocalDataSource` in `:core:database` (or `:core:datastore`) |
| DTO | `XxxDto` in `:core:network/dto/` |
| Entity | `XxxEntity` in `:core:database/entity/` |
| UseCase | `VerbNounUseCase` (e.g., `ResolveInitialCityUseCase`) |
| Navigation route | `XxxRoute` as `@Serializable data object` |
| Hilt module | `XxxModule` in each module's `di/` package |
| UiState | `XxxUiState` in feature module, as `sealed interface` |
| Composable screen | `XxxScreen` |
| Composable component | `NounDescription` (e.g., `CurrentWeatherHeader`) |

### File placement decision tree

When deciding where a new file belongs, ask in order:

1. **Is it a pure data class with no framework deps?** → `:core:model`
2. **Is it about errors, Result, dispatchers?** → `:core:common`
3. **Is it about the HTTP layer (DTOs, API, mappers)?** → `:core:network`
4. **Is it a Room entity, DAO, or database mapper?** → `:core:database`
5. **Is it about preferences storage?** → `:core:datastore`
6. **Is it about device location?** → `:core:location`
7. **Is it a Repository interface or UseCase?** → `:core:domain`
8. **Is it a Repository implementation?** → `:core:data`
9. **Is it a reusable Composable used across features?** → `:core:designsystem`
10. **Is it a screen-specific Composable or ViewModel?** → `:feature:*`
11. **Is it about app-level navigation or wiring?** → `:app`

When in doubt, re-read the module's "Purpose" line at the top of its section.
