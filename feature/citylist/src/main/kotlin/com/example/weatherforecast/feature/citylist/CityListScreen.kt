package com.example.weatherforecast.feature.citylist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherforecast.core.designsystem.component.ErrorState
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.feature.citylist.component.CitySearchBar
import com.example.weatherforecast.feature.citylist.component.EmptySavedListHint
import com.example.weatherforecast.feature.citylist.component.SavedCityItem
import com.example.weatherforecast.feature.citylist.component.SearchResultItem

@Composable
fun CityListScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CityListViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CityListContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onCityTapped = { city -> viewModel.onCityTapped(city, onNavigateBack) },
        onDeleteCity = viewModel::onDeleteCity,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityListContent(
    uiState: CityListUiState,
    onQueryChanged: (String) -> Unit,
    onCityTapped: (City) -> Unit,
    onDeleteCity: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.citylist_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.citylist_action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CitySearchBar(
                query = uiState.query,
                onQueryChanged = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            when (val s = uiState.searchState) {
                SearchState.Idle -> SavedCitiesList(
                    cities = uiState.savedCities,
                    selectedCityId = uiState.selectedCityId,
                    onTap = onCityTapped,
                    onDelete = onDeleteCity,
                )
                SearchState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is SearchState.Results -> SearchResultsList(
                    results = s.cities,
                    onTap = onCityTapped,
                )
                is SearchState.Error -> ErrorState(
                    message = stringResource(R.string.citylist_search_failed),
                    onRetry = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SavedCitiesList(
    cities: List<City>,
    selectedCityId: String?,
    onTap: (City) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (cities.isEmpty()) {
        EmptySavedListHint(modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(cities, key = { it.id }) { city ->
            SavedCityItem(
                city = city,
                isSelected = city.id == selectedCityId,
                onTap = { onTap(city) },
                onDelete = { onDelete(city.id) },
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<City>,
    onTap: (City) -> Unit,
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.citylist_search_no_matches))
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { "search-${it.id}" }) { city ->
            SearchResultItem(
                city = city,
                onTap = { onTap(city) },
            )
        }
    }
}