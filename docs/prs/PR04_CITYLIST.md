# PR 04 — City List and Search: Detailed Spec

> Companion to `docs/DEVELOPMENT_PLAN.md` § PR 04.
> **Execution mode: 3-stage with checkpoints between stages.**
>
> - Stage 1: Domain + Data extensions (search UseCases, CityRepository.search)
> - Stage 2: `:feature:citylist` (UI, ViewModel, UiState)
> - Stage 3: `:app` integration + `:feature:weather` minor edit + emulator validation
>
> After each stage, run the listed verification commands and stop for user approval.

## Goal Recap

Let users **search for and switch between cities**. Selecting a city updates DataStore's `selectedCityId`, which (via PR 03's `ObserveSelectedCityWeatherUseCase`) triggers a re-observe of weather for the new city.

**End state after this PR:**
- Tapping the city name in `WeatherScreen` opens `CityListScreen`.
- Typing in the search bar debounces 300 ms then queries Open-Meteo geocoding API.
- Tapping a search result saves the city, sets it as selected, returns to weather screen — and weather updates to that city.
- Saved cities persist across restarts (Room).
- Selected city persists across restarts (DataStore).
- Deleting a saved city removes it; deleting the currently-selected city falls back to `DefaultCity.TAIPEI`.

**What this PR does NOT include:**
- Location detection (PR 05)
- Drag-to-reorder / grouping / favorites (out of scope)
- Pull-to-refresh, unit toggle (PR 06)

## Prerequisites

- [ ] PR 03 merged to `main`.
- [ ] Local `main` is up to date.
- [ ] `./gradlew clean build` passes on `main`.
- [ ] All three PR 03 emulator scenarios still pass (regression baseline).
- [ ] Branch created: `git checkout -b feat/04-citylist`.
- [ ] `CLAUDE.md` "Current PR" updated to PR 04.
- [ ] Scan `docs/TECH_DEBT.md` for items targeting PR 04 (none currently — TD-001 targets PR 07).

## Reference Documents (Must-Read)

1. **`docs/ARCHITECTURE.md`** — navigation pattern (callback injection)
2. **`docs/MODULE_STRUCTURE.md`** — `:feature:citylist` and `:core:domain` sections
3. **`docs/ERROR_HANDLING.md`** — search-specific error mapping
4. **`docs/CODING_CONVENTIONS.md`** — debounce + flow patterns
5. **PR 03 outputs** — especially `WeatherViewModel`, `ObserveSelectedCityWeatherUseCase`, `WeatherAppNavHost` (you'll edit this)
6. **Open-Meteo Geocoding docs** — https://open-meteo.com/en/docs/geocoding-api

---

# STAGE 1: Domain + Data Extensions

**Scope:** Extend `:core:domain` with search-related UseCases. Extend `:core:data` (`CityRepositoryImpl`) with search method. Add `CityRemoteDataSource` search wiring if PR 02's stub didn't cover it.

**Verification:** `./gradlew :core:domain:build :core:data:build` passes.

**Checkpoint:** Stop and wait for user approval before Stage 2.

## A1. Verify CityRemoteDataSource exists

Check what PR 02 left in `:core:network`:

```bash
cat core/network/src/main/kotlin/com/example/weatherforecast/core/network/datasource/CityRemoteDataSource.kt
```

**Expected:** A class with a `searchCities(query: String): Result<List<City>, AppError>` method using `OpenMeteoGeocodingApi`. If PR 02 left only an empty stub, fill in the implementation now:

```kotlin
class CityRemoteDataSource @Inject internal constructor(
    private val api: OpenMeteoGeocodingApi,
) {
    suspend fun searchCities(query: String): Result<List<City>, AppError> = apiCall {
        api.search(name = query).results.orEmpty().map { it.toDomain() }
    }
}
```

**Note on `@Inject internal constructor`:** preserved from PR 03's TD-001 workaround. Do not change visibility — that refactor is reserved for PR 07.

If `GeocodingResponseDto.results` is non-nullable in PR 02's DTO, drop `.orEmpty()`. Check the actual DTO before writing.

## A2. Update `CityRepository` interface

In `:core:domain/repository/CityRepository.kt`, add:

```kotlin
/**
 * Searches the geocoding API for cities matching the query.
 * Caller is responsible for debouncing.
 * Empty query MUST return Success(emptyList) without hitting the network.
 */
suspend fun searchCities(query: String): Result<List<City>, AppError>
```

Final interface should be:

```kotlin
interface CityRepository {
    fun observeSavedCities(): Flow<List<City>>
    suspend fun getCityById(cityId: String): City?
    suspend fun saveCity(city: City): Result<Unit, AppError>
    suspend fun deleteCity(cityId: String): Result<Unit, AppError>
    suspend fun searchCities(query: String): Result<List<City>, AppError>  // NEW
}
```

## A3. Update `CityRepositoryImpl`

In `:core:data/repository/CityRepositoryImpl.kt`, add:

```kotlin
override suspend fun searchCities(query: String): Result<List<City>, AppError> {
    if (query.isBlank()) return Result.Success(emptyList())
    return remote.searchCities(query)
}
```

You'll also need to inject `CityRemoteDataSource`:

```kotlin
@Singleton
internal class CityRepositoryImpl @Inject constructor(
    private val local: CityLocalDataSource,
    private val remote: CityRemoteDataSource,  // NEW
) : CityRepository {
    // ... existing methods unchanged
}
```

`:core:data/build.gradle.kts` already depends on `:core:network` from PR 03, so no Gradle change.

## A4. Add `:core:domain` UseCases

### `usecase/SearchCitiesUseCase.kt`

```kotlin
package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.model.City
import javax.inject.Inject

class SearchCitiesUseCase @Inject constructor(
    private val cityRepository: CityRepository,
) {
    suspend operator fun invoke(query: String): Result<List<City>, AppError> =
        cityRepository.searchCities(query)
}
```

### `usecase/SelectCityUseCase.kt`

```kotlin
package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.common.result.flatMap
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import javax.inject.Inject

/**
 * Saves a city to Room (idempotent) and marks it as the selected city.
 * Composes two repositories — earns its place as a UseCase.
 */
class SelectCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(city: City): Result<Unit, AppError> =
        cityRepository.saveCity(city).flatMap {
            userPreferencesRepository.setSelectedCityId(city.id)
            Result.Success(Unit)
        }
}
```

### `usecase/DeleteCityUseCase.kt`

This is where the **edge case** is handled: when the deleted city is the currently selected one.

```kotlin
package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.common.result.flatMap
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Deletes a city. If the deleted city is currently selected, fall back to
 * DefaultCity.TAIPEI (re-saving it if needed) so selectedCityId never
 * points at a non-existent row.
 */
class DeleteCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(cityId: String): Result<Unit, AppError> {
        val currentSelectedId = userPreferencesRepository.selectedCityId.first()
        return cityRepository.deleteCity(cityId).flatMap {
            if (cityId == currentSelectedId) {
                cityRepository.saveCity(DefaultCity.TAIPEI).flatMap {
                    userPreferencesRepository.setSelectedCityId(DefaultCity.TAIPEI.id)
                    Result.Success(Unit)
                }
            } else {
                Result.Success(Unit)
            }
        }
    }
}
```

## Stage 1 Verification

```bash
./gradlew :core:domain:build :core:data:build
./gradlew build  # full project still compiles

# Boundary check (domain stays clean)
grep -rn "import com.example.weatherforecast.core.network\|import com.example.weatherforecast.core.database\|import com.example.weatherforecast.core.datastore" core/domain/src/
# Should return nothing
```

## Stage 1 Commits

```
feat: add searchCities to CityRepository contract and impl
feat: add SearchCitiesUseCase
feat: add SelectCityUseCase
feat: add DeleteCityUseCase with selected-city fallback
```

Or condensed to 2 commits if preferred. **Don't** mash all four into one.

### STOP HERE

Report Stage 1 results, wait for user to say "proceed to Stage 2".

---

# STAGE 2: Feature:CityList UI Layer

**Scope:** Implement `:feature:citylist` complete.
**Verification:** `./gradlew :feature:citylist:build` passes + boundary checks.
**Checkpoint:** Stop after this stage.

## Module: `:feature:citylist`

### Files to create

```
feature/citylist/src/main/kotlin/com/example/weatherforecast/feature/citylist/
├── navigation/
│   └── CityListNavigation.kt
├── CityListScreen.kt
├── CityListViewModel.kt
├── CityListUiState.kt
└── component/
    ├── CitySearchBar.kt
    ├── SavedCityItem.kt
    ├── SearchResultItem.kt
    └── EmptySavedListHint.kt
```

### Module Gradle config

`feature/citylist/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.weatherforecast.feature.citylist"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
}
```

**Forbidden imports:** `core:network`, `core:database`, `core:datastore`, `core:location`, `core:data`, `feature:weather`.

### File contents

#### `CityListUiState.kt`

The state design needs to express **three concerns** in parallel:
1. The search query and its result state
2. The list of saved cities
3. The currently-selected city id (so UI can highlight it)

```kotlin
package com.example.weatherforecast.feature.citylist

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.model.City

data class CityListUiState(
    val query: String = "",
    val searchState: SearchState = SearchState.Idle,
    val savedCities: List<City> = emptyList(),
    val selectedCityId: String? = null,
)

sealed interface SearchState {
    /** No active query (empty input). */
    data object Idle : SearchState

    /** Query non-empty, request in flight. */
    data object Loading : SearchState

    /** Query returned results (possibly empty list = "no matches"). */
    data class Results(val cities: List<City>) : SearchState

    /** Query failed (network etc.). */
    data class Error(val error: AppError) : SearchState
}
```

**Why `data class` instead of sealed interface for the top level**:
- Search state and saved list are **independent** — they coexist on the same screen
- Saved list is always shown (even while search is loading)
- Selected city highlight is orthogonal to both
- A sealed interface forcing one-of would lose this concurrency

#### `CityListViewModel.kt`

The most complex ViewModel in the project so far. Three Flow inputs + a search debounce.

```kotlin
package com.example.weatherforecast.feature.citylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherforecast.core.common.result.onFailure
import com.example.weatherforecast.core.common.result.onSuccess
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.usecase.DeleteCityUseCase
import com.example.weatherforecast.core.domain.usecase.SearchCitiesUseCase
import com.example.weatherforecast.core.domain.usecase.SelectCityUseCase
import com.example.weatherforecast.core.model.City
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CityListViewModel @Inject constructor(
    cityRepository: CityRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val searchCities: SearchCitiesUseCase,
    private val selectCity: SelectCityUseCase,
    private val deleteCity: DeleteCityUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    private val searchStateFlow = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            flow {
                if (q.isBlank()) {
                    emit(SearchState.Idle)
                    return@flow
                }
                emit(SearchState.Loading)
                searchCities(q)
                    .onSuccess { results -> emit(SearchState.Results(results)) }
                    .onFailure { err -> emit(SearchState.Error(err)) }
            }
        }

    val uiState: StateFlow<CityListUiState> = combine(
        _query,
        searchStateFlow,
        cityRepository.observeSavedCities(),
        userPreferencesRepository.selectedCityId,
    ) { query, searchState, saved, selectedId ->
        CityListUiState(
            query = query,
            searchState = searchState,
            savedCities = saved,
            selectedCityId = selectedId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CityListUiState(),
    )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun onCityTapped(city: City, onDone: () -> Unit) {
        viewModelScope.launch {
            selectCity(city)
            onDone()
        }
    }

    fun onDeleteCity(cityId: String) {
        viewModelScope.launch {
            deleteCity(cityId)
        }
    }
}
```

**Critical detail — the empty-query path:**

The `flatMapLatest { q -> flow { ... } }` block emits `SearchState.Idle` and returns early when `q.isBlank()`. Without this guard, the network would be hit with empty queries (waste) and `Idle` state would never re-appear after clearing the field.

**Critical detail — debounce placement:**

`debounce` is on `_query` upstream of `flatMapLatest`. This way, fast keystrokes are coalesced *before* the search runs. If you put debounce inside the inner flow, every keystroke would still trigger a flatMap-cancel-recreate cycle.

#### `CityListScreen.kt`

```kotlin
package com.example.weatherforecast.feature.citylist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherforecast.core.designsystem.component.ErrorState
import com.example.weatherforecast.feature.citylist.component.CitySearchBar
import com.example.weatherforecast.feature.citylist.component.EmptySavedListHint
import com.example.weatherforecast.feature.citylist.component.SavedCityItem
import com.example.weatherforecast.feature.citylist.component.SearchResultItem

@Composable
fun CityListScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CityListViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CityListContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onCityTapped = { city -> viewModel.onCityTapped(city, onNavigateBack) },
        onDeleteCity = viewModel::onDeleteCity,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityListContent(
    uiState: CityListUiState,
    onQueryChanged: (String) -> Unit,
    onCityTapped: (com.example.weatherforecast.core.model.City) -> Unit,
    onDeleteCity: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Cities") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CitySearchBar(
                query = uiState.query,
                onQueryChanged = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            when (val s = uiState.searchState) {
                SearchState.Idle -> SavedCitiesList(
                    cities = uiState.savedCities,
                    selectedCityId = uiState.selectedCityId,
                    onTap = onCityTapped,
                    onDelete = onDeleteCity,
                )
                SearchState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is SearchState.Results -> SearchResultsList(
                    results = s.cities,
                    onTap = onCityTapped,
                )
                is SearchState.Error -> ErrorState(
                    message = "Search failed. Try again.",
                    onRetry = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SavedCitiesList(
    cities: List<com.example.weatherforecast.core.model.City>,
    selectedCityId: String?,
    onTap: (com.example.weatherforecast.core.model.City) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (cities.isEmpty()) {
        EmptySavedListHint(modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(cities, key = { it.id }) { city ->
            SavedCityItem(
                city = city,
                isSelected = city.id == selectedCityId,
                onTap = { onTap(city) },
                onDelete = { onDelete(city.id) },
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<com.example.weatherforecast.core.model.City>,
    onTap: (com.example.weatherforecast.core.model.City) -> Unit,
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No matches")
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { "search-${it.id}" }) { city ->
            SearchResultItem(
                city = city,
                onTap = { onTap(city) },
            )
        }
    }
}
```

#### `navigation/CityListNavigation.kt`

```kotlin
package com.example.weatherforecast.feature.citylist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.weatherforecast.feature.citylist.CityListScreen
import kotlinx.serialization.Serializable

@Serializable
data object CityListRoute

fun NavGraphBuilder.cityListScreen(
    onNavigateBack: () -> Unit,
) {
    composable<CityListRoute> {
        CityListScreen(onNavigateBack = onNavigateBack)
    }
}
```

#### `component/CitySearchBar.kt`

```kotlin
package com.example.weatherforecast.feature.citylist.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CitySearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier,
        placeholder = { Text("Search cities") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
    )
}
```

#### `component/SavedCityItem.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedCityItem(
    city: City,
    isSelected: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable { onTap() },
        headlineContent = { Text(city.name) },
        supportingContent = { Text(city.country) },
        leadingContent = {
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected")
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        },
    )
}
```

Imports to ensure:
- `androidx.compose.foundation.clickable`
- `androidx.compose.material.icons.filled.Check`
- `androidx.compose.material.icons.filled.Delete`
- `com.example.weatherforecast.core.model.City`

#### `component/SearchResultItem.kt`

```kotlin
@Composable
internal fun SearchResultItem(
    city: City,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable { onTap() },
        headlineContent = { Text(city.name) },
        supportingContent = { Text(city.country) },
    )
}
```

#### `component/EmptySavedListHint.kt`

```kotlin
@Composable
internal fun EmptySavedListHint(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.LocationCity,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Search above to add a city",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
```

## Stage 2 Verification

```bash
./gradlew :feature:citylist:build

# Boundary checks
grep -rn "import com.example.weatherforecast.core.network\|import com.example.weatherforecast.core.database\|import com.example.weatherforecast.core.datastore\|import com.example.weatherforecast.core.data\|import com.example.weatherforecast.feature.weather" feature/citylist/src/
# Should return nothing
```

## Stage 2 Commits

```
feat: add CityListUiState and SearchState model
feat: add CityListViewModel with debounced search
feat: add CityListScreen and components
feat: add CityListNavigation route
```

### STOP HERE

Report results, wait for user to say "proceed to Stage 3".

---

# STAGE 3: App Integration + Weather Screen Edit + Emulator Validation

**Scope:**
- `:app` registers `cityListScreen` in NavHost, wires callbacks
- `:feature:weather` makes city name in TopAppBar tappable to navigate
- `:app/build.gradle.kts` adds `:feature:citylist` dependency
- Manual emulator validation

## A. `:app` changes

### `app/build.gradle.kts`

Add to dependencies (next to existing `:feature:weather`):

```kotlin
implementation(projects.feature.citylist)
```

### `app/src/main/kotlin/com/example/weatherforecast/navigation/WeatherAppNavHost.kt`

Update from PR 03's version:

```kotlin
package com.example.weatherforecast.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.weatherforecast.feature.citylist.navigation.CityListRoute
import com.example.weatherforecast.feature.citylist.navigation.cityListScreen
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
                navController.navigate(CityListRoute)
            },
        )
        cityListScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
        )
    }
}
```

## B. `:feature:weather` changes

### `WeatherScreen.kt`

The PR 03 version has a `TextButton(onClick = onNavigateToCityList) { Text("Cities") }` in the TopAppBar. Keep that, but **also** make the title tappable. Two equivalent affordances increase discoverability without breaking anything.

In `WeatherContent`'s `topBar` Scaffold parameter:

```kotlin
topBar = {
    TopAppBar(
        title = {
            when (uiState) {
                is WeatherUiState.Success -> Text(
                    text = uiState.city.name,
                    modifier = Modifier.clickable { onNavigateToCityList() },
                )
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
```

Add import: `androidx.compose.foundation.clickable`.

**No other changes to `:feature:weather`.** The `ObserveSelectedCityWeatherUseCase` chain from PR 03 already responds to `selectedCityId` changes via `flatMapLatest` — picking a city in `CityListScreen` calls `userPreferencesRepository.setSelectedCityId(...)`, which makes the weather Flow re-emit for the new city. **No manual refetch needed.** This is the SSOT design paying off.

## Stage 3 Verification

### Build + install

```bash
./gradlew clean build
./gradlew :app:installDebug
```

### Emulator scenarios

Run **all** of these on the emulator. Document any failures.

**1. Navigation round-trip (online)**
- Launch with internet on, fresh install
- Wait for Taipei weather to load
- Tap "Cities" button (or city name in title) → CityList opens
- Tap back arrow → return to WeatherScreen, still showing Taipei
- ✅ No crash, no flicker, weather state preserved

**2. Search and add a city**
- From WeatherScreen, navigate to CityList
- Type "Tokyo" (slowly enough to see debounce)
- Wait ~300ms → results appear
- Tap "Tokyo, Japan" → navigates back automatically
- WeatherScreen now shows Tokyo's weather
- TopAppBar title is "Tokyo"
- ✅ Weather updates without manual refresh

**3. Saved cities persist across launches**
- After step 2, force-stop the app (Recents → swipe away)
- Relaunch
- WeatherScreen shows Tokyo (selected city persisted via DataStore)
- Open CityList → both Taipei and Tokyo appear in saved list
- Tokyo has the "selected" check mark
- ✅ Persistence works

**4. Switch between saved cities**
- In CityList saved list, tap Taipei
- Returns to WeatherScreen, weather updates to Taipei
- Open CityList again → Taipei now has the check mark, Tokyo doesn't
- ✅ Selection switches correctly

**5. Delete a non-selected city**
- Selected city is Taipei. CityList shows Taipei (selected) and Tokyo
- Tap delete on Tokyo
- Tokyo disappears from list
- Selected stays Taipei
- ✅ No crash, no weather change

**6. Delete the currently selected city — the edge case**
- Currently selected: Taipei. Saved list: Taipei, Tokyo (re-add Tokyo first)
- Select Tokyo (so it becomes selected)
- Delete Tokyo from saved list
- Expected: Tokyo disappears, selection falls back to Taipei (DefaultCity), WeatherScreen now shows Taipei weather
- ✅ No crash, no stuck state, weather reflects fallback

**7. Empty search results**
- Type a nonsense query ("zzzqqqxxx") → "No matches" should display
- ✅ No crash

**8. Clear search field**
- Type any query, see results
- Tap the X icon to clear
- Search state returns to Idle, saved list re-appears
- ✅ Field clears, list comes back

If any scenario fails, stop and report — don't try to fix incrementally without user input.

## Stage 3 Commits

```
feat: register cityListScreen in WeatherAppNavHost
feat: make WeatherScreen title tappable for city list navigation
```

---

## Final PR Verification

```bash
./gradlew clean build
./gradlew :app:installDebug
```

Module boundary final sweep:

```bash
grep -rn "import com.example.weatherforecast.feature.weather" feature/citylist/src/
grep -rn "import com.example.weatherforecast.feature.citylist" feature/weather/src/
# Both should return nothing — features are siblings, never depend on each other
```

## Expected total commits

8–11 across the 3 stages. Don't squash; commits-per-stage is fine if they read clearly.

---

## Post-PR Retrospective (fill after merge)

- Total time taken:
- Which stage took longest?
- Search debounce — did the `debounce` + `flatMapLatest` pattern work first try?
- Edge case "delete selected city" — did it surface a bug, or did `DeleteCityUseCase` handle it cleanly?
- Anything to update in `docs/CODING_CONVENTIONS.md` based on what we learned?

---

## Claude CLI: How to Work on This PR

1. **Read this entire spec + the 6 reference docs before writing any code.** Especially re-read PR 03's `WeatherViewModel` and `ObserveSelectedCityWeatherUseCase` — your work here plugs into them.

2. **Execute in 3 stages.** After each stage:
   - Run the listed verification commands
   - Report results
   - **Stop** — do not proceed without user confirmation

3. **Within a stage, autonomous execution is fine.** Don't ask "should I proceed?" after each file.

4. **Commit frequently but sensibly.** 2–4 commits per stage.

5. **If a build failure blocks progress for more than 2 attempts:** stop and describe the issue.

6. **Do not:**
   - Add location-related code (PR 05)
   - Add pull-to-refresh / unit toggle (PR 06)
   - Touch TD-001 data source visibility (PR 07)

7. **Version changes:** Per CLAUDE.md rule 8, flag any `libs.versions.toml` upgrade request before applying.

8. **Edge cases to handle (already in spec, but flag if you find more):**
   - Empty / blank search query → no network call
   - Deleting the currently selected city → fall back to `DefaultCity.TAIPEI`
   - Search debounce should be **on the input flow**, not inside the search call
   - Saved cities and search results coexist — they're not mutually exclusive states

9. **Geocoding API rate limit:** Open-Meteo's geocoding endpoint has fair-use limits. The 300 ms debounce protects against typing-bursts. Don't add aggressive retry-on-failure; one attempt per query is enough.

10. **If the spec seems wrong or contradicts something in another doc:** flag it — don't silently fix.
