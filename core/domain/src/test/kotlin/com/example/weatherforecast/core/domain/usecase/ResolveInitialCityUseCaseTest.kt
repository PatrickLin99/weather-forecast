package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.LocationRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ResolveInitialCityUseCaseTest {

    private val locationRepo = mockk<LocationRepository>()
    private val cityRepo = mockk<CityRepository>(relaxed = true)
    private val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
    private lateinit var useCase: ResolveInitialCityUseCase

    @Before
    fun setUp() {
        useCase = ResolveInitialCityUseCase(locationRepo, cityRepo, prefsRepo)
    }

    @Test
    fun `useLocation true and location succeeds returns location city`() = runTest {
        val locationCity = testCity(id = "current_location", isCurrentLocation = true)
        coEvery { locationRepo.getCurrentLocationCity() } returns Result.Success(locationCity)
        every { prefsRepo.selectedCityId } returns flowOf(null)

        val result = useCase(useLocation = true)

        assertEquals(locationCity, result)
        coVerify { cityRepo.saveCity(locationCity) }
        coVerify { prefsRepo.setSelectedCityId(locationCity.id) }
    }

    @Test
    fun `useLocation false skips location and uses last-selected city`() = runTest {
        val taipei = testCity("taipei")
        every { prefsRepo.selectedCityId } returns flowOf("taipei")
        coEvery { cityRepo.getCityById("taipei") } returns taipei

        val result = useCase(useLocation = false)

        assertEquals(taipei, result)
        coVerify(exactly = 0) { locationRepo.getCurrentLocationCity() }
    }

    @Test
    fun `no last-selected city falls back to DefaultCity TAIPEI`() = runTest {
        every { prefsRepo.selectedCityId } returns flowOf(null)

        val result = useCase(useLocation = false)

        assertEquals(DefaultCity.TAIPEI, result)
        coVerify { cityRepo.saveCity(DefaultCity.TAIPEI) }
        coVerify { prefsRepo.setSelectedCityId(DefaultCity.TAIPEI.id) }
    }

    @Test
    fun `useLocation true but location fails falls through to last-selected`() = runTest {
        coEvery { locationRepo.getCurrentLocationCity() } returns Result.Failure(AppError.LocationTimeout)
        val tokyo = testCity("tokyo")
        every { prefsRepo.selectedCityId } returns flowOf("tokyo")
        coEvery { cityRepo.getCityById("tokyo") } returns tokyo

        val result = useCase(useLocation = true)

        assertEquals(tokyo, result)
    }

    @Test
    fun `last-selected id exists in prefs but not in Room falls back to DefaultCity`() = runTest {
        every { prefsRepo.selectedCityId } returns flowOf("deleted_city")
        coEvery { cityRepo.getCityById("deleted_city") } returns null

        val result = useCase(useLocation = false)

        assertEquals(DefaultCity.TAIPEI, result)
        coVerify { cityRepo.saveCity(DefaultCity.TAIPEI) }
    }
}

private fun testCity(id: String, isCurrentLocation: Boolean = false) = City(
    id = id,
    name = id.replaceFirstChar { it.uppercase() },
    country = "Test",
    latitude = 0.0,
    longitude = 0.0,
    isCurrentLocation = isCurrentLocation,
)