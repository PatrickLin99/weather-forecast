package com.example.weatherforecast.core.location.provider

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import com.example.weatherforecast.core.common.dispatcher.Dispatcher
import com.example.weatherforecast.core.common.dispatcher.DispatcherQualifier
import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import com.example.weatherforecast.core.model.Coordinates
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCATION_TIMEOUT_MS = 5_000L

@Singleton
class LocationProvider @Inject internal constructor(
    @ApplicationContext private val context: Context,
    @DispatcherQualifier(Dispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Caller is responsible for verifying ACCESS_COARSE_LOCATION before calling.
     * Throws SecurityException if permission is missing — caller must guard.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<Coordinates, AppError> = withContext(ioDispatcher) {
        if (!isLocationEnabled()) {
            return@withContext Result.Failure(AppError.LocationDisabled)
        }

        val cts = CancellationTokenSource()
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            try {
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token,
                ).await()
            } catch (se: SecurityException) {
                null
            }
        }

        if (location == null) {
            cts.cancel()
            return@withContext Result.Failure(AppError.LocationTimeout)
        }

        Result.Success(Coordinates(location.latitude, location.longitude))
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}