package com.example.weatherforecast.feature.weather

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.weatherforecast.core.common.error.AppError

@Composable
internal fun AppError.toUserMessage(): String = when (this) {
    AppError.NoNetwork -> stringResource(R.string.error_no_network)
    AppError.NetworkTimeout -> stringResource(R.string.error_network_timeout)
    is AppError.ServerError -> stringResource(R.string.error_server)
    AppError.UnknownNetworkError -> stringResource(R.string.error_unknown_network)
    AppError.DatabaseError -> stringResource(R.string.error_database)
    is AppError.DataParsingError -> stringResource(R.string.error_data_parsing)
    AppError.CityNotFound,
    AppError.GeocoderFailed,
    AppError.LocationDisabled,
    AppError.LocationPermissionDenied,
    AppError.LocationTimeout,
    AppError.LocationUnavailable,
    is AppError.Unexpected,
    -> stringResource(R.string.error_unexpected)
}