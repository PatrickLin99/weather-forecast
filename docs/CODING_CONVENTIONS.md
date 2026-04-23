# Coding Conventions

Concrete style rules for this project. When Claude CLI generates code,
it should conform to everything here.

> When in doubt, follow the style of existing code in the same module.

## 1. Naming

### 1.1 Files and classes
- Kotlin files named after the primary declaration: `WeatherRepository.kt`, `WeatherUiState.kt`.
- Multiple related sealed children in one file: name the file after the parent (e.g., `AppError.kt` contains all `AppError` subtypes).
- ViewModels: `XxxViewModel`. Screens: `XxxScreen`. Routes: `XxxRoute`.
- Repository interface: `XxxRepository`. Implementation: `XxxRepositoryImpl`.

### 1.2 Functions and variables
- Functions: `camelCase`, verb-first (`fetchWeather`, `observeSelectedCityId`).
- Reactive observation: `observeX()` returning `Flow<T>`.
- One-shot suspend action: `fetchX()` / `refreshX()` / `saveX()` returning `Result<T, AppError>`.
- Boolean properties/functions: `isX` / `hasX` / `canX`.

### 1.3 Constants
- Top-level or companion `const val`: `SCREAMING_SNAKE_CASE`.
- Enum entries: `SCREAMING_SNAKE_CASE`.
- UI dimension/magic numbers: avoid — extract to `@Composable` parameters or theme.

### 1.4 Packages
- Base: `com.example.weatherforecast`
- Module-specific sub-packages include module path:
  - `com.example.weatherforecast.core.network.dto`
  - `com.example.weatherforecast.feature.weather.component`
- This makes every import statement reveal the source module.

---

## 2. Kotlin Style

### 2.1 Visibility

**Default to the most restrictive visibility that works.**

| Visibility | When to use |
|------------|-------------|
| `private` | Anything that doesn't need to cross a file |
| `internal` | Implementation classes in core modules (e.g., `WeatherRepositoryImpl`) |
| `public` (default) | Only interfaces, data classes in `:core:model`, and public API of each module |

**Example:**
```kotlin
// ✅ Good — internal because only Hilt needs to see it
internal class WeatherRepositoryImpl @Inject constructor(
    private val remoteDataSource: WeatherRemoteDataSource,
    private val localDataSource: WeatherLocalDataSource,
) : WeatherRepository { ... }

// ❌ Bad — unnecessary public exposure
class WeatherRepositoryImpl ... : WeatherRepository { ... }
```

### 2.2 Data, sealed, and enum classes

- **`data class`** for value objects (domain models, UI state payloads).
- **`sealed interface`** for closed hierarchies with behavior or state discrimination (UiState, AppError).
- **`enum class`** for fixed sets without internal variation (`TemperatureUnit`, `WeatherCondition`).
- **`data object`** for singletons in sealed hierarchies (e.g., `AppError.NoNetwork`).

### 2.3 Null safety

- **Avoid `!!`.** It's almost always a code smell.
- **Avoid `lateinit`** in new code. Use nullable + smart-cast or constructor injection.
- Use `?.let { }` for transformations, `?:` for fallback values.
- For Compose state: `remember { mutableStateOf<T?>(null) }` is fine when the state genuinely starts absent.

### 2.4 Extension functions

- Use them to **add domain-specific convenience**, not to show off.
- Place in the same package as the type they extend.
- Prefer file name reflecting the receiver: `CityExtensions.kt`.

---

## 3. Error Handling

### 3.1 Result type

**Always use the custom `Result<T, AppError>` from `:core:common`.** Never `kotlin.Result`.

```kotlin
// ✅ Our Result type
suspend fun refreshWeather(city: City): Result<Unit, AppError>

// ❌ Never
suspend fun refreshWeather(city: City): kotlin.Result<Unit>
```

### 3.2 Network layer — `apiCall { }` helper

All Retrofit calls go through the central helper to map exceptions consistently:

```kotlin
// ✅ Good
internal class WeatherRemoteDataSource @Inject constructor(
    private val api: OpenMeteoForecastApi,
) {
    suspend fun fetchWeather(city: City): Result<Weather, AppError> = apiCall {
        api.getForecast(city.latitude, city.longitude).toDomain()
    }
}

// ❌ Bad — manual try/catch duplicating error mapping
suspend fun fetchWeather(city: City): Result<Weather, AppError> {
    return try {
        val response = api.getForecast(...)
        Result.Success(response.toDomain())
    } catch (e: IOException) {
        Result.Failure(AppError.NoNetwork)
    } catch (e: Exception) { ... }
}
```

### 3.3 Data source & repository

- Network / database data sources return `Result<T, AppError>` for operations that can fail.
- Repository methods mirror this: `suspend fun refreshX(): Result<Unit, AppError>`.
- **Never throw** for expected errors. Throwing is reserved for programmer errors (illegal state).

### 3.4 UI layer

- ViewModel converts `AppError` into user-facing `UiState.Error` or `UiState.Success(transientMessage = ...)`.
- Composables do not know about `AppError` directly. They receive already-resolved strings/states.

See `docs/ERROR_HANDLING.md` for the full `AppError` hierarchy and mapping strategy.

---

## 4. Coroutines and Flow

### 4.1 Flow vs suspend

| Return type | Use when |
|-------------|----------|
| `Flow<T>` | Observing changing data over time (Room queries, DataStore) |
| `suspend fun(): T` | One-shot operation that eventually returns |
| `suspend fun(): Result<T, AppError>` | One-shot that can fail recoverably |

### 4.2 `stateIn` for UI state

ViewModel's exposed StateFlow always uses this pattern:

```kotlin
val uiState: StateFlow<WeatherUiState> = combine(...)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeatherUiState.Loading,
    )
```

- `WhileSubscribed(5_000)` keeps upstream alive 5s after last collector — survives config changes.
- Always provide an `initialValue`.

### 4.3 Dispatcher injection

**Never hardcode `Dispatchers.IO`.** Always inject qualified dispatchers.

```kotlin
// ✅ Good
internal class SomeDataSource @Inject constructor(
    @Dispatcher(Dispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun readFile() = withContext(ioDispatcher) { ... }
}

// ❌ Bad
suspend fun readFile() = withContext(Dispatchers.IO) { ... }
```

Why: testability. Tests replace dispatchers with `UnconfinedTestDispatcher`.

### 4.4 Scope discipline

- ViewModel: `viewModelScope` only.
- Repository/data source: no scope ownership — they are driven by callers.
- Use `withContext(ioDispatcher) { }` for heavy operations inside suspend functions.
- Never use `GlobalScope`.

### 4.5 Flow operators — common patterns

| Need | Operator |
|------|----------|
| React to latest upstream change, cancel previous | `flatMapLatest` |
| Combine multiple flows | `combine` |
| Initial value before upstream emits | `.onStart { emit(...) }` |
| Error recovery without crashing collector | `.catch { emit(ErrorState(...)) }` |
| Debounce search input | `.debounce(300.milliseconds)` |

---

## 5. Jetpack Compose

### 5.1 Composable naming

- `PascalCase`, noun-based: `CurrentWeatherHeader`, `DailyForecastList`.
- Screens: `XxxScreen`, top-level entry.
- Stateful composable may delegate to stateless version: `WeatherScreen` (reads ViewModel) → `WeatherContent` (pure).

### 5.2 Parameter order

Standard order for every Composable:

```kotlin
@Composable
fun SomeComponent(
    // 1. Required data parameters (no defaults)
    weather: Weather,
    city: City,
    // 2. Required callbacks
    onRefresh: () -> Unit,
    // 3. Modifier (always optional, default = Modifier)
    modifier: Modifier = Modifier,
    // 4. Other optional parameters with defaults
    isStale: Boolean = false,
) { ... }
```

- **`modifier` goes right after required params, before other optionals.**
- Modifier is the first thing applied to the root element: `Column(modifier = modifier) { ... }`.

### 5.3 State hoisting

- Screens hoist state to ViewModel.
- Reusable components accept state + callbacks, own nothing.

```kotlin
// ✅ Hoisted — testable, previewable
@Composable
fun WeatherContent(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) { ... }

// ❌ Not hoisted — hard to preview, tied to ViewModel
@Composable
fun WeatherContent(modifier: Modifier = Modifier) {
    val viewModel: WeatherViewModel = hiltViewModel()
    ...
}
```

Connect at the top of the screen:

```kotlin
@Composable
fun WeatherScreen(onNavigateToCityList: () -> Unit) {
    val viewModel: WeatherViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WeatherContent(
        uiState = uiState,
        onRefresh = viewModel::onRefresh,
        onNavigateToCityList = onNavigateToCityList,
    )
}
```

### 5.4 State collection

**Always** use `collectAsStateWithLifecycle()`, never plain `collectAsState()`:

```kotlin
// ✅ Good — lifecycle-aware, stops collecting when STOPPED
val uiState by viewModel.uiState.collectAsStateWithLifecycle()

// ❌ Bad — keeps collecting even when app is backgrounded
val uiState by viewModel.uiState.collectAsState()
```

### 5.5 Preview

- Every non-trivial Composable should have a `@Preview`.
- Use `@PreviewParameter` or preview sample data from `ui/PreviewData.kt`.
- Previews are `private` functions at the bottom of the file.

```kotlin
@Preview(showBackground = true)
@Composable
private fun CurrentWeatherHeaderPreview() {
    WeatherAppTheme {
        CurrentWeatherHeader(weather = PreviewData.sampleWeather)
    }
}
```

### 5.6 `remember` vs `rememberSaveable`

- `remember { ... }` for transient UI state (animation, computed values).
- `rememberSaveable { ... }` for state that must survive config change or process death (text field content, scroll position when meaningful).
- **Persistent user state goes to ViewModel**, not either of these.

---

## 6. Hilt

### 6.1 Injection

Prefer **constructor injection** with `@Inject constructor(...)`. Field injection only when forced by the framework (Application, Activity, Fragment, ViewModel via `@HiltViewModel`).

### 6.2 `@Binds` vs `@Provides`

**`@Binds`** when the binding is simply "interface → implementation" — no construction logic needed. Must be in an `abstract class`.

```kotlin
// ✅ Good — @Binds
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindWeatherRepository(
        impl: WeatherRepositoryImpl,
    ): WeatherRepository
}
```

**`@Provides`** when you need to construct something (Retrofit, DataStore, etc.).

```kotlin
// ✅ Good — @Provides for constructed types
@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(OPEN_METEO_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
```

### 6.3 Module location

- Each module has its own `di/` package with module-specific `@Module`.
- All use `@InstallIn(SingletonComponent::class)` unless there's a specific reason otherwise.
- Modules are `internal` — external code doesn't need to reference them.

### 6.4 Qualifiers

- Define qualifiers in `:core:common/dispatcher/` or relevant core module.
- Use `@Qualifier` annotation, `AnnotationRetention.BINARY`.

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Dispatcher(val value: DispatcherType)

enum class DispatcherType { IO, Default, Main }
```

---

## 7. Retrofit and kotlinx.serialization

### 7.1 DTOs

- Place in `:core:network/dto/`.
- Suffix: `Dto`.
- Use `@Serializable` and `@SerialName` for JSON field mapping.

```kotlin
@Serializable
internal data class ForecastResponseDto(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("current") val current: CurrentWeatherDto,
    @SerialName("daily") val daily: DailyForecastDto,
)
```

- DTOs are `internal` — they must not leak outside `:core:network`.
- Every DTO has a corresponding mapper to a domain model.

### 7.2 API interfaces

```kotlin
internal interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
    ): ForecastResponseDto
}
```

- API functions are `suspend`. No `Call<T>`, no RxJava.
- Default parameters for stable query strings. Non-stable parameters (coordinates, unit) are passed in by caller.

### 7.3 Mappers

- One file per mapping direction: `WeatherDtoMapper.kt`.
- Use extension functions: `fun ForecastResponseDto.toDomain(): Weather = ...`
- Mappers are pure, synchronous, `internal`.

---

## 8. Room

### 8.1 Entity naming

- Suffix: `Entity`. Place in `:core:database/entity/`.
- Use `@Entity(tableName = "snake_case_table_name")`.
- Primary key explicit: `@PrimaryKey val id: String`.
- Foreign keys with `onDelete = CASCADE` when child lifecycle matches parent.

### 8.2 DAO naming

- One DAO per related table group: `CityDao`, `WeatherDao`.
- Methods:
  - Queries returning Flow: `observeX(): Flow<T>`.
  - One-shot suspend queries: `getX(): T?` (nullable if can be absent).
  - Writes: `upsertX()`, `deleteX()`. Use `@Upsert` where possible.

```kotlin
@Dao
internal interface WeatherDao {
    @Query("SELECT * FROM current_weather WHERE cityId = :cityId")
    fun observeCurrentWeather(cityId: String): Flow<CurrentWeatherEntity?>

    @Upsert
    suspend fun upsertCurrentWeather(weather: CurrentWeatherEntity)

    @Transaction
    suspend fun upsertFullWeather(
        current: CurrentWeatherEntity,
        daily: List<DailyForecastEntity>,
    ) {
        upsertCurrentWeather(current)
        upsertDailyForecasts(daily)
    }
}
```

### 8.3 Transactions

- Use `@Transaction` for any write involving multiple DAOs or tables.
- Use `@Transaction` on read queries that `@Relation` joins data.

---

## 9. Testing

### 9.1 Test naming

Use backticks for human-readable test names:

```kotlin
@Test
fun `when refresh succeeds, weather is upserted into Room`() = runTest { ... }

@Test
fun `given no network, observeWeather still emits cached value`() = runTest { ... }
```

Format: `given X, when Y, then Z` or `when Y, then Z`.

### 9.2 Test structure — arrange, act, assert

```kotlin
@Test
fun `when refresh fails with NoNetwork, state shows stale banner`() = runTest {
    // Arrange
    val fakeRepository = FakeWeatherRepository().apply {
        refreshResult = Result.Failure(AppError.NoNetwork)
    }
    val viewModel = WeatherViewModel(fakeRepository, ...)

    // Act
    viewModel.onRefresh()

    // Assert
    viewModel.uiState.test {
        val state = awaitItem() as WeatherUiState.Success
        assertThat(state.isStale).isTrue()
    }
}
```

### 9.3 Fake vs mock

- **Prefer fakes (hand-written test doubles)** over MockK when the interface is simple.
- Use MockK for verification-heavy tests or when the real class is hard to fake.
- Fakes go in `src/test/kotlin/.../fake/`.

### 9.4 Coroutines in tests

- Use `runTest { }` from `kotlinx-coroutines-test`.
- Inject test dispatchers via the same qualifiers as production.
- For ViewModel tests, use a `MainDispatcherRule` at the top of the file:

```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()
```

### 9.5 Turbine for Flow

```kotlin
viewModel.uiState.test {
    assertThat(awaitItem()).isEqualTo(WeatherUiState.Loading)
    assertThat(awaitItem()).isInstanceOf(WeatherUiState.Success::class.java)
    cancelAndIgnoreRemainingEvents()
}
```

---

## 10. Build Configuration

### 10.1 Version Catalog

All dependencies go through `gradle/libs.versions.toml`. **Never** hardcode versions in module build files.

```kotlin
// ✅ Good
dependencies {
    implementation(libs.retrofit)
    implementation(libs.okhttp)
}

// ❌ Bad
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
}
```

### 10.2 Type-safe project accessors

Use `projects.core.xxx` syntax:

```kotlin
// ✅ Good
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
}

// ❌ Bad
dependencies {
    implementation(project(":core:domain"))
}
```

Enable in `settings.gradle.kts`:
```kotlin
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
```

### 10.3 Convention plugins

Most modules apply a convention plugin (one line). Only module-specific dependencies go in the module's own `build.gradle.kts`.

```kotlin
// :core:network/build.gradle.kts
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
}
```

### 10.4 `api` vs `implementation`

- Default to `implementation` (faster builds).
- Use `api` only when a module must re-export a dependency (e.g., `:core:designsystem` exposes Compose types to features).

---

## 11. Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <short summary in imperative mood>

<optional body explaining the why>
```

### Types

| Type | Use for |
|------|---------|
| `feat` | New user-visible feature |
| `fix` | Bug fix |
| `refactor` | Code change that neither adds features nor fixes bugs |
| `test` | Adding or modifying tests |
| `docs` | Documentation only |
| `chore` | Build config, dependencies, tooling |
| `style` | Formatting, whitespace (no code change) |
| `perf` | Performance improvement |

### Examples

```
feat: display weekly forecast list
fix: prevent crash when search query is empty
refactor: extract WeatherDtoMapper to separate file
test: add ResolveInitialCityUseCase fallback tests
chore: update Kotlin to 2.1.0
docs: add ERROR_HANDLING.md
```

### Guidelines

- Subject line: imperative mood ("add", not "added"), ≤ 72 characters.
- Subject line: lowercase after type, no period at end.
- Body (optional): wrap at 72 chars, explain *why* not *what*.
- One logical change per commit. Split large changes.
- Scope (parentheses after type) is **not used** in this project. The summary itself should be descriptive enough.

---

## 12. Prohibited Patterns

Quick reference for things **never** to do in this codebase.

| ❌ Don't | ✅ Do |
|---------|------|
| `kotlin.Result` | Custom `Result<T, AppError>` from `:core:common` |
| `Dispatchers.IO` hardcoded | Inject `@Dispatcher(Dispatcher.IO) CoroutineDispatcher` |
| `GlobalScope.launch { }` | `viewModelScope`, or structured scope from caller |
| `collectAsState()` | `collectAsStateWithLifecycle()` |
| `!!` non-null assertion | Smart cast, `?.let`, `?:`, or refactor to avoid |
| `lateinit var` | Constructor injection, or nullable with smart cast |
| Feature importing `:core:network` / `:core:database` / `:core:datastore` / `:core:location` | Depend on `:core:domain` interfaces only |
| DTO leaking out of `:core:network` | Map to domain model before returning from data source |
| Entity leaking out of `:core:database` | Same — map before returning |
| `android.util.Log.d(...)` in committed code | No logging in production code paths. If needed for temporary debugging, use `android.util.Log` locally and remove before commit. |
| `Gson` / `Moshi` | `kotlinx.serialization` |
| `@Provides` when `@Binds` would work | `@Binds` (faster KSP, clearer intent) |
| `project(":core:xxx")` | `projects.core.xxx` |
| Hardcoded dependency version in `build.gradle.kts` | Reference via `libs.xxx` from `libs.versions.toml` |
| `Throwable` as error type in public API | `AppError` sealed class |
| `println` / `System.out.println` | Never in production code |
| String resources as hardcoded literals (long term) | `stringResource(R.string.xxx)` — see note below |

### Note on string resources

For this project's scope, hardcoded user-facing strings in Composables are acceptable in early PRs for speed. **PR 06 (polish) extracts all user-facing strings to `strings.xml`** for proper i18n foundation. Claude CLI should not refactor this proactively before PR 06.
