package com.example.weatherforecast.core.data.repository

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.database.datasource.CityLocalDataSource
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.network.datasource.CityRemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CityRepositoryImplTest {

    private val local = mockk<CityLocalDataSource>(relaxed = true)
    private val remote = mockk<CityRemoteDataSource>()
    private lateinit var repo: CityRepositoryImpl

    @Before
    fun setUp() {
        repo = CityRepositoryImpl(local, remote)
    }

    @Test
    fun `searchCities returns empty list without hitting network when query is blank`() = runTest {
        val result = repo.searchCities("")

        assertEquals(Result.Success(emptyList<City>()), result)
        coVerify(exactly = 0) { remote.searchCities(any()) }
    }

    @Test
    fun `searchCities returns empty list for whitespace-only query`() = runTest {
        val result = repo.searchCities("   ")

        assertEquals(Result.Success(emptyList<City>()), result)
        coVerify(exactly = 0) { remote.searchCities(any()) }
    }

    @Test
    fun `searchCities delegates to remote for non-blank query`() = runTest {
        val cities = listOf(testCity("tokyo"))
        coEvery { remote.searchCities("tokyo") } returns Result.Success(cities)

        val result = repo.searchCities("tokyo")

        assertEquals(Result.Success(cities), result)
        coVerify(exactly = 1) { remote.searchCities("tokyo") }
    }

    @Test
    fun `searchCities propagates remote failure`() = runTest {
        coEvery { remote.searchCities(any()) } returns Result.Failure(AppError.NoNetwork)

        val result = repo.searchCities("paris")

        assertEquals(Result.Failure(AppError.NoNetwork), result)
    }

    @Test
    fun `saveCity delegates to local upsert`() = runTest {
        val city = testCity("taipei")
        coEvery { local.upsertCity(city) } returns Result.Success(Unit)

        val result = repo.saveCity(city)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { local.upsertCity(city) }
    }

    @Test
    fun `deleteCity delegates to local delete`() = runTest {
        coEvery { local.deleteCity("taipei") } returns Result.Success(Unit)

        val result = repo.deleteCity("taipei")

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { local.deleteCity("taipei") }
    }
}