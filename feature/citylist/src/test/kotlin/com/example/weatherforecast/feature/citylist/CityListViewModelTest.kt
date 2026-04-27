package com.example.weatherforecast.feature.citylist

import app.cash.turbine.test
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.domain.repository.CityRepository
import com.example.weatherforecast.core.domain.repository.UserPreferencesRepository
import com.example.weatherforecast.core.domain.usecase.DeleteCityUseCase
import com.example.weatherforecast.core.domain.usecase.SearchCitiesUseCase
import com.example.weatherforecast.core.domain.usecase.SelectCityUseCase
import com.example.weatherforecast.core.model.City
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val DEBOUNCE_MS = 300L

class CityListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cityRepository = mockk<CityRepository>()
    private val userPreferencesRepository = mockk<UserPreferencesRepository>()
    private val searchCities = mockk<SearchCitiesUseCase>()
    private val selectCity = mockk<SelectCityUseCase>(relaxed = true)
    private val deleteCity = mockk<DeleteCityUseCase>(relaxed = true)

    @Before
    fun setUp() {
        every { cityRepository.observeSavedCities() } returns flowOf(emptyList())
        every { userPreferencesRepository.selectedCityId } returns flowOf(null)
    }

    private fun createViewModel() = CityListViewModel(
        cityRepository = cityRepository,
        userPreferencesRepository = userPreferencesRepository,
        searchCities = searchCities,
        selectCity = selectCity,
        deleteCity = deleteCity,
    )

    @Test
    fun `initial searchState is Idle`() = runTest {
        val vm = createViewModel()
        assertTrue(vm.uiState.value.searchState is SearchState.Idle)
    }

    @Test
    fun `blank query keeps searchState Idle without hitting network`() = runTest {
        val vm = createViewModel()

        vm.uiState.test {
            awaitItem() // initial
            vm.onQueryChanged("  ")
            advanceTimeBy(DEBOUNCE_MS + 1)
            val state = awaitItem()
            assertTrue(state.searchState is SearchState.Idle)
            cancel()
        }
    }

    @Test
    fun `non-blank query transitions Loading then Results`() = runTest {
        val results = listOf(testCity("tokyo"))
        coEvery { searchCities("tokyo") } returns Result.Success(results)

        val vm = createViewModel()

        vm.uiState.test {
            awaitItem() // initial Idle

            vm.onQueryChanged("tokyo")
            advanceTimeBy(DEBOUNCE_MS + 1)

            // Loading
            val loading = awaitItem()
            assertTrue(loading.searchState is SearchState.Loading)

            // Results
            val done = awaitItem()
            val resultsState = done.searchState
            assertTrue(resultsState is SearchState.Results)
            assertEquals(results, (resultsState as SearchState.Results).cities)

            cancel()
        }
    }

    @Test
    fun `search failure transitions to Error state`() = runTest {
        coEvery { searchCities(any()) } returns Result.Failure(AppError.NoNetwork)

        val vm = createViewModel()

        vm.uiState.test {
            awaitItem() // initial

            vm.onQueryChanged("paris")
            advanceTimeBy(DEBOUNCE_MS + 1)

            awaitItem() // Loading
            val error = awaitItem()
            assertTrue(error.searchState is SearchState.Error)

            cancel()
        }
    }

    @Test
    fun `clearing query resets searchState to Idle`() = runTest {
        val results = listOf(testCity("london"))
        coEvery { searchCities("london") } returns Result.Success(results)

        val vm = createViewModel()

        vm.uiState.test {
            awaitItem() // initial Idle
            vm.onQueryChanged("london")
            advanceTimeBy(DEBOUNCE_MS + 1)
            awaitItem() // Loading
            awaitItem() // Results

            vm.onQueryChanged("")
            advanceTimeBy(DEBOUNCE_MS + 1)
            // _query changing to "" immediately causes an intermediate combine re-emit
            // (searchState still Results), then debounce fires and emits Idle
            val item = awaitItem()
            val cleared = if (item.searchState is SearchState.Idle) item else awaitItem()
            assertTrue(cleared.searchState is SearchState.Idle)

            cancel()
        }
    }

    @Test
    fun `saved cities are reflected in uiState`() = runTest {
        val taipei = testCity("taipei")
        every { cityRepository.observeSavedCities() } returns flowOf(listOf(taipei))

        val vm = createViewModel()

        // WhileSubscribed means the upstream only runs while there's a collector.
        // Assert via .test{} so the upstream is active and savedCities is populated.
        vm.uiState.test {
            val state = awaitItem()
            val savedCities = if (state.savedCities.isNotEmpty()) state.savedCities
                              else awaitItem().savedCities
            assertEquals(listOf(taipei), savedCities)
            cancel()
        }
    }
}

private fun testCity(id: String) = City(
    id = id,
    name = id.replaceFirstChar { it.uppercase() },
    country = "Test",
    latitude = 0.0,
    longitude = 0.0,
)