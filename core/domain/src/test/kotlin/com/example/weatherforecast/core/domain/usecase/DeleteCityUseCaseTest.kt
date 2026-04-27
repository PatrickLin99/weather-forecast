package com.example.weatherforecast.core.domain.usecase

import com.example.weatherforecast.core.common.constant.DefaultCity
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DeleteCityUseCaseTest {

    private val cityRepo = mockk<CityRepository>(relaxed = true)
    private val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
    private lateinit var useCase: DeleteCityUseCase

    @Before
    fun setUp() {
        useCase = DeleteCityUseCase(cityRepo, prefsRepo)
    }

    @Test
    fun `deleting non-selected city does not change selectedCityId`() = runTest {
        every { prefsRepo.selectedCityId } returns flowOf("taipei")
        coEvery { cityRepo.deleteCity("tokyo") } returns Result.Success(Unit)

        useCase("tokyo")

        coVerify(exactly = 0) { prefsRepo.setSelectedCityId(any()) }
        coVerify(exactly = 0) { cityRepo.saveCity(DefaultCity.TAIPEI) }
    }

    @Test
    fun `deleting selected city saves DefaultCity and updates selectedCityId`() = runTest {
        every { prefsRepo.selectedCityId } returns flowOf("tokyo")
        coEvery { cityRepo.deleteCity("tokyo") } returns Result.Success(Unit)
        coEvery { cityRepo.saveCity(DefaultCity.TAIPEI) } returns Result.Success(Unit)

        useCase("tokyo")

        coVerify { cityRepo.saveCity(DefaultCity.TAIPEI) }
        coVerify { prefsRepo.setSelectedCityId(DefaultCity.TAIPEI.id) }
    }

    @Test
    fun `delete failure propagates without touching selectedCityId`() = runTest {
        every { prefsRepo.selectedCityId } returns flowOf("tokyo")
        coEvery { cityRepo.deleteCity("tokyo") } returns Result.Failure(AppError.DatabaseError)

        val result = useCase("tokyo")

        assertEquals(Result.Failure(AppError.DatabaseError), result)
        coVerify(exactly = 0) { prefsRepo.setSelectedCityId(any()) }
    }

    @Test
    fun `deleting with null selectedCityId does not change selectedCityId`() = runTest {
        every { prefsRepo.selectedCityId } returns flowOf(null)
        coEvery { cityRepo.deleteCity("taipei") } returns Result.Success(Unit)

        useCase("taipei")

        coVerify(exactly = 0) { prefsRepo.setSelectedCityId(any()) }
    }
}