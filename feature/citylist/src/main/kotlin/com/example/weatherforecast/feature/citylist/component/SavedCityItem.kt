package com.example.weatherforecast.feature.citylist.component

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.weatherforecast.core.model.City

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedCityItem(
    city: City,
    isSelected: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable { onTap() },
        headlineContent = { Text(city.name) },
        supportingContent = { Text(city.country) },
        leadingContent = {
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected")
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        },
    )
}