package com.example.weatherforecast.core.location.geocoder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.weatherforecast.core.common.dispatcher.Dispatcher
import com.example.weatherforecast.core.common.dispatcher.DispatcherQualifier
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.City
import com.example.weatherforecast.core.model.Coordinates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class GeocoderWrapper @Inject internal constructor(
    @ApplicationContext private val context: Context,
    @DispatcherQualifier(Dispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Reverse-geocodes coordinates to a City with stable id "current_location".
     * Maps both an exception and an empty result to AppError.GeocoderFailed.
     */
    suspend fun reverseGeocode(coords: Coordinates): Result<City, AppError> =
        withContext(ioDispatcher) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    fetchAddressesAsync(geocoder, coords)
                } else {
                    fetchAddressesSync(geocoder, coords)
                }
                val first = addresses.firstOrNull()
                    ?: return@withContext Result.Failure(AppError.GeocoderFailed)
                Result.Success(first.toCity(coords))
            } catch (e: Exception) {
                Result.Failure(AppError.GeocoderFailed)
            }
        }

    @SuppressLint("NewApi")
    private suspend fun fetchAddressesAsync(
        geocoder: Geocoder,
        coords: Coordinates,
    ): List<Address> = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(
            coords.latitude,
            coords.longitude,
            1,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    cont.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    cont.resume(emptyList())
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun fetchAddressesSync(
        geocoder: Geocoder,
        coords: Coordinates,
    ): List<Address> =
        geocoder.getFromLocation(coords.latitude, coords.longitude, 1) ?: emptyList()
}

private fun Address.toCity(coords: Coordinates): City {
    val cityName = locality ?: subAdminArea ?: adminArea ?: "Unknown"
    val country = countryName ?: ""
    return City(
        id = "current_location",
        name = cityName,
        country = country,
        latitude = coords.latitude,
        longitude = coords.longitude,
        isCurrentLocation = true,
    )
}