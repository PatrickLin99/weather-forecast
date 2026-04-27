package com.example.weatherforecast.feature.weather

import app.cash.turbine.test
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.usecase.ObserveSelectedCityUseCase
import com.example.weatherforecast.core.domain.usecase.ObserveSelectedCityWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.RefreshWeatherUseCase
import com.example.weatherforecast.core.domain.usecase.ResolveInitialCityUseCase
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.DailyForecast
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import com.example.weatherforecast.core.model.WeatherCondition
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val resolveInitialCity = mockk<ResolveInitialCityUseCase>()
    private val observeSelectedCityWeather = mockk<ObserveSelectedCityWeatherUseCase>()
    private val observeSelectedCity = mockk<ObserveSelectedCityUseCase>()
    private val refreshWeather = mockk<RefreshWeatherUseCase>()
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)

    private val weatherFlow = MutableSharedFlow<Pair<City, Weather?>>(replay = 1)
    private val cityFlow = MutableSharedFlow<City>(replay = 1)

    @Before
    fun setUp() {
        every { observeSelectedCityWeather() } returns weatherFlow
        every { observeSelectedCity() } returns cityFlow
        every { userPreferencesRepository.temperatureUnit } returns flowOf(TemperatureUnit.CELSIUS)
        coEvery { resolveInitialCity(any()) } returns testCity()
        coEvery { refreshWeather(any()) } returns Result.Success(Unit)
    }

    private fun createViewModel() = WeatherViewModel(
        resolveInitialCity = resolveInitialCity,
        observeSelectedCityWeather = observeSelectedCityWeather,
        observeSelectedCity = observeSelectedCity,
        refreshWeather = refreshWeather,
        userPreferencesRepository = userPreferencesRepository,
    )

    @Test
    fun `initial state is Loading`() = runTest {
        val vm = createViewModel()
        assertTrue(vm.uiState.value is WeatherUiState.Loading)
    }

    @Test
    fun `weather non-null emits Success`() = runTest {
        val vm = createViewModel()
        val city = testCity()
        val weather = testWeather()

        vm.uiState.test {
            awaitItem() // Loading or initial
            weatherFlow.emit(city to weather)
            val success = awaitItem()
            assertTrue(success is WeatherUiState.Success)
            val s = success as WeatherUiState.Success
            assertTrue(s.weather == weather)
            assertTrue(s.city == city)
            assertTrue(!s.isStale)
            cancel()
        }
    }

    @Test
    fun `weather null and no error keeps uiState Loading`() = runTest {
        val vm = createViewModel()
        val city = testCity()

        vm.uiState.test {
            awaitItem() // initial Loading
            weatherFlow.emit(city to null)
            // StateFlow deduplicates: combine re-emits Loading, which equals the current
            // StateFlow value, so no new emission is delivered to subscribers.
            expectNoEvents()
            assertTrue(vm.uiState.value is WeatherUiState.Loading)
            cancel()
        }
    }

    @Test
    fun `refresh failure with cached weather shows Success with isStale true`() = runTest {
        val vm = createViewModel()
        val city = testCity()
        val weather = testWeather()

        // Start with successful data
        weatherFlow.emit(city to weather)

        // Now simulate a failed refresh by triggering onRefresh
        coEvery { refreshWeather(any()) } returns Result.Failure(AppError.NoNetwork)

        vm.uiState.test {
            awaitItem() // consume current Success
            vm.onRefresh()
            val state = awaitItem()
            assertTrue(state is WeatherUiState.Success)
            val s = state as WeatherUiState.Success
            assertTrue(s.isStale)
            assertTrue(s.transientError == AppError.NoNetwork)
            cancel()
        }
    }

    @Test
    fun `isRefreshing is false after onRefresh completes`() = runTest {
        val vm = createViewModel()
        weatherFlow.emit(testCity() to testWeather())

        vm.onRefresh()
        // runTest drains all coroutines; isRefreshing must be false when done
        assertTrue(!vm.isRefreshing.value)
    }
}

private fun testCity() = City(
    id = "taipei",
    name = "Taipei",
    country = "Taiwan",
    latitude = 25.0,
    longitude = 121.5,
)

private fun testWeather() = Weather(
    cityId = "taipei",
    temperature = 25.0,
    feelsLike = 24.0,
    humidity = 60,
    windSpeed = 5.0,
    condition = WeatherCondition.CLEAR,
    unit = TemperatureUnit.CELSIUS,
    updatedAt = Instant.fromEpochSeconds(0),
    daily = emptyList<DailyForecast>(),
)