package com.example.weatherforecast.core.data.repository

import app.cash.turbine.test
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.database.datasource.WeatherLocalDataSource
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.DailyForecast
import com.example.weatherforecast.core.model.TemperatureUnit
import com.example.weatherforecast.core.model.Weather
import com.example.weatherforecast.core.model.WeatherCondition
import com.example.weatherforecast.core.network.datasource.WeatherRemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeatherRepositoryImplTest {

    private val remote = mockk<WeatherRemoteDataSource>()
    private val local = mockk<WeatherLocalDataSource>(relaxed = true)
    private lateinit var repo: WeatherRepositoryImpl

    @Before
    fun setUp() {
        repo = WeatherRepositoryImpl(remote, local)
    }

    @Test
    fun `observeWeather delegates to local data source`() = runTest {
        val weather = testWeather("taipei")
        every { local.observeWeather("taipei", TemperatureUnit.CELSIUS) } returns flowOf(weather)

        repo.observeWeather("taipei", TemperatureUnit.CELSIUS).test {
            assertEquals(weather, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `observeWeather emits null when local has no cache`() = runTest {
        every { local.observeWeather("unknown", TemperatureUnit.CELSIUS) } returns flowOf(null)

        repo.observeWeather("unknown", TemperatureUnit.CELSIUS).test {
            assertEquals(null, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `refreshWeather fetches and upserts on network success`() = runTest {
        val city = testCity("taipei")
        val weather = testWeather("taipei")
        coEvery { remote.fetchWeather(city, TemperatureUnit.CELSIUS) } returns Result.Success(weather)
        coEvery { local.upsertWeather(weather) } returns Result.Success(Unit)

        val result = repo.refreshWeather(city, TemperatureUnit.CELSIUS)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { local.upsertWeather(weather) }
    }

    @Test
    fun `refreshWeather propagates network failure without writing to local`() = runTest {
        val city = testCity("taipei")
        coEvery { remote.fetchWeather(any(), any()) } returns Result.Failure(AppError.NoNetwork)

        val result = repo.refreshWeather(city, TemperatureUnit.CELSIUS)

        assertEquals(Result.Failure(AppError.NoNetwork), result)
        coVerify(exactly = 0) { local.upsertWeather(any()) }
    }

    @Test
    fun `refreshWeather propagates database failure`() = runTest {
        val city = testCity("taipei")
        val weather = testWeather("taipei")
        coEvery { remote.fetchWeather(city, TemperatureUnit.CELSIUS) } returns Result.Success(weather)
        coEvery { local.upsertWeather(weather) } returns Result.Failure(AppError.DatabaseError)

        val result = repo.refreshWeather(city, TemperatureUnit.CELSIUS)

        assertEquals(Result.Failure(AppError.DatabaseError), result)
    }
}

internal fun testCity(id: String = "taipei") = City(
    id = id,
    name = id.replaceFirstChar { it.uppercase() },
    country = "Test",
    latitude = 0.0,
    longitude = 0.0,
)

internal fun testWeather(cityId: String = "taipei") = Weather(
    cityId = cityId,
    temperature = 25.0,
    feelsLike = 24.0,
    humidity = 60,
    windSpeed = 5.0,
    condition = WeatherCondition.CLEAR,
    unit = TemperatureUnit.CELSIUS,
    updatedAt = Instant.fromEpochSeconds(0),
    daily = emptyList<DailyForecast>(),
)