# PR 02 — Core Foundations: Detailed Spec

> Companion to `docs/DEVELOPMENT_PLAN.md` § PR 02.
> Execution mode: **single-pass** — Claude CLI completes all six core modules in one go.
> No per-module checkpoints. Final verification via `./gradlew build`.

## Goal Recap

Implement the six non-feature core modules so PR 03 can integrate them into a working weather screen.

**End state:**
- All six `:core:*` modules (except `:domain` and `:data`, which belong to PR 03) have concrete, production-quality implementations.
- `./gradlew build` passes cleanly.
- App still launches and shows AS default Hello Compose (no UI change this PR).
- No feature module code, no repository implementations.

## Prerequisites

- [ ] PR 01 merged to `main`.
- [ ] Local `main` is up to date (`git checkout main && git pull`).
- [ ] `./gradlew clean build` passes on a fresh `main` checkout.
- [ ] Branch created: `git checkout -b feat/02-core-foundations`.
- [ ] `CLAUDE.md` "Current PR" section updated to point at PR 02.

## Scope Overview

Six modules, implemented in dependency order. Claude CLI should process them sequentially (not in parallel) because later modules depend on earlier ones.

```
1. :core:model           ← pure data classes (foundation)
2. :core:common          ← Result, AppError, Dispatchers (used by everyone)
3. :core:designsystem    ← Theme + shared Composables (independent)
4. :core:network         ← Retrofit + DTOs + mappers (depends on model, common)
5. :core:database        ← Room + Entities + DAOs (depends on model, common)
6. :core:datastore       ← DataStore Preferences (depends on model, common)
```

**Not in scope:**
- `:core:domain` — deferred to PR 03
- `:core:data` — deferred to PR 03
- `:core:location` — deferred to PR 05
- Any `:feature:*` code
- Any tests (tests are welcome if trivial, otherwise defer to PR 07)

## Reference Documents (Must-Read Before Coding)

Claude CLI should open and scan these before starting:

1. **`docs/MODULE_STRUCTURE.md`** — authoritative package structure and key types for each module
2. **`docs/CODING_CONVENTIONS.md`** — naming, visibility, DI, error handling style
3. **`docs/ERROR_HANDLING.md`** — especially the `AppError` full hierarchy and `apiCall { }` helper
4. **`docs/ARCHITECTURE.md`** — dependency direction and boundaries

When these docs conflict with this spec, follow the docs. If this spec is stricter than the docs (e.g., a specific file shape), follow the spec.

---

## Module 1: `:core:model`

Pure Kotlin domain models. Foundation for all other modules.

### Files to create

```
core/model/src/main/kotlin/com/opennet/weatherforecast/core/model/
├── City.kt
├── Weather.kt
├── DailyForecast.kt
├── HourlyForecast.kt
├── TemperatureUnit.kt
├── WeatherCondition.kt
└── Coordinates.kt
```

### Contents

**`City.kt`**:
```kotlin
data class City(
    val id: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false,
)
```

**`Coordinates.kt`**:
```kotlin
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)
```

**`WeatherCondition.kt`**:
```kotlin
enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW,
    THUNDERSTORM,
    FOG,
    UNKNOWN,
}
```

**`TemperatureUnit.kt`**:
```kotlin
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT,
}
```

**`HourlyForecast.kt`** (reserved — included in model but not yet surfaced to UI):
```kotlin
import kotlinx.datetime.Instant

data class HourlyForecast(
    val time: Instant,
    val temperature: Double,
    val condition: WeatherCondition,
)
```

**`DailyForecast.kt`**:
```kotlin
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class DailyForecast(
    val date: LocalDate,
    val tempMin: Double,
    val tempMax: Double,
    val condition: WeatherCondition,
    val sunrise: Instant?,
    val sunset: Instant?,
    val precipitationProbability: Int?,  // 0..100, null if not provided
)
```

**`Weather.kt`**:
```kotlin
import kotlinx.datetime.Instant

data class Weather(
    val cityId: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,              // percentage 0..100
    val windSpeed: Double,          // m/s or mph — unit is determined by API request
    val condition: WeatherCondition,
    val unit: TemperatureUnit,
    val updatedAt: Instant,
    val daily: List<DailyForecast>,
    val hourly: List<HourlyForecast> = emptyList(),  // reserved for future use
)
```

### Module Gradle config

**`core/model/build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.weatherapp.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
}
```

### Catalog updates

Add to `gradle/libs.versions.toml` if not present:

```toml
[versions]
kotlinxDatetime = "0.6.1"

[libraries]
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }
```

### Rules

- No Android imports (not even `androidx.annotation`).
- No Hilt annotations.
- No `@Serializable` unless a model needs to travel through Navigation routes (currently none do).

---

## Module 2: `:core:common`

Cross-cutting utilities. Foundation for error handling and DI conventions.

### Files to create

```
core/common/src/main/kotlin/com/opennet/weatherforecast/core/common/
├── result/
│   ├── Result.kt
│   └── ResultExtensions.kt
├── error/
│   └── AppError.kt
├── dispatcher/
│   ├── Dispatcher.kt
│   └── DispatcherQualifier.kt
├── constant/
│   └── DefaultCity.kt
└── di/
    └── DispatcherModule.kt
```

### Contents

**`result/Result.kt`**: Use the exact definition from `docs/ERROR_HANDLING.md` § "The Custom Result Type".

**`result/ResultExtensions.kt`**: Include `map`, `flatMap`, `onSuccess`, `onFailure`, `getOrNull`, `errorOrNull` — all from `docs/ERROR_HANDLING.md`.

**`error/AppError.kt`**: Use the exact 13-subtype hierarchy from `docs/ERROR_HANDLING.md` § "The AppError Hierarchy".

**`dispatcher/Dispatcher.kt`**:
```kotlin
enum class Dispatcher {
    IO,
    Default,
    Main,
}
```

**`dispatcher/DispatcherQualifier.kt`**:
```kotlin
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DispatcherQualifier(val value: Dispatcher)
```

**`constant/DefaultCity.kt`**:
```kotlin
import com.example.weatherforecast.core.model.City

object DefaultCity {
    val TAIPEI = City(
        id = "default_taipei",
        name = "Taipei",
        country = "Taiwan",
        latitude = 25.0330,
        longitude = 121.5654,
        isCurrentLocation = false,
    )
}
```

**`di/DispatcherModule.kt`**:
```kotlin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DispatcherModule {

    @Provides
    @Singleton
    @DispatcherQualifier(Dispatcher.IO)
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @DispatcherQualifier(Dispatcher.Default)
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @DispatcherQualifier(Dispatcher.Main)
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
```

### Module Gradle config

**`core/common/build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
}

android {
    namespace = "com.example.weatherforecast.core.common"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
```

### Catalog updates

Add if missing:
```toml
[libraries]
javax-inject = { module = "javax.inject:javax.inject", version = "1" }
```

### Note

Previously `MODULE_STRUCTURE.md` described `:core:common` as a Kotlin JVM module. We're upgrading it to Android Library because Hilt's `@Module` annotation processing requires the Android toolchain. Domain and common both share this constraint. This is a minor deviation from the original docs — update `MODULE_STRUCTURE.md` if bothered, or leave as a known exception.

---

## Module 3: `:core:designsystem`

Material 3 theme and shared Composables.

### Files to create

```
core/designsystem/src/main/kotlin/com/opennet/weatherforecast/core/designsystem/
├── theme/
│   ├── Color.kt
│   ├── Theme.kt
│   ├── Type.kt
│   └── Shape.kt
├── component/
│   ├── WeatherIcon.kt
│   ├── LoadingIndicator.kt
│   ├── ErrorState.kt
│   ├── EmptyState.kt
│   └── TemperatureText.kt
└── icon/
    └── WeatherAppIcons.kt
```

### Contents

**`theme/Color.kt`**: Define a minimal Material 3 light color scheme. Use neutral blues and grays suitable for a weather app. No dark theme.

**`theme/Theme.kt`**:
```kotlin
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun WeatherAppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = WeatherAppTypography,
        shapes = WeatherAppShapes,
        content = content,
    )
}
```

**`theme/Type.kt`**: Material 3 default typography. Add overrides only for the main temperature display (large, light weight).

**`theme/Shape.kt`**: Material 3 default shapes.

**`component/WeatherIcon.kt`**:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.weatherforecast.core.model.WeatherCondition

@Composable
fun WeatherIcon(
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when (condition) {
        WeatherCondition.CLEAR -> Icons.Filled.WbSunny
        WeatherCondition.CLOUDY -> Icons.Filled.Cloud
        WeatherCondition.RAIN -> Icons.Filled.WaterDrop
        WeatherCondition.SNOW -> Icons.Filled.AcUnit
        WeatherCondition.THUNDERSTORM -> Icons.Filled.Bolt
        WeatherCondition.FOG -> Icons.Filled.Cloud  // no dedicated fog icon
        WeatherCondition.UNKNOWN -> Icons.Filled.HelpOutline
    }
    Icon(
        imageVector = icon,
        contentDescription = condition.name,
        modifier = modifier,
    )
}
```

**`component/LoadingIndicator.kt`**: Centered `CircularProgressIndicator` wrapper.

**`component/ErrorState.kt`**:
```kotlin
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
```

**`component/EmptyState.kt`**: Similar pattern — icon + message, no retry.

**`component/TemperatureText.kt`**:
```kotlin
@Composable
fun TemperatureText(
    value: Double,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
) {
    val symbol = when (unit) {
        TemperatureUnit.CELSIUS -> "°C"
        TemperatureUnit.FAHRENHEIT -> "°F"
    }
    Text(
        text = "${value.toInt()}$symbol",
        style = style,
        modifier = modifier,
    )
}
```

**`icon/WeatherAppIcons.kt`**:
```kotlin
object WeatherAppIcons {
    // Re-export commonly used icons as named references
    val LocationOn = Icons.Filled.LocationOn
    val Search = Icons.Filled.Search
    val Settings = Icons.Filled.Settings
    val Menu = Icons.Filled.Menu
    // add as needed
}
```

### Module Gradle config

**`core/designsystem/build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.weatherforecast.core.designsystem"
    buildFeatures { compose = true }
}

dependencies {
    api(projects.core.model)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui.tooling.preview)
    debugApi(libs.androidx.compose.ui.tooling)
    api(libs.coil.compose)
}
```

### Note

If the existing convention plugin `weatherapp.android.library` does not enable Compose automatically, apply `compose-compiler` plugin manually as shown, and set `buildFeatures.compose = true`. Alternatively, extend the convention plugin — but that's scope creep for this PR.

---

## Module 4: `:core:network`

Retrofit + OkHttp + kotlinx-serialization + Open-Meteo API.

### Files to create

```
core/network/src/main/kotlin/com/opennet/weatherforecast/core/network/
├── api/
│   ├── OpenMeteoForecastApi.kt
│   └── OpenMeteoGeocodingApi.kt
├── dto/
│   ├── ForecastResponseDto.kt
│   ├── CurrentWeatherDto.kt
│   ├── DailyForecastDto.kt
│   ├── HourlyForecastDto.kt
│   └── GeocodingResponseDto.kt
├── mapper/
│   ├── WeatherDtoMapper.kt
│   ├── CityDtoMapper.kt
│   └── WeatherCodeMapper.kt
├── datasource/
│   ├── WeatherRemoteDataSource.kt
│   └── CityRemoteDataSource.kt
├── util/
│   └── ApiCall.kt
└── di/
    ├── NetworkModule.kt
    └── Qualifiers.kt
```

### Contents highlights

**`api/OpenMeteoForecastApi.kt`**:
```kotlin
internal interface OpenMeteoForecastApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("forecast_days") forecastDays: Int = 7,
    ): ForecastResponseDto

    companion object {
        private const val CURRENT_PARAMS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code"
        private const val DAILY_PARAMS =
            "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
        private const val HOURLY_PARAMS =
            "temperature_2m,weather_code"
    }
}
```

**`api/OpenMeteoGeocodingApi.kt`**:
```kotlin
internal interface OpenMeteoGeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingResponseDto
}
```

**DTOs** — mirror Open-Meteo's response shape exactly. Reference: https://open-meteo.com/en/docs

Example `CurrentWeatherDto`:
```kotlin
@Serializable
internal data class CurrentWeatherDto(
    @SerialName("time") val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("weather_code") val weatherCode: Int,
)
```

Similarly structured DTOs for `DailyForecastDto`, `HourlyForecastDto`, `ForecastResponseDto` (wrapper), `GeocodingResponseDto`.

**`util/ApiCall.kt`**: Use the exact implementation from `docs/ERROR_HANDLING.md` § "Network layer — `apiCall { }` helper".

**`mapper/WeatherCodeMapper.kt`**:
```kotlin
internal fun Int.toWeatherCondition(): WeatherCondition = when (this) {
    0 -> WeatherCondition.CLEAR
    1, 2, 3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
    95, 96, 99 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.UNKNOWN
}
```

Reference: https://open-meteo.com/en/docs (WMO Weather interpretation codes table)

**`mapper/WeatherDtoMapper.kt`**:
```kotlin
internal fun ForecastResponseDto.toDomain(cityId: String, unit: TemperatureUnit): Weather {
    return Weather(
        cityId = cityId,
        temperature = current.temperature,
        feelsLike = current.apparentTemperature,
        humidity = current.humidity,
        windSpeed = current.windSpeed,
        condition = current.weatherCode.toWeatherCondition(),
        unit = unit,
        updatedAt = Clock.System.now(),
        daily = daily.toDomain(),
        hourly = hourly.toDomain(),
    )
}
// daily and hourly also get their own mapper functions
```

**`mapper/CityDtoMapper.kt`**: Convert geocoding result items to `City`.

**`datasource/WeatherRemoteDataSource.kt`**:
```kotlin
internal class WeatherRemoteDataSource @Inject constructor(
    private val api: OpenMeteoForecastApi,
) {
    suspend fun fetchWeather(
        city: City,
        unit: TemperatureUnit,
    ): Result<Weather, AppError> = apiCall {
        val tempUnitParam = if (unit == TemperatureUnit.FAHRENHEIT) "fahrenheit" else "celsius"
        api.getForecast(
            latitude = city.latitude,
            longitude = city.longitude,
            temperatureUnit = tempUnitParam,
        ).toDomain(cityId = city.id, unit = unit)
    }
}
```

**`datasource/CityRemoteDataSource.kt`**: wraps geocoding API, returns `Result<List<City>, AppError>`.

**`di/NetworkModule.kt`**: Provides `Retrofit`, `OkHttpClient`, `Json`, both API services.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    private const val FORECAST_BASE_URL = "https://api.open-meteo.com/"
    private const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @ForecastRetrofit
    fun provideForecastRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(FORECAST_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @GeocodingRetrofit
    fun provideGeocodingRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(GEOCODING_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideForecastApi(@ForecastRetrofit retrofit: Retrofit): OpenMeteoForecastApi =
        retrofit.create(OpenMeteoForecastApi::class.java)

    @Provides
    @Singleton
    fun provideGeocodingApi(@GeocodingRetrofit retrofit: Retrofit): OpenMeteoGeocodingApi =
        retrofit.create(OpenMeteoGeocodingApi::class.java)
}
```

**`di/Qualifiers.kt`**:
```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ForecastRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class GeocodingRetrofit
```

### Module Gradle config

```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.weatherforecast.core.network"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
```

### Network security config

Because Open-Meteo uses HTTPS, no `android:usesCleartextTraffic` needed. The `:app` manifest already allows HTTPS by default.

### INTERNET permission

`:app/AndroidManifest.xml` must include `<uses-permission android:name="android.permission.INTERNET" />`. This PR doesn't touch `:app/AndroidManifest.xml`, but verify it exists — if not, add it as part of this PR (minimal scope exception).

---

## Module 5: `:core:database`

Room persistence layer.

### Files to create

```
core/database/src/main/kotlin/com/opennet/weatherforecast/core/database/
├── WeatherDatabase.kt
├── entity/
│   ├── CityEntity.kt
│   ├── CurrentWeatherEntity.kt
│   ├── DailyForecastEntity.kt
│   └── HourlyForecastEntity.kt
├── dao/
│   ├── CityDao.kt
│   └── WeatherDao.kt
├── converter/
│   └── Converters.kt
├── mapper/
│   ├── CityEntityMapper.kt
│   └── WeatherEntityMapper.kt
├── datasource/
│   ├── CityLocalDataSource.kt
│   └── WeatherLocalDataSource.kt
└── di/
    └── DatabaseModule.kt
```

### Contents highlights

**`entity/CityEntity.kt`**:
```kotlin
@Entity(tableName = "cities")
internal data class CityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean,
    val addedAt: Long,  // epoch millis
)
```

**`entity/CurrentWeatherEntity.kt`**:
```kotlin
@Entity(
    tableName = "current_weather",
    foreignKeys = [ForeignKey(
        entity = CityEntity::class,
        parentColumns = ["id"],
        childColumns = ["cityId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class CurrentWeatherEntity(
    @PrimaryKey val cityId: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val conditionCode: String,      // WeatherCondition.name
    val unit: String,               // TemperatureUnit.name
    val updatedAt: Long,
)
```

**`entity/DailyForecastEntity.kt`**:
```kotlin
@Entity(
    tableName = "daily_forecasts",
    primaryKeys = ["cityId", "date"],
    foreignKeys = [ForeignKey(
        entity = CityEntity::class,
        parentColumns = ["id"],
        childColumns = ["cityId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class DailyForecastEntity(
    val cityId: String,
    val date: String,              // ISO 8601 date
    val tempMin: Double,
    val tempMax: Double,
    val conditionCode: String,
    val sunriseEpoch: Long?,
    val sunsetEpoch: Long?,
    val precipitationProbability: Int?,
)
```

**`entity/HourlyForecastEntity.kt`**: reserved — include entity for schema forward-compat, no DAO methods yet.

```kotlin
@Entity(
    tableName = "hourly_forecasts",
    primaryKeys = ["cityId", "time"],
    foreignKeys = [ForeignKey(
        entity = CityEntity::class,
        parentColumns = ["id"],
        childColumns = ["cityId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class HourlyForecastEntity(
    val cityId: String,
    val time: Long,                // epoch millis
    val temperature: Double,
    val conditionCode: String,
)
```

**`dao/CityDao.kt`**:
```kotlin
@Dao
internal interface CityDao {
    @Query("SELECT * FROM cities ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<CityEntity>>

    @Query("SELECT * FROM cities WHERE id = :cityId")
    suspend fun getById(cityId: String): CityEntity?

    @Upsert
    suspend fun upsert(city: CityEntity)

    @Query("DELETE FROM cities WHERE id = :cityId")
    suspend fun deleteById(cityId: String)

    @Query("SELECT * FROM cities WHERE isCurrentLocation = 1 LIMIT 1")
    suspend fun getCurrentLocationCity(): CityEntity?
}
```

**`dao/WeatherDao.kt`**:
```kotlin
@Dao
internal interface WeatherDao {
    @Query("SELECT * FROM current_weather WHERE cityId = :cityId")
    fun observeCurrentWeather(cityId: String): Flow<CurrentWeatherEntity?>

    @Query("SELECT * FROM daily_forecasts WHERE cityId = :cityId ORDER BY date ASC")
    fun observeDailyForecasts(cityId: String): Flow<List<DailyForecastEntity>>

    @Upsert
    suspend fun upsertCurrentWeather(weather: CurrentWeatherEntity)

    @Upsert
    suspend fun upsertDailyForecasts(forecasts: List<DailyForecastEntity>)

    @Query("DELETE FROM daily_forecasts WHERE cityId = :cityId")
    suspend fun deleteDailyForecasts(cityId: String)

    @Transaction
    suspend fun upsertFullWeather(
        current: CurrentWeatherEntity,
        daily: List<DailyForecastEntity>,
    ) {
        upsertCurrentWeather(current)
        deleteDailyForecasts(current.cityId)
        upsertDailyForecasts(daily)
    }
}
```

**`converter/Converters.kt`**: Not strictly needed if all fields are primitives (`Long`, `String`, etc.). If adding non-primitive types later, add here.

**`WeatherDatabase.kt`**:
```kotlin
@Database(
    entities = [
        CityEntity::class,
        CurrentWeatherEntity::class,
        DailyForecastEntity::class,
        HourlyForecastEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class WeatherDatabase : RoomDatabase() {
    abstract fun cityDao(): CityDao
    abstract fun weatherDao(): WeatherDao
}
```

**`mapper/CityEntityMapper.kt`**: extension functions `City.toEntity()` and `CityEntity.toDomain()`.

**`mapper/WeatherEntityMapper.kt`**: similar for weather types. Converts domain `Weather` → `CurrentWeatherEntity` + `List<DailyForecastEntity>` pair.

**`datasource/WeatherLocalDataSource.kt`**:
```kotlin
internal class WeatherLocalDataSource @Inject constructor(
    private val weatherDao: WeatherDao,
) {
    fun observeWeather(cityId: String, unit: TemperatureUnit): Flow<Weather?> =
        combine(
            weatherDao.observeCurrentWeather(cityId),
            weatherDao.observeDailyForecasts(cityId),
        ) { current, daily ->
            current?.toDomain(daily = daily.map { it.toDomain() }, unit = unit)
        }

    suspend fun upsertWeather(weather: Weather): Result<Unit, AppError> = try {
        weatherDao.upsertFullWeather(
            current = weather.toCurrentEntity(),
            daily = weather.daily.map { it.toEntity(weather.cityId) },
        )
        Result.Success(Unit)
    } catch (e: Throwable) {
        Result.Failure(AppError.DatabaseError)
    }
}
```

**`datasource/CityLocalDataSource.kt`**: similar, wraps `CityDao`.

**`di/DatabaseModule.kt`**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeatherDatabase =
        Room.databaseBuilder(
            context,
            WeatherDatabase::class.java,
            "weather.db",
        ).build()

    @Provides
    fun provideCityDao(db: WeatherDatabase): CityDao = db.cityDao()

    @Provides
    fun provideWeatherDao(db: WeatherDatabase): WeatherDao = db.weatherDao()
}
```

### Module Gradle config

```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.weatherforecast.core.database"

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
```

### Schema export

The `schemas/` directory will be generated by Room on first build. Commit it to track DB schema evolution.

Add to `.gitignore` **exceptions** if needed — don't ignore `schemas/`.

---

## Module 6: `:core:datastore`

DataStore Preferences for user settings.

### Files to create

```
core/datastore/src/main/kotlin/com/opennet/weatherforecast/core/datastore/
├── UserPreferencesDataSource.kt
├── key/
│   └── PreferencesKeys.kt
└── di/
    └── DataStoreModule.kt
```

### Contents

**`key/PreferencesKeys.kt`**:
```kotlin
internal object PreferencesKeys {
    val SELECTED_CITY_ID = stringPreferencesKey("selected_city_id")
    val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
}
```

**`UserPreferencesDataSource.kt`**:
```kotlin
internal class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val selectedCityId: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.SELECTED_CITY_ID] }

    val temperatureUnit: Flow<TemperatureUnit> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            it[PreferencesKeys.TEMPERATURE_UNIT]
                ?.let { name -> runCatching { TemperatureUnit.valueOf(name) }.getOrNull() }
                ?: TemperatureUnit.CELSIUS
        }

    suspend fun setSelectedCityId(cityId: String) {
        dataStore.edit { it[PreferencesKeys.SELECTED_CITY_ID] = cityId }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[PreferencesKeys.TEMPERATURE_UNIT] = unit.name }
    }
}
```

**`di/DataStoreModule.kt`**:
```kotlin
private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
)

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.userPreferencesDataStore
}
```

### Module Gradle config

```kotlin
plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
}

android {
    namespace = "com.example.weatherforecast.core.datastore"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(libs.datastore.preferences)
}
```

---

## Final Verification

After all six modules are implemented, run in order:

### 1. Clean build

```bash
./gradlew clean build
```

**Expected:** BUILD SUCCESSFUL with no errors and zero new warnings.

### 2. Independent module builds

Verify each module can build in isolation (catches stale dependencies):

```bash
./gradlew :core:model:build
./gradlew :core:common:build
./gradlew :core:designsystem:build
./gradlew :core:network:build
./gradlew :core:database:build
./gradlew :core:datastore:build
```

Each should succeed independently.

### 3. App still launches

```bash
./gradlew :app:installDebug
```

Open on emulator — Hello Compose should still appear. No new UI. No crash.

### 4. Verification checks

```bash
# No hardcoded Dispatchers.IO — should return empty
grep -rn "Dispatchers\.IO\|Dispatchers\.Default\|Dispatchers\.Main" --include="*.kt" core/

# No kotlin.Result usage — should return empty
grep -rn "import kotlin\.Result" --include="*.kt" core/

# No direct imports across forbidden boundaries (no feature imports here, but verify core:model has no Android imports)
grep -rn "import android\." --include="*.kt" core/model/
grep -rn "import androidx\." --include="*.kt" core/model/
```

All four greps should return nothing. If any returns content, investigate.

### 5. Room schema

Verify `core/database/schemas/com.example.weatherforecast.core.database.WeatherDatabase/1.json` exists after build.

### 6. Hilt sanity check (not exhaustive; full DI verified in PR 03)

```bash
./gradlew :app:kspDebugKotlin
```

Should complete without errors. Hilt's errors sometimes surface only in dependent modules — PR 03 is where the full graph gets tested.

---

## Commit Strategy

Suggested commit sequence (one per module, in dependency order):

```
feat: add core:model with domain data classes
feat: add core:common with Result, AppError, and Dispatcher qualifier
feat: add core:designsystem with theme and shared Composables
feat: add core:network with Open-Meteo API and Retrofit setup
feat: add core:database with Room schema and DAOs
feat: add core:datastore with user preferences
```

If Claude CLI generates everything in one session, break the final commits into these six for clean history. Don't merge all into a single commit.

---

## Going to Next PR

After DoD is fully checked and PR 02 merged:

1. Update `CLAUDE.md`:
   - PR 02 status: ⏳ → ✅
   - PR 03 status: ⏳ → 🚧
   - "Current PR" block updated

2. Produce `docs/prs/PR03_WEATHER_VERTICAL.md` (author-produced, not Claude CLI).

3. Retrospective note (at the bottom of this file, optional):
   - How long did this actually take?
   - Any convention plugin gap discovered?
   - Any module requiring deviation from `MODULE_STRUCTURE.md`?

---

## Claude CLI: How to Work on This PR

1. **Read the full spec top-to-bottom before generating any code.** Don't generate and reference in parallel.

2. **Read the four reference docs** listed at the top (MODULE_STRUCTURE, CODING_CONVENTIONS, ERROR_HANDLING, ARCHITECTURE).

3. **Implement modules in dependency order:** model → common → designsystem → network → database → datastore.

4. **Use `internal` visibility liberally.** Implementation details (DTOs, entities, data sources, DI modules) should be `internal`. Domain types (`City`, `Weather`) and Hilt qualifiers are `public`.

5. **One commit per module.** Don't squash all six into one commit.

6. **Match existing patterns from PR 01.** Look at how `:app`'s existing build.gradle.kts uses the version catalog and convention plugins. Apply the same style.

7. **If a detail is missing from this spec**, check `docs/MODULE_STRUCTURE.md` first; if still unclear, ask before improvising.

8. **Do not implement:**
   - Repository implementations (PR 03)
   - Repository interfaces (PR 03, but will draft in `:core:domain` — deferred)
   - Any Location module code (PR 05)
   - Any ViewModel, Composable screen, or navigation code (PR 03+)

9. **Do not add tests** unless a specific behavior (e.g., `apiCall { }` exception mapping) is trivially testable with a few lines. PR 07 is the test catch-all.

10. **Run the final verification yourself** (§ "Final Verification") before telling the user you're done.

---

## Post-PR Retrospective (fill after merge)

- Total time taken:
- Biggest friction point:
- Any deviation from this spec, and why:
- Anything to change in PR 03 spec based on this experience:
