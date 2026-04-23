# Error Handling

This document defines the complete error-handling strategy for the app.
Audience: anyone (human or AI) writing code that deals with failures.

## Principles

1. **No crash, no blank screen, ever.** Every error state has a defined UI.
2. **Cached data beats error screens.** When Room has data, show it — even if refresh failed.
3. **Errors are modeled as values**, not thrown exceptions. Use `Result<T, AppError>`.
4. **One sealed hierarchy for all errors.** `AppError` covers every failure mode.
5. **Errors carry meaning, not implementation details.** `AppError.NoNetwork` is a user-facing concept, not "SocketTimeoutException".

## The Custom Result Type

Located in `:core:common/result/Result.kt`.

```kotlin
sealed class Result<out T, out E> {
    data class Success<T>(val data: T) : Result<T, Nothing>()
    data class Failure<E>(val error: E) : Result<Nothing, E>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
}
```

### Why not `kotlin.Result`?

- `kotlin.Result` locks the error type to `Throwable`, losing precise typing.
- Kotlin restricts `kotlin.Result` as a public return type, causing API friction.
- We want the compiler to enforce "your error is specifically an `AppError`".

### Helpers (`ResultExtensions.kt`)

```kotlin
inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Failure -> this
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> =
    when (this) {
        is Result.Success -> transform(data)
        is Result.Failure -> this
    }

inline fun <T, E> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> = apply {
    if (this is Result.Success) action(data)
}

inline fun <T, E> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> = apply {
    if (this is Result.Failure) action(error)
}

fun <T, E> Result<T, E>.getOrNull(): T? = (this as? Result.Success)?.data
fun <T, E> Result<T, E>.errorOrNull(): E? = (this as? Result.Failure)?.error
```

---

## The AppError Hierarchy

Located in `:core:common/error/AppError.kt`.

```kotlin
sealed class AppError {

    // --- Network errors ---
    data object NoNetwork : AppError()
    data object NetworkTimeout : AppError()
    data class ServerError(val httpCode: Int) : AppError()
    data object UnknownNetworkError : AppError()

    // --- Location errors ---
    data object LocationPermissionDenied : AppError()
    data object LocationTimeout : AppError()
    data object LocationDisabled : AppError()        // GPS turned off at device level
    data object LocationUnavailable : AppError()     // FusedLocationProvider returned null

    // --- Data / parsing errors ---
    data object CityNotFound : AppError()            // geocoding returned empty
    data object GeocoderFailed : AppError()          // Android Geocoder threw
    data class DataParsingError(val detail: String) : AppError()

    // --- Database errors ---
    data object DatabaseError : AppError()

    // --- Fallback ---
    data class Unexpected(val cause: Throwable) : AppError()
}
```

### Why each subtype exists

| Error | When it occurs | Why distinct |
|-------|----------------|--------------|
| `NoNetwork` | `IOException` — no connectivity | Triggers "offline, showing cached" UX |
| `NetworkTimeout` | `SocketTimeoutException` | User should retry; different copy than `NoNetwork` |
| `ServerError(code)` | HTTP 4xx / 5xx from Open-Meteo | Code might be useful for bug reports |
| `UnknownNetworkError` | Any other network-layer throwable | Fallback; still distinct from `Unexpected` |
| `LocationPermissionDenied` | User rejected or never granted permission | Not a "bug" — triggers in-context prompt in UI |
| `LocationTimeout` | `FusedLocationProvider` timed out | Suggests retry or fallback |
| `LocationDisabled` | Device location services turned off | Different UX: suggest opening Settings |
| `LocationUnavailable` | Provider returned null location | Rare; usually means indoor/no signal |
| `CityNotFound` | Geocoding API returned empty results | Inline search feedback, not a crash state |
| `GeocoderFailed` | Android `Geocoder` threw (service not available) | Rare edge case; different from "no results" |
| `DataParsingError(detail)` | DTO → domain conversion failed unexpectedly | Detail helps debugging; shouldn't happen in production |
| `DatabaseError` | Room write/read threw | Surfaces corrupted DB or migration bug |
| `Unexpected(cause)` | Catch-all | Ensures zero crash: unknown throwable wrapped into `AppError` |

---

## Where Errors Are Created

### Network layer: `apiCall { }` helper

Single central point for mapping Retrofit exceptions to `AppError`. Located in `:core:network/util/ApiCall.kt`.

```kotlin
internal suspend inline fun <T> apiCall(
    crossinline block: suspend () -> T,
): Result<T, AppError> =
    try {
        Result.Success(block())
    } catch (e: SocketTimeoutException) {
        Result.Failure(AppError.NetworkTimeout)
    } catch (e: UnknownHostException) {
        Result.Failure(AppError.NoNetwork)
    } catch (e: IOException) {
        Result.Failure(AppError.NoNetwork)
    } catch (e: HttpException) {
        Result.Failure(AppError.ServerError(httpCode = e.code()))
    } catch (e: SerializationException) {
        Result.Failure(AppError.DataParsingError(detail = e.message.orEmpty()))
    } catch (e: CancellationException) {
        throw e  // Never swallow cancellation — it's structured concurrency, not an error
    } catch (e: Throwable) {
        Result.Failure(AppError.UnknownNetworkError)
    }
```

**Rules:**
- **Every Retrofit call** is wrapped in `apiCall { }`. No manual `try/catch` in data sources.
- **`CancellationException` is rethrown.** Never catch it as an error — coroutine cancellation is a control-flow signal, not a failure.
- If a new exception type needs specific handling, add it to `apiCall` — not to individual callers.

### Location layer

Location errors come from a variety of sources (permissions, provider, Geocoder). Each wrapper returns `Result<T, AppError>`:

```kotlin
internal class LocationProvider @Inject constructor(
    private val client: FusedLocationProviderClient,
    @Dispatcher(Dispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getCurrentLocation(): Result<Coordinates, AppError> {
        // Assumes permission already granted (caller's responsibility)
        return try {
            val location = withTimeoutOrNull(5_000) {
                withContext(ioDispatcher) { client.lastLocation.await() }
            }
            when {
                location == null -> Result.Failure(AppError.LocationTimeout)
                else -> Result.Success(Coordinates(location.latitude, location.longitude))
            }
        } catch (e: SecurityException) {
            Result.Failure(AppError.LocationPermissionDenied)
        } catch (e: Throwable) {
            Result.Failure(AppError.LocationUnavailable)
        }
    }
}
```

### Database layer

Room exceptions are rare but can happen (disk full, corrupted DB). Wrap write operations in data sources:

```kotlin
internal class WeatherLocalDataSource @Inject constructor(
    private val dao: WeatherDao,
) {
    suspend fun upsertWeather(weather: Weather): Result<Unit, AppError> = try {
        dao.upsertFullWeather(
            current = weather.toCurrentEntity(),
            daily = weather.daily.map { it.toEntity(weather.cityId) },
        )
        Result.Success(Unit)
    } catch (e: Throwable) {
        Result.Failure(AppError.DatabaseError)
    }
}
```

Read operations via `Flow` are less commonly wrapped — Room's `Flow` queries don't throw for missing data (they emit empty/null instead). If they do throw, the `Flow` terminates — handle with `.catch { }` in the repository.

---

## Where Errors Flow Through

### Repository

Repositories propagate `Result<T, AppError>` upward. They rarely introduce new errors:

```kotlin
internal class WeatherRepositoryImpl @Inject constructor(
    private val remote: WeatherRemoteDataSource,
    private val local: WeatherLocalDataSource,
) : WeatherRepository {

    override fun observeWeather(cityId: String): Flow<Weather?> =
        local.observeWeather(cityId)

    override suspend fun refreshWeather(city: City): Result<Unit, AppError> =
        remote.fetchWeather(city)
            .flatMap { weather -> local.upsertWeather(weather) }
}
```

Note: `observeWeather` returns `Flow<Weather?>` without `Result` wrapping — Room emissions don't fail as errors (null = no cache).

### ViewModel

The ViewModel is where `AppError` gets translated into `UiState`. This is the only layer that needs to make UX-relevant decisions about errors:

```kotlin
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val observeSelectedCityWeather: ObserveSelectedCityWeatherUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
    private val resolveInitialCity: ResolveInitialCityUseCase,
) : ViewModel() {

    private val _transientMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WeatherUiState> = observeSelectedCityWeather()
        .map { (city, weather) ->
            when {
                weather != null -> WeatherUiState.Success(
                    weather = weather,
                    city = city,
                    isStale = false,  // reconciled on refresh result below
                    transientMessage = _transientMessage.value,
                )
                else -> WeatherUiState.Loading  // no cache yet, waiting for first fetch
            }
        }
        .catch { emit(WeatherUiState.Error(AppError.Unexpected(it), canRetry = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherUiState.Loading,
        )

    init {
        // Trigger initial refresh
        viewModelScope.launch {
            val city = resolveInitialCity()  // UseCase handles fallback chain internally
            refreshWeather(city)
                .onFailure { handleRefreshFailure(it) }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            val currentCity = (uiState.value as? WeatherUiState.Success)?.city ?: return@launch
            refreshWeather(currentCity)
                .onFailure { handleRefreshFailure(it) }
        }
    }

    private fun handleRefreshFailure(error: AppError) {
        // If we have cached data, keep showing it with a banner/snackbar.
        // If we have nothing, UI stays in Loading; reflect as Error only when no cache.
        _transientMessage.value = error.toUserMessage()
    }
}
```

### UiState: when to use `Error` vs `Success(isStale)`

**The rule: "Do we have anything to show?"**

- **Room has data** → `Success(weather, isStale = true, transientMessage = "offline")`.
- **Room has nothing and refresh failed** → `Error(error, canRetry = true)`.

This means: a returning user with cached data **never** sees an error screen. Only true cold-start failures reach `UiState.Error`.

---

## Where Errors Become Strings

Composables never see `AppError` directly. A mapping helper lives in `:core:designsystem` (or can start in the feature module):

```kotlin
@Composable
fun AppError.toUserMessage(): String = when (this) {
    AppError.NoNetwork ->
        "No internet connection"
    AppError.NetworkTimeout ->
        "Request timed out. Check your connection and try again."
    is AppError.ServerError ->
        "Server error. Please try again later."
    AppError.UnknownNetworkError ->
        "Something went wrong with the network."

    AppError.LocationPermissionDenied ->
        "Location permission denied"
    AppError.LocationTimeout ->
        "Couldn't get your location. Try again."
    AppError.LocationDisabled ->
        "Location services are turned off."
    AppError.LocationUnavailable ->
        "Unable to determine your location right now."

    AppError.CityNotFound ->
        "No cities found for that query."
    AppError.GeocoderFailed ->
        "Geocoder is unavailable."
    is AppError.DataParsingError ->
        "Unexpected data format."
    AppError.DatabaseError ->
        "Local database error."

    is AppError.Unexpected ->
        "Something went wrong."
}
```

**Rules:**
- Keep messages concise and user-centric. Not "SocketTimeoutException" — "Request timed out".
- In PR 06 (polish), extract these strings to `strings.xml` for i18n readiness.
- `@Composable` on the function is not strictly necessary now, but keeps the door open for `stringResource()` usage later.

---

## UX Strategy Per Error Type

How each `AppError` appears to the user depends on context:

| Error | Weather Screen (has cache) | Weather Screen (no cache) | City Search |
|-------|----------------------------|---------------------------|-------------|
| `NoNetwork` | Banner + keep showing cache | Full-screen error + retry | Inline message "Check connection" |
| `NetworkTimeout` | Snackbar "Refresh failed, retry?" | Full-screen error + retry | Inline message |
| `ServerError(code)` | Snackbar | Full-screen error + retry | Inline message |
| `LocationPermissionDenied` | Banner "Enable location for auto-detect" | Falls back to Taipei (not an error state) | N/A |
| `LocationTimeout` | Same (fallback to last / default) | Same | N/A |
| `LocationDisabled` | Banner with "Open Settings" action | Fallback + banner | N/A |
| `CityNotFound` | N/A | N/A | Inline "No cities found" |
| `Unexpected` | Snackbar "Unexpected error" | Full-screen error + retry | Inline generic message |

**Guiding questions for future errors:**
1. Can we preserve existing UI (cache)? → Use a banner / snackbar.
2. Is there a user action that can recover? → Show it.
3. Is this inline to a field/search? → Inline message.
4. Otherwise → Full-screen error with retry.

---

## Crash Prevention

Beyond `Result`-based errors, three safety nets prevent crashes:

### 1. `.catch { }` on ViewModel flows

Every ViewModel flow chain ends with a `.catch { }` that emits an `Error` state:

```kotlin
observeX()
    .map { ... }
    .catch { emit(UiState.Error(AppError.Unexpected(it), canRetry = true)) }
    .stateIn(...)
```

### 2. `try/catch` in `init` blocks

ViewModel `init` actions (e.g., initial refresh) are wrapped so a failure doesn't prevent `uiState` from emitting:

```kotlin
init {
    viewModelScope.launch {
        try {
            // initial work
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            _transientMessage.value = AppError.Unexpected(e).toUserMessage()
        }
    }
}
```

### 3. No `!!` in Composables

The prohibition on `!!` (see `CODING_CONVENTIONS.md` §2.3) means a null value flows gracefully through `?:` fallbacks or conditional rendering, never throwing `NullPointerException`.

---

## Testing Errors

Every error path should have a test:

```kotlin
@Test
fun `when network fails and cache exists, state stays Success with stale flag`() = runTest {
    val fakeRemote = FakeWeatherRemoteDataSource().apply {
        result = Result.Failure(AppError.NoNetwork)
    }
    val fakeLocal = FakeWeatherLocalDataSource().apply {
        preloadedWeather = sampleWeather
    }
    val repo = WeatherRepositoryImpl(fakeRemote, fakeLocal)
    val viewModel = WeatherViewModel(...)

    viewModel.uiState.test {
        // Loading → Success with cache → Refresh failure surfaces as transientMessage
        skipItems(1)
        val state = awaitItem() as WeatherUiState.Success
        assertThat(state.weather).isEqualTo(sampleWeather)
        // Refresh outcome is observable via transientMessage
        awaitItem().let {
            val refreshed = it as WeatherUiState.Success
            assertThat(refreshed.transientMessage).isNotNull()
        }
    }
}
```

Key test cases to cover:
- Each `AppError` subtype triggers the expected `UiState`.
- Cache-present vs cache-absent behavior differs correctly.
- `CancellationException` is not caught as an error (it propagates).

---

## Summary

1. **Errors are `AppError` values.** One sealed hierarchy covers all failure modes.
2. **`Result<T, AppError>` is the currency** between data layer and ViewModel.
3. **`apiCall { }` is the single point** where Retrofit exceptions become `AppError`.
4. **ViewModel decides UX:** preserve cached content when possible, escalate to `Error` state only when there's truly nothing to show.
5. **Composables see strings, not `AppError`**: one mapping function at the boundary.
6. **Three safety nets prevent crashes:** `.catch { }` on flows, `try/catch` in init, no `!!` anywhere.
