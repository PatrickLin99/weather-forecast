package com.example.weatherforecast.feature.citylist.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (city.isCurrentLocation) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.citylist_label_current_location),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(city.name)
            }
        },
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