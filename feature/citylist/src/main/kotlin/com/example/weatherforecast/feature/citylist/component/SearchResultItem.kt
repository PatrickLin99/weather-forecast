package com.example.weatherforecast.feature.citylist.component

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.weatherforecast.core.model.City

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchResultItem(
    city: City,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable { onTap() },
        headlineContent = { Text(city.name) },
        supportingContent = { Text(city.country) },
    )
}