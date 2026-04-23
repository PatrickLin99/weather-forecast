# PR 03 — First Vertical Slice: Weather Screen Detailed Spec

> Companion to `docs/DEVELOPMENT_PLAN.md` § PR 03.
> **Execution mode: 3-stage with checkpoints between stages.**
>
> - Stage 1: Domain + Data layers (pure logic, no UI)
> - Stage 2: `:feature:weather` (UI, ViewModel, UiState)
> - Stage 3: `:app` integration (Hilt app, MainActivity, NavHost)
>
> After each stage, run `./gradlew build` (and for Stage 3, `./gradlew :app:installDebug`),
> then stop and wait for the user to verify before proceeding to the next stage.

## Goal Recap

Deliver the first end-to-end vertical slice: real Taipei weather appears on screen, fetched from Open-Meteo, cached in Room, observed via Flow, rendered in Compose.

**End state after this PR:**
- Launching the app shows Taipei's current weather + 7-day forecast.
- Offline (airplane mode) after first successful fetch → shows cached data with a stale banner.
- Cold-start offline → shows full-screen error with retry.
- No crashes in any scenario.

**What this PR does NOT include:**
- Location detection (PR 05)
- City switching or city list (PR 04)
- Unit toggle / pull-to-refresh (PR 06)
- Tests beyond what's trivially useful

## Prerequisites

- [ ] PR 02 merged to `main`.
- [ ] Local `main` is up to date.
- [ ] `./gradlew clean build` passes on main.
- [ ] Branch created: `git checkout -b feat/03-weather-vertical`.
- [ ] `CLAUDE.md` "Current PR" section updated.

## Reference Documents (Must-Read)

1. **`docs/ARCHITECTURE.md`** — SSOT diagram, UDF flow, navigation pattern
2. **`docs/MODULE_STRUCTURE.md`** — `:core:domain`, `:core:data`, `:feature:weather`, `:app` sections
3. **`docs/ERROR_HANDLING.md`** — UiState transitions, AppError → user message mapping
4. **`docs/CODING_CONVENTIONS.md`** — stateIn pattern, Hilt @Binds, Composable conventions
5. **Previous PRs' outputs** — `:core:common`, `:core:network`, `:core:database`, `:core:datastore` already exist from PR 02

---

# STAGE 1: Domain + Data Layers

**Scope:** `:core:domain` + `:core:data` modules.
**Verification:** `./gradlew :core:domain:build :core:data:build` passes.
**Checkpoint:** Stop after this stage, wait for user confirmation.

## Module A: `:core:domain`

### Files to create

```
core/domain/src/main/kotlin/com/example/weatherforecast/core/domain/
├── repository/
│   ├── WeatherRepository.kt
│   ├── CityRepository.kt
│   └── UserPreferencesRepository.kt
└── usecase/
    ├── ResolveInitialCityUseCase.kt
    ├── ObserveSelectedCityWeatherUseCase.kt
    └── RefreshWeatherUseCase.kt
```

> Note: `LocationRepository` is deferred to PR 05.
> Note: Package uses `com.example.weatherforecast` (matching PR 02's actual namespace).

### Module Gradle config

**`core/domain/build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
}

android {
    namespace = "com.example.weatherforecast.core.domain"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.kotlinx.coroutines.core)
}
```

### File contents

**`repository/WeatherRepository.kt`**:
```kotlin
package com.example.weatherforecast.core.domain.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    /**
     * Observes cached weather for a city. Emits `null` when no cache exists.
     * This is the Single Source of Truth for the UI — always observe this, never call refresh directly for display purposes.
     */
    fun observeWeather(cityId: String, unit: TemperatureUnit): Flow<Weather?>

    /**
     * Fetches fresh weather from the network and upserts into Room.
     * UI observes `observeWeather` to see the result.
     * Returns Success(Unit) on completion; Failure(AppError) on error.
     */
    suspend fun refreshWeather(city: City, unit: TemperatureUnit): Result<Unit, AppError>
}
```

**`repository/CityRepository.kt`**:
```kotlin
package com.example.weatherforecast.core.domain.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.Flow

interface CityRepository {
    /**
     * Observes all saved cities (ordered by addedAt DESC).
     */
    fun observeSavedCities(): Flow<List<City>>

    /**
     * Fetches a single city by id. Returns null if not found.
     */
    suspend fun getCityById(cityId: String): City?

    /**
     * Upserts a city (user-selected or default fallback).
     * Idempotent — same id will overwrite.
     */
    suspend fun saveCity(city: City): Result<Unit, AppError>

    /**
     * Deletes a city by id. Cascades to delete its weather cache (via FK).
     */
    suspend fun deleteCity(cityId: String): Result<Unit, AppError>

    // Search and current-location methods are deferred to later PRs.
}
```

**`repository/UserPreferencesRepository.kt`**:
```kotlin
package com.example.weatherforecast.core.domain.repository

import com.example.weatherforecast.core.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val selectedCityId: Flow<String?>
    val temperatureUnit: Flow<TemperatureUnit>

    suspend fun setSelectedCityId(cityId: String)
    suspend fun setTemperatureUnit(unit: TemperatureUnit)
}
```

**`usecase/ResolveInitialCityUseCase.kt`**:

**Important:** This PR's version is **simplified** — no location branch. Full fallback chain with location arrives in PR 05.

```kotlin
package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves which city to show on app launch.
 *
 * Current fallback chain (PR 03):
 *   1. Last selected city (from DataStore) → fetch from Room
 *   2. Default: Taipei (DefaultCity.TAIPEI)
 *
 * PR 05 will insert a location-detection branch at the top.
 */
class ResolveInitialCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(): City {
        val lastCityId = userPreferencesRepository.selectedCityId.first()
        if (lastCityId != null) {
            cityRepository.getCityById(lastCityId)?.let { return it }
        }
        return DefaultCity.TAIPEI
    }
}
```

**`usecase/ObserveSelectedCityWeatherUseCase.kt`**:

This UseCase exists because it **combines** the selectedCityId Flow with the weather Flow using `flatMapLatest` — genuine composition, not a pass-through.

```kotlin
package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.repository.WeatherRepository
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.Weather
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Observes the weather for the currently-selected city.
 *
 * Emits pairs of (City, Weather?) where:
 *   - Weather is null when cache is empty (triggers Loading state in UI)
 *   - Weather is non-null when Room has data
 *
 * Uses flatMapLatest so that when selectedCityId changes, the previous
 * city's weather flow is cancelled and the new city's flow begins.
 */
class ObserveSelectedCityWeatherUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cityRepository: CityRepository,
    private val weatherRepository: WeatherRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Pair<City, Weather?>> {
        return combine(
            userPreferencesRepository.selectedCityId,
            userPreferencesRepository.temperatureUnit,
        ) { cityId, unit -> cityId to unit }
            .flatMapLatest { (cityId, unit) ->
                val city = cityId?.let { cityRepository.getCityById(it) } ?: DefaultCity.TAIPEI
                weatherRepository.observeWeather(city.id, unit).let { weatherFlow ->
                    kotlinx.coroutines.flow.map(weatherFlow) { weather -> city to weather }
                }
            }
    }
}
```

Wait — the above `flatMapLatest` body has a subtle issue (nested `map` on a Flow variable). Use this cleaner form:

```kotlin
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Pair<City, Weather?>> {
        return combine(
            userPreferencesRepository.selectedCityId,
            userPreferencesRepository.temperatureUnit,
        ) { cityId, unit -> cityId to unit }
            .flatMapLatest { (cityId, unit) ->
                flow {
                    val city = cityId?.let { cityRepository.getCityById(it) } ?: DefaultCity.TAIPEI
                    emitAll(
                        weatherRepository.observeWeather(city.id, unit)
                            .map { weather -> city to weather }
                    )
                }
            }
    }
```

Requires imports: `kotlinx.coroutines.flow.flow`, `kotlinx.coroutines.flow.emitAll`, `kotlinx.coroutines.flow.map`.

**`usecase/RefreshWeatherUseCase.kt`**:

```kotlin
package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.repository.WeatherRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Triggers a network fetch for the given city and upserts into Room.
 * UI observes `observeWeather` to see the result after this completes.
 */
class RefreshWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(city: City): Result<Unit, AppError> {
        val unit = userPreferencesRepository.temperatureUnit.first()
        return weatherRepository.refreshWeather(city, unit)
    }
}
```

---

## Module B: `:core:data`

### Files to create

```
core/data/src/main/kotlin/com/example/weatherforecast/core/data/
├── repository/
│   ├── WeatherRepositoryImpl.kt
│   ├── CityRepositoryImpl.kt
│   └── UserPreferencesRepositoryImpl.kt
└── di/
    └── RepositoryModule.kt
```

### Module Gradle config

**`core/data/build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
}

android {
    namespace = "com.example.weatherforecast.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.model)
    implementation(projects.core.common)
}
```

### File contents

**`repository/WeatherRepositoryImpl.kt`**:

The **single most important class in this PR**. It implements SSOT: observe reads from Room, refresh writes to Room.

```kotlin
package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.common.result.flatMap
import com.example.weatherforecast.core.database.datasource.WeatherLocalDataSource
import com.example.weatherforecast.core.domain.repository.WeatherRepository
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import com.example.weatherforecast.core.network.datasource.WeatherRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WeatherRepositoryImpl @Inject constructor(
    private val remote: WeatherRemoteDataSource,
    private val local: WeatherLocalDataSource,
) : WeatherRepository {

    override fun observeWeather(cityId: String, unit: TemperatureUnit): Flow<Weather?> =
        local.observeWeather(cityId, unit)

    override suspend fun refreshWeather(city: City, unit: TemperatureUnit): Result<Unit, AppError> =
        remote.fetchWeather(city, unit)
            .flatMap { weather -> local.upsertWeather(weather) }
}
```

**Key design points:**
- `observeWeather` returns Flow from Room — it never touches the network.
- `refreshWeather` fetches from network, then writes to Room. UI observes Room via `observeWeather`, so fresh data appears automatically.
- No in-memory caching. Room is the only cache.

**`repository/CityRepositoryImpl.kt`**:

```kotlin
package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.database.datasource.CityLocalDataSource
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CityRepositoryImpl @Inject constructor(
    private val local: CityLocalDataSource,
) : CityRepository {

    override fun observeSavedCities(): Flow<List<City>> =
        local.observeAllCities()

    override suspend fun getCityById(cityId: String): City? =
        local.getCityById(cityId)

    override suspend fun saveCity(city: City): Result<Unit, AppError> =
        local.upsertCity(city)

    override suspend fun deleteCity(cityId: String): Result<Unit, AppError> =
        local.deleteCity(cityId)
}
```

**Note:** If `CityLocalDataSource` from PR 02 doesn't have all these method names exactly, adjust one or the other to match. The interface is the contract; data source method names are an implementation detail.

**`repository/UserPreferencesRepositoryImpl.kt`**:

```kotlin
package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.datastore.UserPreferencesDataSource
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : UserPreferencesRepository {

    override val selectedCityId: Flow<String?> = dataSource.selectedCityId
    override val temperatureUnit: Flow<TemperatureUnit> = dataSource.temperatureUnit

    override suspend fun setSelectedCityId(cityId: String) {
        dataSource.setSelectedCityId(cityId)
    }

    override suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataSource.setTemperatureUnit(unit)
    }
}
```

**`di/RepositoryModule.kt`**:

```kotlin
package com.example.weatherforecast.core.data.di

import com.example.weatherforecast.core.data.repository.CityRepositoryImpl
import com.example.weatherforecast.core.data.repository.UserPreferencesRepositoryImpl
import com.example.weatherforecast.core.data.repository.WeatherRepositoryImpl
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindCityRepository(impl: CityRepositoryImpl): CityRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
}
```

---

## Stage 1 Verification

Run these commands in order:

```bash
./gradlew :core:domain:build
./gradlew :core:data:build
./gradlew build
```

All should succeed. Build time grows noticeably now that more modules have real content.

Also check:
```bash
# Domain must not depend on data-source modules
grep -rn "import com.example.weatherforecast.core.network" core/domain/src/ 2>/dev/null
grep -rn "import com.example.weatherforecast.core.database" core/domain/src/ 2>/dev/null
grep -rn "import com.example.weatherforecast.core.datastore" core/domain/src/ 2>/dev/null
# All three should return nothing.
```

### Stage 1 Commits

Suggested sequence (5 commits):

```
feat: add core:domain with repository interfaces
feat: add WeatherRepository use cases
feat: add CityRepositoryImpl and UserPreferencesRepositoryImpl
feat: add WeatherRepositoryImpl with SSOT logic
feat: add RepositoryModule with Hilt bindings
```

Or condense to 2-3 if that feels cleaner. The key is not a single monolithic commit.

### STOP HERE

After Stage 1 verification passes, **report to user**:
- Build results
- Any deviation from spec
- Any unexpected challenges

Wait for user to say "proceed to Stage 2".

---

# STAGE 2: Feature:Weather UI Layer

**Scope:** `:feature:weather` module.
**Verification:** `./gradlew :feature:weather:build` passes.
**Checkpoint:** Stop after this stage, wait for user confirmation.

## Module: `:feature:weather`

### Files to create

```
feature/weather/src/main/kotlin/com/example/weatherforecast/feature/weather/
├── navigation/
│   └── WeatherNavigation.kt
├── WeatherScreen.kt
├── WeatherViewModel.kt
├── WeatherUiState.kt
├── component/
│   ├── CurrentWeatherHeader.kt
│   ├── WeatherDetailsRow.kt
│   ├── DailyForecastList.kt
│   └── StaleDataBanner.kt
└── ui/
    └── PreviewData.kt
```

### Module Gradle config

**`feature/weather/build.gradle.kts`**:

```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.feature)
}

android {
    namespace = "com.example.weatherforecast.feature.weather"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
}
```

**Enforce:** No dependency on `core:network`, `core:database`, `core:datastore`, `core:location`, or `core:data`.

### File contents

**`WeatherUiState.kt`**:

```kotlin
package com.example.weatherforecast.feature.weather

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.Weather

sealed interface WeatherUiState {
    /** Initial state; no city resolved yet. */
    data object Loading : WeatherUiState

    /** Weather data available. May be stale (offline with cache). */
    data class Success(
        val weather: Weather,
        val city: City,
        val isStale: Boolean = false,
        /** One-shot message (e.g., refresh failed but cache shown). Null when no message. */
        val transientMessage: String? = null,
    ) : WeatherUiState

    /** Cold-start failure (no cache, network unavailable). */
    data class Error(
        val error: AppError,
        val canRetry: Boolean,
    ) : WeatherUiState
}
```

**`WeatherViewModel.kt`**:

The second most important class in this PR. Mirrors the SSOT flow in UDF form.

```kotlin
package com.example.weatherforecast.feature.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.onFailure
import com.example.weatherforecast.core.domain.usecase.ObserveSelectedCityWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.RefreshWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.ResolveInitialCityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val resolveInitialCity: ResolveInitialCityUseCase,
    private val observeSelectedCityWeather: ObserveSelectedCityWeatherUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
) : ViewModel() {

    /** Transient UI signals (e.g., "refresh failed"). Merged into Success state. */
    private val _transientMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WeatherUiState> = combine(
        observeSelectedCityWeather(),
        _transientMessage,
    ) { (city, weather), message ->
        when {
            weather != null -> WeatherUiState.Success(
                weather = weather,
                city = city,
                isStale = message != null,
                transientMessage = message,
            )
            else -> WeatherUiState.Loading
        } as WeatherUiState
    }
        .catch { emit(WeatherUiState.Error(AppError.Unexpected(it), canRetry = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherUiState.Loading,
        )

    init {
        // Resolve initial city and trigger first fetch
        viewModelScope.launch {
            try {
                val initialCity = resolveInitialCity()
                // Ensure the city exists in DB before triggering selection / refresh
                // (needed for DefaultCity fallback on fresh install)
                refreshWeather(initialCity).onFailure { handleRefreshFailure(it) }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _transientMessage.value = "Initial load failed"
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            val currentCity = (uiState.value as? WeatherUiState.Success)?.city ?: return@launch
            refreshWeather(currentCity).onFailure { handleRefreshFailure(it) }
        }
    }

    fun onDismissTransientMessage() {
        _transientMessage.value = null
    }

    private fun handleRefreshFailure(error: AppError) {
        _transientMessage.value = error.toUserMessage()
    }
}

private fun AppError.toUserMessage(): String = when (this) {
    AppError.NoNetwork -> "No internet connection"
    AppError.NetworkTimeout -> "Request timed out"
    is AppError.ServerError -> "Server error. Please try again later."
    AppError.UnknownNetworkError -> "Network error"
    AppError.DatabaseError -> "Local data error"
    is AppError.DataParsingError -> "Unexpected data format"
    is AppError.Unexpected -> "Something went wrong"
    // Location / CityNotFound / GeocoderFailed are not reachable in this PR
    else -> "Something went wrong"
}
```

**Critical detail:** the `init` block triggers `refreshWeather` with the resolved city. On fresh install (no DataStore state), `DefaultCity.TAIPEI` is used — but note that `DefaultCity.TAIPEI` is not in Room yet. The `refreshWeather` call will persist weather for `cityId = "default_taipei"` into `CurrentWeatherEntity`, which depends on `CityEntity` row existing due to the foreign key. **Handle this:**

- Either make `ResolveInitialCityUseCase` **also** upsert the resolved city into `CityRepository` on fresh DefaultCity path, OR
- Modify `refreshWeather` / `WeatherRepositoryImpl.refreshWeather` to upsert the city row before upserting weather.

**Recommended approach:** Modify `ResolveInitialCityUseCase`:

```kotlin
class ResolveInitialCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(): City {
        val lastCityId = userPreferencesRepository.selectedCityId.first()
        if (lastCityId != null) {
            cityRepository.getCityById(lastCityId)?.let { return it }
        }
        // Ensure DefaultCity is persisted so FK-dependent weather writes succeed.
        cityRepository.saveCity(DefaultCity.TAIPEI)
        userPreferencesRepository.setSelectedCityId(DefaultCity.TAIPEI.id)
        return DefaultCity.TAIPEI
    }
}
```

This makes the fallback self-healing: subsequent launches find `DefaultCity.TAIPEI` via the `selectedCityId` path.

**`WeatherScreen.kt`**:

```kotlin
package com.example.weatherforecast.feature.weather

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherforecast.core.designsystem.component.ErrorState
import com.example.weatherforecast.core.designsystem.component.LoadingIndicator
import com.example.weatherforecast.feature.weather.component.CurrentWeatherHeader
import com.example.weatherforecast.feature.weather.component.DailyForecastList
import com.example.weatherforecast.feature.weather.component.StaleDataBanner
import com.example.weatherforecast.feature.weather.component.WeatherDetailsRow

@Composable
fun WeatherScreen(
    onNavigateToCityList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeatherViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WeatherContent(
        uiState = uiState,
        onRetry = viewModel::onRefresh,
        onNavigateToCityList = onNavigateToCityList,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherContent(
    uiState: WeatherUiState,
    onRetry: () -> Unit,
    onNavigateToCityList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    when (uiState) {
                        is WeatherUiState.Success -> Text(uiState.city.name)
                        else -> Text("Weather")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToCityList) {
                        Text("Cities")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                is WeatherUiState.Loading -> LoadingIndicator()
                is WeatherUiState.Error -> ErrorState(
                    message = "Unable to load weather",
                    onRetry = if (uiState.canRetry) onRetry else null,
                    modifier = Modifier.fillMaxSize(),
                )
                is WeatherUiState.Success -> WeatherSuccessContent(state = uiState)
            }
        }
    }
}

@Composable
private fun WeatherSuccessContent(state: WeatherUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isStale && state.transientMessage != null) {
            StaleDataBanner(message = state.transientMessage)
        }
        CurrentWeatherHeader(weather = state.weather)
        WeatherDetailsRow(weather = state.weather)
        DailyForecastList(forecasts = state.weather.daily)
    }
}
```

**`navigation/WeatherNavigation.kt`**:

```kotlin
package com.example.weatherforecast.feature.weather.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.weatherforecast.feature.weather.WeatherScreen
import kotlinx.serialization.Serializable

@Serializable
data object WeatherRoute

fun NavGraphBuilder.weatherScreen(
    onNavigateToCityList: () -> Unit,
) {
    composable<WeatherRoute> {
        WeatherScreen(onNavigateToCityList = onNavigateToCityList)
    }
}
```

**`component/CurrentWeatherHeader.kt`**:

```kotlin
package com.example.weatherforecast.feature.weather.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weatherforecast.core.designsystem.component.TemperatureText
import com.example.weatherforecast.core.designsystem.component.WeatherIcon
import com.example.weatherforecast.core.model.Weather

@Composable
internal fun CurrentWeatherHeader(
    weather: Weather,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WeatherIcon(
            condition = weather.condition,
            modifier = Modifier.size(96.dp),
        )
        TemperatureText(
            value = weather.temperature,
            unit = weather.unit,
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = weather.condition.name,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
```

**`component/WeatherDetailsRow.kt`**:

Shows feels-like, humidity, wind speed in a row.

```kotlin
@Composable
internal fun WeatherDetailsRow(
    weather: Weather,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        WeatherDetailItem(label = "Feels like", value = "${weather.feelsLike.toInt()}°")
        WeatherDetailItem(label = "Humidity", value = "${weather.humidity}%")
        WeatherDetailItem(label = "Wind", value = "${weather.windSpeed.toInt()} m/s")
    }
}

@Composable
private fun WeatherDetailItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
```

**`component/DailyForecastList.kt`**:

```kotlin
@Composable
internal fun DailyForecastList(
    forecasts: List<DailyForecast>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("7-Day Forecast", style = MaterialTheme.typography.titleMedium)
        forecasts.forEach { forecast ->
            DailyForecastRow(forecast = forecast)
        }
    }
}

@Composable
private fun DailyForecastRow(forecast: DailyForecast) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = forecast.date.toString(),
            modifier = Modifier.weight(1f),
        )
        WeatherIcon(
            condition = forecast.condition,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text("${forecast.tempMin.toInt()}° / ${forecast.tempMax.toInt()}°")
    }
}
```

**`component/StaleDataBanner.kt`**:

```kotlin
@Composable
internal fun StaleDataBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
```

**`ui/PreviewData.kt`**:

```kotlin
package com.example.weatherforecast.feature.weather.ui

internal object PreviewData {
    // Used by @Preview composables. Fill with sample Weather / City objects.
    // Can be stubbed; real previews are not critical for PR 03 delivery.
}
```

---

## Stage 2 Verification

```bash
./gradlew :feature:weather:build
```

Must pass. If Compose compiler complains about missing preview data, that's fine — previews are optional.

Also verify boundary:
```bash
grep -rn "import com.example.weatherforecast.core.network" feature/weather/src/ 2>/dev/null
grep -rn "import com.example.weatherforecast.core.database" feature/weather/src/ 2>/dev/null
grep -rn "import com.example.weatherforecast.core.datastore" feature/weather/src/ 2>/dev/null
grep -rn "import com.example.weatherforecast.core.data" feature/weather/src/ 2>/dev/null
# All four should return nothing.
```

### Stage 2 Commits

Suggested:
```
feat: add WeatherUiState and WeatherViewModel
feat: add WeatherScreen and navigation
feat: add weather display components
```

### STOP HERE

Report Stage 2 build results. Wait for user to say "proceed to Stage 3".

---

# STAGE 3: App Integration

**Scope:** Update `:app` module with Hilt Application, MainActivity, and NavHost.
**Verification:** `./gradlew :app:installDebug` + manual emulator check.

## Files to modify/create

```
app/src/main/kotlin/com/example/weatherforecast/
├── WeatherApp.kt                ← NEW
├── MainActivity.kt              ← MODIFY (use WeatherAppTheme + NavHost)
└── navigation/
    └── WeatherAppNavHost.kt     ← NEW
```

Also modify:
- `app/build.gradle.kts` — add dependencies on feature module, data module, Hilt plugin
- `app/src/main/AndroidManifest.xml` — register `WeatherApp` as the Application class

## File contents

**`WeatherApp.kt`**:

```kotlin
package com.example.weatherforecast

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WeatherApp : Application()
```

**`MainActivity.kt`** (update):

```kotlin
package com.example.weatherforecast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.weatherforecast.core.designsystem.theme.WeatherAppTheme
import com.example.weatherforecast.navigation.WeatherAppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    WeatherAppNavHost(navController = navController)
                }
            }
        }
    }
}
```

**`navigation/WeatherAppNavHost.kt`**:

```kotlin
package com.example.weatherforecast.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.weatherforecast.feature.weather.navigation.WeatherRoute
import com.example.weatherforecast.feature.weather.navigation.weatherScreen

@Composable
fun WeatherAppNavHost(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = WeatherRoute,
    ) {
        weatherScreen(
            onNavigateToCityList = {
                // TODO PR 04: navigate to city list
            },
        )
    }
}
```

**`app/build.gradle.kts`** (append to existing):

```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.application)
    alias(libs.plugins.weatherapp.android.hilt)  // ← ADD if not already applied
}

android {
    namespace = "com.example.weatherforecast"
    // ... keep existing config
}

dependencies {
    // Feature
    implementation(projects.feature.weather)
    // Data layer — must be imported by :app to activate RepositoryModule Hilt bindings
    implementation(projects.core.data)

    // Existing core dependencies
    implementation(projects.core.designsystem)
    implementation(projects.core.common)
    implementation(projects.core.model)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Existing Compose/AndroidX
    // ...
}
```

**`AndroidManifest.xml`** (update `<application>` tag):

```xml
<application
    android:name=".WeatherApp"
    android:allowBackup="true"
    ...>
    <!-- rest unchanged -->
</application>
```

---

## Stage 3 Verification

```bash
# Full build
./gradlew clean build

# Install to emulator (assumes emulator running)
./gradlew :app:installDebug
```

Then **manually on the emulator**:

1. **Online scenario:** Launch the app with internet connected.
   - ✅ Loading indicator shows briefly
   - ✅ Taipei weather appears: current temp, condition icon, 7-day list
   - ✅ No crash
   - ✅ TopAppBar shows "Taipei"

2. **Offline-with-cache scenario:** After step 1, turn on airplane mode, force-stop and relaunch.
   - ✅ Taipei weather appears immediately (from Room)
   - ✅ Top banner or snackbar hints at offline / refresh failure
   - ✅ No crash

3. **Cold-start offline scenario:** Uninstall app, turn on airplane mode, install and launch.
   - ✅ Full-screen error state appears with "Retry" button
   - ✅ No crash

### Stage 3 Commits

```
feat: add WeatherApp Hilt application class
feat: add WeatherAppNavHost and MainActivity integration
```

---

## Final PR Verification

After Stage 3:

```bash
# Everything passes
./gradlew clean build
./gradlew :app:installDebug

# Module boundary checks
grep -rn "import com.example.weatherforecast.core.network\|import com.example.weatherforecast.core.database\|import com.example.weatherforecast.core.datastore\|import com.example.weatherforecast.core.data" feature/ 2>/dev/null
# Should return nothing
```

## Full Commit Summary

Expected total: 8-12 commits across the 3 stages. Not a single mega-commit.

## Post-PR Retrospective

Fill in after merge:
- Total time taken:
- Which stage took longest?
- Any re-work needed between stages?
- `flatMapLatest` in `ObserveSelectedCityWeatherUseCase` — did it work first try?
- `DefaultCity` FK issue — was the pre-emptive upsert sufficient?

### Bugs found during Stage 3 emulator validation

- **WeatherViewModel `combine` chain missed the "cache empty + refresh failed" Error path.** Spec's two-branch shape (`weather != null → Success`, `else → Loading`) left a cold-start offline scenario stuck in Loading forever, violating `ERROR_HANDLING.md`'s rule that no-cache-plus-failure should surface `UiState.Error(canRetry = true)`. Fixed by introducing `_lastRefreshError: MutableStateFlow<AppError?>` and a three-branch `when` (`Success` / `Error` / `Loading`); `onRefresh` also extended to retry from Error state.

---

## Claude CLI: How to Work on This PR

1. **Read this entire spec + the 5 reference docs before writing any code.**

2. **Execute in 3 stages.** After each stage:
   - Run the stage's verification commands
   - Report results
   - **Stop** — do not proceed to the next stage without user confirmation

3. **Within a stage, autonomous execution is fine** — don't ask "should I proceed?" after each file.

4. **Commit frequently but sensibly.** 2-5 commits per stage, not 1 monolith, not 20 tiny ones.

5. **If a build failure blocks progress for more than 2 attempts:** stop and describe the issue, don't improvise increasingly risky fixes.

6. **Do not:**
   - Add location-related code (PR 05)
   - Add city list / search (PR 04)
   - Add pull-to-refresh, unit toggle (PR 06)
   - Add tests (except trivial ones for `ApiCall` if missing from PR 02)

7. **Version changes:** Per CLAUDE.md rule 8, flag any `libs.versions.toml` upgrade request before applying.

8. **If spec seems wrong:** don't silently fix — flag it. Examples:
   - Missing import in a code snippet
   - Method name mismatch between spec and actual PR 02 output
   - Unclear decision point

## Post-PR Retrospective

- **Total time taken:** [約幾小時]
- **Which stage took longest?:** [Stage 1 / 2 / 3，通常是 Stage 3 整合]
- **Hilt version upgrade:** Hilt 2.55 → 2.59.2 to support AGP 9. Decided based on Claude CLI's investigation of Hilt release notes and Now in Android alignment.
- **Bug discovered mid-Stage 3:** WeatherViewModel's combine chain lacked an Error path when cache was empty AND refresh failed. Symptom: cold-start offline stuck in Loading indefinitely. Fix: added `_lastRefreshError` state and three-way branch (Success / Error / Loading). Spec will be updated to reflect this pattern for future PRs.
- **TD-001 logged:** Data sources are concrete class + internal constructor. Deferred to PR 07.
- **flatMapLatest pattern:** worked first try with the spec's final version.
- **DefaultCity FK handling:** pre-emptive saveCity in ResolveInitialCityUseCase worked as designed, no FK violation observed.