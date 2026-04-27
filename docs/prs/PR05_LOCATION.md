# PR 05 — Location Integration: Detailed Spec

> Companion to `docs/DEVELOPMENT_PLAN.md` § PR 05.
> **Execution mode: 3-stage with checkpoints between stages.**
>
> - Stage 1: `:core:location` + domain/data extensions (no UI)
> - Stage 2: `:feature:weather` permission flow + banner + ResolveInitialCity update
> - Stage 3: `:app` integration + emulator validation
>
> After each stage, run the listed verification commands and stop for user approval.

## Goal Recap

Detect the user's current city automatically (with permission), with graceful fallback when permission is denied, GPS is off, or the location service times out.

**End state after this PR:**
- Fresh install + no permission → shows Taipei (default) + banner prompting permission grant
- User grants permission → next launch detects current city + shows that city's weather
- User denies permission → banner stays, app works normally with default/last-selected city
- GPS disabled at system level → distinct banner ("Enable Location"), can navigate to Settings
- Location detection times out (5s) → silently fall back to last-selected/default
- Once detected, current-location city is persisted in Room with `isCurrentLocation = true` and reused (not duplicated) on subsequent detections at the same place

**What this PR does NOT include:**
- Background location (never)
- Fine-grained location (COARSE is enough for weather)
- Continuous location updates while app is open (single-fetch-per-session)
- Pull-to-refresh (PR 06)
- Unit toggle (PR 06)
- Tests (PR 07 — but write them now if quick to do, especially for `ResolveInitialCityUseCase` branches)

## Prerequisites

- [ ] PR 04 merged to `main`
- [ ] Local `main` is up to date
- [ ] `./gradlew clean build` passes
- [ ] All 8 PR 04 emulator scenarios still pass (regression baseline)
- [ ] Branch created: `git checkout -b feat/05-location`
- [ ] `CLAUDE.md` "Current PR" updated to PR 05
- [ ] Scan `docs/TECH_DEBT.md`:
  - **TD-002 (`getCityById` overhead)** is targeted at "PR 05 / 06 re-evaluate" — note whether PR 05 changes the calling pattern. (It shouldn't, but verify.)

## Reference Documents (Must-Read)

1. **`docs/ARCHITECTURE.md`** — feature module dependency rules (`:feature:weather` cannot import `:core:location`)
2. **`docs/MODULE_STRUCTURE.md`** — `:core:location` package layout
3. **`docs/ERROR_HANDLING.md`** — `AppError.LocationDisabled`, `AppError.LocationPermissionDenied`, `AppError.LocationTimeout`, `AppError.GeocoderFailed` (these were defined in PR 02 but not yet used)
4. **`docs/CODING_CONVENTIONS.md`** — DI patterns, dispatcher injection
5. **PR 03/PR 04 outputs** — especially `ResolveInitialCityUseCase`, `ObserveSelectedCityUseCase`, `WeatherViewModel`
6. **Android docs**:
   - https://developer.android.com/develop/sensors-and-location/location/retrieve-current
   - https://developer.android.com/jetpack/compose/permissions
   - `Geocoder` deprecation note: https://developer.android.com/reference/android/location/Geocoder

---

# STAGE 1: `:core:location` + Domain/Data Extensions

**Scope:**
- Implement `:core:location` (was empty since PR 01)
- Add `LocationRepository` interface to `:core:domain`
- Add `LocationRepositoryImpl` in `:core:data`
- Update `ResolveInitialCityUseCase` with location branch

**Verification:** `./gradlew :core:location:build :core:domain:build :core:data:build` passes + boundary checks.

**Checkpoint:** Stop for user approval before Stage 2.

## Module A: `:core:location`

### Files to create

```
core/location/src/main/kotlin/com/example/weatherforecast/core/location/
├── provider/
│   └── LocationProvider.kt
├── geocoder/
│   └── GeocoderWrapper.kt
├── datasource/
│   └── LocationDataSource.kt
└── di/
    └── LocationModule.kt
```

### Module Gradle config

`core/location/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
}

android {
    namespace = "com.example.weatherforecast.core.location"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
}
```

Add to `gradle/libs.versions.toml` if not present:

```toml
[versions]
playServicesLocation = "21.3.0"

[libraries]
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "kotlinxCoroutines" }
```

(`kotlinxCoroutines` should already exist in catalog from earlier PRs.)

**Note on Play Services dependency:** This pulls in Google Play Services. The emulator must have Google Play Services installed (most do, but check if a system image without it is being used).

### File contents

#### `provider/LocationProvider.kt`

```kotlin
package com.example.weatherforecast.core.location.provider

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import com.example.weatherforecast.core.common.dispatcher.Dispatcher
import com.example.weatherforecast.core.common.dispatcher.DispatcherQualifier
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.Coordinates
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCATION_TIMEOUT_MS = 5_000L

@Singleton
class LocationProvider @Inject internal constructor(
    @ApplicationContext private val context: Context,
    @DispatcherQualifier(Dispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Returns the device's current coarse location, or an AppError describing why we couldn't.
     *
     * Caller is responsible for verifying ACCESS_COARSE_LOCATION is granted before calling.
     * This method will throw SecurityException if not — caller should map that to LocationPermissionDenied
     * before invoking. We use @SuppressLint here because the runtime permission gate is the caller's job.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<Coordinates, AppError> = withContext(ioDispatcher) {
        if (!isLocationEnabled()) {
            return@withContext Result.Failure(AppError.LocationDisabled)
        }

        val cts = CancellationTokenSource()
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            try {
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token,
                ).await()
            } catch (se: SecurityException) {
                null  // mapped below
            }
        }

        if (location == null) {
            cts.cancel()
            return@withContext Result.Failure(AppError.LocationTimeout)
        }

        Result.Success(Coordinates(location.latitude, location.longitude))
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
```

**Key design decisions explained:**

- **`getCurrentLocation` (not `lastLocation`)**: `lastLocation` returns null on fresh installs. `getCurrentLocation` actively requests a fix (with a power-budget priority). Slightly slower but always returns something within the timeout window.
- **`PRIORITY_BALANCED_POWER_ACCURACY`**: This matches our `ACCESS_COARSE_LOCATION` permission. Don't use `PRIORITY_HIGH_ACCURACY` — that needs FINE permission.
- **5-second timeout**: User experience — waiting longer than 5s to know "we couldn't get your location" is unacceptable. Fall back to default city quickly.
- **`@SuppressLint("MissingPermission")` is correct here**: The class is internal infra; permission is the caller's responsibility (`LocationDataSource` checks before calling).
- **Why `@Inject internal constructor`**: Same TD-001 pattern as PR 02/03 — class public for cross-module injection, constructor internal to preserve encapsulation. Will be refactored in PR 07.

#### `geocoder/GeocoderWrapper.kt`

```kotlin
package com.example.weatherforecast.core.location.geocoder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.weatherforecast.core.common.dispatcher.Dispatcher
import com.example.weatherforecast.core.common.dispatcher.DispatcherQualifier
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.Coordinates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class GeocoderWrapper @Inject internal constructor(
    @ApplicationContext private val context: Context,
    @DispatcherQualifier(Dispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Reverse-geocodes coordinates to a City.
     * Returns LocationResolutionFailed if no address found or geocoder errored.
     */
    suspend fun reverseGeocode(coords: Coordinates): Result<City, AppError> = withContext(ioDispatcher) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                fetchAddressesAsync(geocoder, coords)
            } else {
                fetchAddressesSync(geocoder, coords)
            }
            val first = addresses.firstOrNull()
                ?: return@withContext Result.Failure(AppError.LocationResolutionFailed)
            Result.Success(first.toCity(coords))
        } catch (e: Exception) {
            Result.Failure(AppError.LocationResolutionFailed)
        }
    }

    @SuppressLint("NewApi")  // gated by SDK_INT check
    private suspend fun fetchAddressesAsync(
        geocoder: Geocoder,
        coords: Coordinates,
    ): List<Address> = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(coords.latitude, coords.longitude, 1) { addresses ->
            cont.resume(addresses)
        }
    }

    @Suppress("DEPRECATION")
    private fun fetchAddressesSync(
        geocoder: Geocoder,
        coords: Coordinates,
    ): List<Address> {
        return geocoder.getFromLocation(coords.latitude, coords.longitude, 1) ?: emptyList()
    }
}

private fun Address.toCity(coords: Coordinates): City {
    val cityName = locality ?: subAdminArea ?: adminArea ?: "Unknown"
    val country = countryName ?: ""
    return City(
        id = "current_location",  // stable id for the auto-detected city
        name = cityName,
        country = country,
        latitude = coords.latitude,
        longitude = coords.longitude,
        isCurrentLocation = true,
    )
}
```

**Key design decisions:**

- **Stable `id = "current_location"`**: Crucial for de-duplication. Every auto-detection upserts to this same row, so re-detection in the same place doesn't create dupes. New location replaces the lat/lng/name in place.
- **`locality` fallback chain**: `locality` is the city; if unavailable (rural areas, some countries), fall back to `subAdminArea` (county/township) → `adminArea` (state/province) → "Unknown".
- **API 33+ async form**: Avoids the deprecation warning. Older APIs use the deprecated sync form (suppressed).
- **`Locale.getDefault()`**: Geocoder returns localized names. For Taiwan locale → "台北市"; for English locale → "Taipei". Acceptable trade-off — localizing later is harder than getting the user-expected name now.

#### `datasource/LocationDataSource.kt`

```kotlin
package com.example.weatherforecast.core.location.datasource

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.common.result.flatMap
import com.example.weatherforecast.core.location.geocoder.GeocoderWrapper
import com.example.weatherforecast.core.location.provider.LocationProvider
import com.example.weatherforecast.core.model.City
import javax.inject.Inject

class LocationDataSource @Inject internal constructor(
    private val locationProvider: LocationProvider,
    private val geocoder: GeocoderWrapper,
) {
    /**
     * Composed: get current coordinates → reverse-geocode → return City.
     * Caller must verify permission before calling.
     */
    suspend fun fetchCurrentLocationCity(): Result<City, AppError> =
        locationProvider.getCurrentLocation().flatMap { coords ->
            geocoder.reverseGeocode(coords)
        }
}
```

#### `di/LocationModule.kt`

No `@Provides` needed — Hilt builds the graph from `@Inject` constructors. The DI module file is intentionally empty for now (kept for future module-level provides if needed).

```kotlin
package com.example.weatherforecast.core.location.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object LocationModule {
    // Reserved for future @Provides bindings.
    // Currently all dependencies are wired via @Inject constructors.
}
```

If KSP complains about an empty `@Module` with no provides, delete the file and let Hilt's automatic discovery handle everything (this is the most likely outcome). Don't keep an empty stub if it causes warnings.

## Module B: `:core:domain` extension

### File: `repository/LocationRepository.kt` (new)

```kotlin
package com.example.weatherforecast.core.domain.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City

interface LocationRepository {
    /**
     * Returns the device's current location as a City (with isCurrentLocation = true).
     * Caller must verify permission before calling — this method does not prompt the user.
     */
    suspend fun getCurrentLocationCity(): Result<City, AppError>
}
```

### File: `usecase/ResolveInitialCityUseCase.kt` (modify)

Replace PR 03's two-branch fallback with a three-branch one. **The order matters: location → last-selected → default.**

```kotlin
package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.common.result.getOrNull
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.LocationRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves which city to show on app launch.
 *
 * Fallback chain (PR 05):
 *   1. If `useLocation` AND we have permission AND location succeeds → save and return that city
 *   2. Last-selected city (DataStore → Room) → return
 *   3. DefaultCity.TAIPEI (persisted on first call so FK-dependent writes succeed)
 *
 * @param useLocation if false, skip the location branch entirely. Caller passes false when
 *                    permission is not granted, so we don't waste a SecurityException trip.
 */
class ResolveInitialCityUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val cityRepository: CityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(useLocation: Boolean): City {
        if (useLocation) {
            val locationCity = locationRepository.getCurrentLocationCity().getOrNull()
            if (locationCity != null) {
                cityRepository.saveCity(locationCity)
                userPreferencesRepository.setSelectedCityId(locationCity.id)
                return locationCity
            }
            // Location failed — fall through to next branch silently.
            // The banner UX (handled in WeatherViewModel) will surface the issue.
        }

        val lastCityId = userPreferencesRepository.selectedCityId.first()
        if (lastCityId != null) {
            cityRepository.getCityById(lastCityId)?.let { return it }
        }

        cityRepository.saveCity(DefaultCity.TAIPEI)
        userPreferencesRepository.setSelectedCityId(DefaultCity.TAIPEI.id)
        return DefaultCity.TAIPEI
    }
}
```

**Critical:** This UseCase now takes a parameter (`useLocation: Boolean`). PR 03's `WeatherViewModel.init` calls `resolveInitialCity()` — it must change to `resolveInitialCity(useLocation = ...)`. We'll do that in Stage 2.

**Why a parameter, not "auto-detect permission inside the UseCase"**: Permission state is a UI concern. The UseCase's job is to compose data sources, not to interrogate Android permission APIs. The caller (ViewModel) checks permission via Compose's `rememberPermissionState`, then passes the boolean.

## Module C: `:core:data` extension

### File: `repository/LocationRepositoryImpl.kt` (new)

```kotlin
package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.LocationRepository
import com.example.weatherforecast.core.location.datasource.LocationDataSource
import com.example.weatherforecast.core.model.City
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LocationRepositoryImpl @Inject constructor(
    private val locationDataSource: LocationDataSource,
) : LocationRepository {
    override suspend fun getCurrentLocationCity(): Result<City, AppError> =
        locationDataSource.fetchCurrentLocationCity()
}
```

### File: `di/RepositoryModule.kt` (modify)

Add the new binding:

```kotlin
@Binds
@Singleton
abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository
```

### File: `core/data/build.gradle.kts` (modify)

Add `:core:location` dependency:

```kotlin
dependencies {
    api(projects.core.domain)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.location)  // NEW
    implementation(projects.core.model)
    implementation(projects.core.common)
}
```

## Stage 1 Verification

```bash
./gradlew :core:location:build :core:domain:build :core:data:build
./gradlew build  # full project compiles

# Boundary check — domain still doesn't import data sources
grep -rn "import com.example.weatherforecast.core.location\|import com.example.weatherforecast.core.network\|import com.example.weatherforecast.core.database\|import com.example.weatherforecast.core.datastore" core/domain/src/
# Should return nothing — domain only imports model + common

# Boundary check — :core:location doesn't import database/network/data
grep -rn "import com.example.weatherforecast.core.network\|import com.example.weatherforecast.core.database\|import com.example.weatherforecast.core.data" core/location/src/
# Should return nothing
```

**Expected breakages:**

`WeatherViewModel` will NOT compile after this stage because `resolveInitialCity()` now requires a parameter. **This is intentional** — Stage 2 will fix it. If Claude CLI is uneasy about leaving the project briefly broken between stages, it can pass a temporary `useLocation = false` to keep the build green, then update properly in Stage 2.

**Recommendation: keep build green.** Pass `useLocation = false` in `WeatherViewModel.init` as a stopgap commit, mark it with a TODO referencing Stage 2. Cleaner CI and rebase story.

## Stage 1 Commits

```
feat: add :core:location with LocationProvider and GeocoderWrapper
feat: add LocationRepository interface
feat: add LocationRepositoryImpl and Hilt binding
feat: extend ResolveInitialCityUseCase with location branch
chore: keep WeatherViewModel building with useLocation=false stopgap
```

### STOP HERE

Report Stage 1 results, wait for user approval before Stage 2.

---

# STAGE 2: Permission Flow + Banner UI

**Scope:**
- `:feature:weather` updates: `WeatherViewModel` integrates permission state + location enable state, banner UI in `WeatherScreen`
- New `:feature:weather` components: `LocationPermissionBanner`, `LocationDisabledBanner`
- `:app/AndroidManifest.xml`: add `ACCESS_COARSE_LOCATION` permission
- `gradle/libs.versions.toml`: add `accompanist-permissions` if going that route, OR rely on `androidx.activity` for Compose-native permission

**Verification:** `./gradlew :feature:weather:build` passes + boundary checks.

**Checkpoint:** Stop after this stage.

## A. AndroidManifest

`app/src/main/AndroidManifest.xml`:

```xml
<manifest ...>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />  <!-- NEW -->

    <application ...>
        ...
    </application>
</manifest>
```

**No FINE location.** COARSE is enough for city-level weather and reduces user friction (fewer permissions = higher grant rate).

## B. Permission API choice

Two options:

### Option B1: Accompanist Permissions (recommended)

Mature, simple API.

```toml
# libs.versions.toml
[versions]
accompanist = "0.36.0"

[libraries]
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist" }
```

Add to `feature/weather/build.gradle.kts`:

```kotlin
implementation(libs.accompanist.permissions)
```

### Option B2: Compose-native via `androidx.activity:activity-compose`

`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` — works but has more boilerplate for permission state observation.

**Decision: Option B1 (Accompanist).** Less boilerplate, well-tested with Compose, single dependency. If team prefers minimum dependencies, B2 is fine — they're equivalent.

## C. `:feature:weather` updates

### File: `LocationPromptState.kt` (new)

A small state model for the banner.

```kotlin
package com.example.weatherforecast.feature.weather

sealed interface LocationPromptState {
    data object Hidden : LocationPromptState
    data object NeedsPermission : LocationPromptState  // user hasn't granted
    data object LocationDisabled : LocationPromptState  // permission granted but GPS off
}
```

### File: `WeatherViewModel.kt` (modify)

Add permission and location-enabled signals as inputs to drive the banner state and the initial-city resolution.

```kotlin
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val resolveInitialCity: ResolveInitialCityUseCase,
    private val observeSelectedCity: ObserveSelectedCityUseCase,
    private val observeSelectedCityWeather: ObserveSelectedCityWeatherUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
) : ViewModel() {

    private val _lastRefreshError = MutableStateFlow<AppError?>(null)
    private val _hasLocationPermission = MutableStateFlow(false)
    private val _isLocationEnabled = MutableStateFlow(true)  // best-effort assumption

    /** Banner state derived from permission and location-enabled signals. */
    val locationPromptState: StateFlow<LocationPromptState> = combine(
        _hasLocationPermission,
        _isLocationEnabled,
    ) { hasPerm, locOn ->
        when {
            !hasPerm -> LocationPromptState.NeedsPermission
            !locOn -> LocationPromptState.LocationDisabled
            else -> LocationPromptState.Hidden
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocationPromptState.NeedsPermission,
    )

    val uiState: StateFlow<WeatherUiState> = combine(
        observeSelectedCityWeather(),
        _lastRefreshError,
    ) { (city, weather), lastError ->
        // Same three-way branch as PR 03 fix
        when {
            weather != null -> WeatherUiState.Success(
                weather = weather,
                city = city,
                isStale = lastError != null,
                transientMessage = lastError?.toUserMessage(),
            )
            lastError != null -> WeatherUiState.Error(
                error = lastError,
                canRetry = true,
            )
            else -> WeatherUiState.Loading
        }
    }
        .catch { emit(WeatherUiState.Error(AppError.Unexpected(it), canRetry = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherUiState.Loading,
        )

    init {
        viewModelScope.launch {
            // Bootstrap: on first run, decide whether to use location.
            // _hasLocationPermission has a starting value of `false` — caller must update before init runs?
            // In practice, the Composable updates _hasLocationPermission *during* its first composition,
            // before the user can interact. To handle the edge case where permission was previously
            // granted, we read the current value at init time.
            resolveInitialCity(useLocation = _hasLocationPermission.value)

            // Then continue auto-refresh on every selected city change
            observeSelectedCity()
                .collect { city ->
                    refreshWeather(city).onFailure { handleRefreshFailure(it) }
                }
        }
    }

    fun onLocationPermissionChanged(granted: Boolean, locationEnabled: Boolean = true) {
        _hasLocationPermission.value = granted
        _isLocationEnabled.value = locationEnabled
        // If permission was just granted, attempt to resolve to the current location now
        // (otherwise user has to restart the app for it to kick in).
        if (granted) {
            viewModelScope.launch {
                val city = resolveInitialCity(useLocation = true)
                // resolveInitialCity already calls setSelectedCityId, which will trigger
                // the observeSelectedCity collect → refreshWeather chain.
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            val currentCity = (uiState.value as? WeatherUiState.Success)?.city ?: return@launch
            refreshWeather(currentCity).onFailure { handleRefreshFailure(it) }
        }
    }

    private fun handleRefreshFailure(error: AppError) {
        _lastRefreshError.value = error
    }

    // ... existing toUserMessage etc.
}
```

**Critical detail — the permission re-resolve flow:**

When the user taps the banner and grants permission, we want the app to immediately switch to current-location city without restart. The handler `onLocationPermissionChanged(granted = true, ...)` re-runs `resolveInitialCityUseCase(useLocation = true)` which:
1. Fetches location, geocodes it
2. Saves the City with `id = "current_location"`
3. Sets `selectedCityId = "current_location"`

Step 3 emits to the `observeSelectedCity()` flow, which the init's `collect` block handles → `refreshWeather` runs → UI updates to show the new city.

**No need to manually trigger weather refresh here** — the existing observe-and-refresh chain handles it. SSOT design pays off.

**Critical detail — initial state of `_hasLocationPermission`:**

Set to `false` initially. The Composable's first composition will read the actual permission state via `rememberPermissionState` and call `onLocationPermissionChanged(...)` to sync. There's a tiny window where init runs before this sync, but we handle it: if perm was previously granted, the sync triggers re-resolve almost immediately.

**Alternative considered:** check permission state inside the ViewModel using `ContextCompat.checkSelfPermission`. Rejected — keeps platform calls out of ViewModel and pushes responsibility to the Composable layer where Compose's permission API lives.

### File: `component/LocationPermissionBanner.kt` (new)

```kotlin
package com.example.weatherforecast.feature.weather.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LocationPermissionBanner(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onTap() },
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Enable location for auto-detect",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
```

### File: `component/LocationDisabledBanner.kt` (new)

```kotlin
package com.example.weatherforecast.feature.weather.component

@Composable
internal fun LocationDisabledBanner(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenSettings() },
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOff,  // import from material-icons-extended
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Location is off — tap to enable in Settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
```

### File: `WeatherScreen.kt` (modify)

Add permission state + banner above the weather content.

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WeatherScreen(
    onNavigateToCityList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeatherViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locationPromptState by viewModel.locationPromptState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val permissionState = rememberPermissionState(
        permission = android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ) { granted ->
        viewModel.onLocationPermissionChanged(
            granted = granted,
            locationEnabled = isLocationEnabled(context),
        )
    }

    // Initial sync: when the screen first composes, push the current permission state to the VM
    // so it can decide whether to attempt location-based resolution.
    LaunchedEffect(permissionState.status) {
        viewModel.onLocationPermissionChanged(
            granted = permissionState.status.isGranted,
            locationEnabled = isLocationEnabled(context),
        )
    }

    WeatherContent(
        uiState = uiState,
        locationPromptState = locationPromptState,
        onRequestPermission = { permissionState.launchPermissionRequest() },
        onOpenLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        },
        onRetry = viewModel::onRefresh,
        onNavigateToCityList = onNavigateToCityList,
        modifier = modifier,
    )
}

private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
```

Add the necessary imports: `android.content.Context`, `android.content.Intent`, `android.location.LocationManager`, `android.provider.Settings`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.ui.platform.LocalContext`, `com.google.accompanist.permissions.ExperimentalPermissionsApi`, `com.google.accompanist.permissions.isGranted`, `com.google.accompanist.permissions.rememberPermissionState`, etc.

In `WeatherContent`, render the banner above the existing scaffold body:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherContent(
    uiState: WeatherUiState,
    locationPromptState: LocationPromptState,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToCityList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { /* same as PR04 */ },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Banner ABOVE main content, not inside it
            when (locationPromptState) {
                LocationPromptState.NeedsPermission -> LocationPermissionBanner(
                    onTap = onRequestPermission,
                )
                LocationPromptState.LocationDisabled -> LocationDisabledBanner(
                    onOpenSettings = onOpenLocationSettings,
                )
                LocationPromptState.Hidden -> Unit
            }
            // Main content
            Box(modifier = Modifier.fillMaxSize()) {
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
}
```

## Stage 2 Verification

```bash
./gradlew :feature:weather:build

# Boundary check — feature:weather still doesn't import core:location, core:network, etc.
grep -rn "import com.example.weatherforecast.core.network\|import com.example.weatherforecast.core.database\|import com.example.weatherforecast.core.datastore\|import com.example.weatherforecast.core.location\|import com.example.weatherforecast.core.data" feature/weather/src/
# Should return nothing — feature only knows about :core:domain via interfaces
```

## Stage 2 Commits

```
chore: declare ACCESS_COARSE_LOCATION permission in :app
build: add accompanist-permissions dependency
feat: add LocationPromptState and banner components
feat: integrate location permission flow in WeatherViewModel
feat: render location banners above weather content
```

### STOP HERE

Report Stage 2 results, wait for user approval before Stage 3.

---

# STAGE 3: App Integration + Emulator Validation

**Scope:**
- `:app/build.gradle.kts` — verify all dependencies present (likely no change needed; PR 03/04 already wired feature:weather)
- Manual emulator validation — 6 scenarios

## A. App-level changes

Most likely **no changes needed**. Confirm with:

```bash
git diff main..HEAD -- app/
```

If `:app/build.gradle.kts` requires updating (e.g., adding direct location dependency), it should not — `:feature:weather` and `:core:data` already pull location transitively.

`AndroidManifest.xml` was already updated in Stage 2.

## B. Emulator scenarios

Use Android emulator with:
- API 33+ recommended (test the new Geocoder async API)
- Google Play Services included (most images do)

**Setting emulator location**: Extended controls (... menu) → Location → Single Points → enter coordinates and "Send Location". Use Taipei (25.0330, 121.5654) for control, switch to e.g. Tokyo (35.6762, 139.6503) to test re-detection.

### Scenario 1: Fresh install, no permission

- Uninstall app, ensure no permission cached
- Launch app
- ✅ "Enable location for auto-detect" banner visible above weather
- ✅ Weather shows Taipei (DefaultCity fallback)
- ✅ TopAppBar title = "Taipei"
- ✅ No crash

### Scenario 2: Grant permission via banner

- From Scenario 1 state, tap the banner
- System permission dialog appears
- Tap "Allow"
- ✅ Banner disappears
- ✅ Within ~2-5 seconds (location fetch + geocode), TopAppBar title changes to current location's city name (whatever the emulator is set to)
- ✅ Weather body updates to that city's weather
- ✅ No crash

### Scenario 3: Permission persists across launches

- After Scenario 2, force-stop the app
- Relaunch
- ✅ Banner does NOT appear (permission already granted)
- ✅ Within ~2-5 seconds, current-location city's weather appears
- ✅ Same city as Scenario 2 (assuming emulator location unchanged)

### Scenario 4: Deny permission

- Uninstall app to reset permission
- Launch, tap banner, system dialog → "Don't allow"
- ✅ Banner stays visible (permission still denied)
- ✅ Weather body shows Taipei (default)
- ✅ App does not crash, does not get stuck

### Scenario 5: Location services disabled at system level

- Permission granted (from Scenario 2)
- Open emulator system Settings → turn off Location
- Return to app, force-stop, relaunch
- ✅ Banner shows "Location is off — tap to enable in Settings"
- ✅ Weather body shows previously-detected city (cached) or fallback
- Tap the banner → system Location settings opens
- ✅ No crash

### Scenario 6: Location moves (re-detection works)

- Permission granted, original location set to Taipei
- Launch app → shows Taipei weather (current-location city)
- Don't kill the app. Change emulator location to Tokyo
- Force-stop app, relaunch
- ✅ Within ~2-5 seconds, app shows Tokyo weather
- ✅ In CityList: only ONE row labeled "current location" — Taipei row was overwritten, not duplicated (because both used `id = "current_location"`)

If Scenario 6 fails (creates duplicates), check that `LocationDataSource.fetchCurrentLocationCity` returns a `City` with `id = "current_location"` (the stable id from `Address.toCity`).

## Stage 3 Commits

Likely just one commit if anything:

```
chore: verify app integration of location feature
```

If no app changes needed, no commit — just verification.

---

## Final PR Verification

```bash
./gradlew clean build
./gradlew :app:installDebug

# Final boundary sweep
grep -rn "import com.example.weatherforecast.core.location" feature/
# Should return nothing — features never import core:location directly
```

## Expected total commits

10–14 across the 3 stages, plus 1–2 docs commits at the end (CLAUDE.md update, retrospective).

## Post-PR Retrospective (fill after merge)

- Total time taken:
- Permission flow — anything surprising about Accompanist or the Compose permission API?
- Did the `id = "current_location"` de-duplication work, or did re-detections create dupes?
- Geocoder reliability on emulator vs real device?
- Any TECH_DEBT updates? (e.g., `LocationProvider` `@Inject internal constructor` — consolidate with TD-001 or extend it?)
- Anything to update in `docs/MODULE_STRUCTURE.md` based on what we learned?

---

## Claude CLI: How to Work on This PR

1. **Read this entire spec + the 6 reference docs before writing any code.** Permission flows are subtle — do not improvise.

2. **Execute in 3 stages with hard checkpoints.** No proceeding without user approval.

3. **Within a stage, autonomous execution is fine.**

4. **Commit frequently but sensibly.** 3-5 commits per stage.

5. **If a build failure blocks progress for more than 2 attempts:** stop and describe.

6. **Do not:**
   - Add pull-to-refresh (PR 06)
   - Add unit toggle (PR 06)
   - Touch TD-001 data source visibility (PR 07)
   - Add any settings screen (out of project scope)
   - Add background location updates (never)
   - Request FINE location permission (COARSE only)

7. **Version changes:** Per CLAUDE.md rule 8, flag any `libs.versions.toml` upgrade request before applying. Adding new entries (play-services-location, accompanist-permissions) is fine and doesn't need flagging — that's not an upgrade.

8. **Edge cases to handle (already in spec, but flag if you find more):**
   - Permission granted but GPS off → show LocationDisabled banner
   - Permission granted but `getCurrentLocation` times out (5s) → silently fall back to default, don't crash, don't show error UI (the banner UX is enough)
   - Geocoder returns empty list → AppError.LocationResolutionFailed
   - Re-detection at the same place → no row duplication (stable `id = "current_location"`)
   - User flips airplane mode after permission granted → location may still work (cached or coarse), but if it fails, fallback to last-selected city

9. **Emulator setup**: emulators without Google Play Services will fail FusedLocationProviderClient. Document this in retrospective if encountered.

10. **If the spec is wrong or contradicts another doc**: flag it. Spec writer admits PR 04 had a misjudgment — be vigilant for similar in this spec.

11. **Permission UX:** The agreed UX is **in-context** (no first-launch prompt). User taps banner to trigger system dialog. Don't add an auto-prompt on launch.
