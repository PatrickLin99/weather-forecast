package com.example.weatherforecast.feature.citylist.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.weatherforecast.feature.citylist.R

@Composable
internal fun EmptySavedListHint(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.LocationCity,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.citylist_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}