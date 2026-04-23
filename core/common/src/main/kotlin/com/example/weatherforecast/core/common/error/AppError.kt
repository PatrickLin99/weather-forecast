package com.example.weatherforecast.core.common.error

sealed class AppError {

    // --- Network errors ---
    data object NoNetwork : AppError()
    data object NetworkTimeout : AppError()
    data class ServerError(val httpCode: Int) : AppError()
    data object UnknownNetworkError : AppError()

    // --- Location errors ---
    data object LocationPermissionDenied : AppError()
    data object LocationTimeout : AppError()
    data object LocationDisabled : AppError()
    data object LocationUnavailable : AppError()

    // --- Data / parsing errors ---
    data object CityNotFound : AppError()
    data object GeocoderFailed : AppError()
    data class DataParsingError(val detail: String) : AppError()

    // --- Database errors ---
    data object DatabaseError : AppError()

    // --- Fallback ---
    data class Unexpected(val cause: Throwable) : AppError()
}