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
import androidx.compose.ui.res.stringResource
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.feature.citylist.R

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
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.citylist_label_selected),
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.citylist_action_delete),
                )
            }
        },
    )
}