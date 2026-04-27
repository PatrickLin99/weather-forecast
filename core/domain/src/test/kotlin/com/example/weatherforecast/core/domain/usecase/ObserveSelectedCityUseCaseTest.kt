package com.example.weatherforecast.core.domain.usecase

import app.cash.turbine.test
import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.model.City
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ObserveSelectedCityUseCaseTest {

    private val prefsRepo = mockk<UserPreferencesRepository>()
    private val cityRepo = mockk<CityRepository>()
    private lateinit var useCase: ObserveSelectedCityUseCase

    @Before
    fun setUp() {
        useCase = ObserveSelectedCityUseCase(prefsRepo, cityRepo)
    }

    @Test
    fun `emits matching city when selectedCityId resolves in saved list`() = runTest {
        val taipei = testCity("taipei")
        every { prefsRepo.selectedCityId } returns flowOf("taipei")
        every { cityRepo.observeSavedCities() } returns flowOf(listOf(taipei))

        useCase().test {
            assertEquals(taipei, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `falls back to DefaultCity TAIPEI when selected id not found in saved list`() = runTest {
        every { prefsRepo.selectedCityId } returns flowOf("ghost_city")
        every { cityRepo.observeSavedCities() } returns flowOf(listOf(testCity("taipei")))

        useCase().test {
            assertEquals(DefaultCity.TAIPEI, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `re-emits when saved cities list updates for same id (stale city fix)`() = runTest {
        val savedCities = MutableStateFlow(listOf(testCity("loc", name = "Old Name")))
        every { prefsRepo.selectedCityId } returns flowOf("loc")
        every { cityRepo.observeSavedCities() } returns savedCities

        useCase().test {
            assertEquals("Old Name", awaitItem().name)

            val updated = testCity("loc", name = "New Name")
            savedCities.value = listOf(updated)
            assertEquals("New Name", awaitItem().name)

            cancel()
        }
    }

    @Test
    fun `does not emit duplicate when same city emits twice`() = runTest {
        val taipei = testCity("taipei")
        val savedCities = MutableStateFlow(listOf(taipei))
        every { prefsRepo.selectedCityId } returns flowOf("taipei")
        every { cityRepo.observeSavedCities() } returns savedCities

        useCase().test {
            assertEquals(taipei, awaitItem())
            // Emitting the same list again should NOT produce a second emission (distinctUntilChanged)
            savedCities.value = listOf(taipei)
            expectNoEvents()
            cancel()
        }
    }
}

private fun testCity(id: String, name: String = id.replaceFirstChar { it.uppercase() }) = City(
    id = id,
    name = name,
    country = "Test",
    latitude = 0.0,
    longitude = 0.0,
)