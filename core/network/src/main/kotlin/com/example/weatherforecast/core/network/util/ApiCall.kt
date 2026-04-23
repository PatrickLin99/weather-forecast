package com.example.weatherforecast.core.network.util

import com.example.weatherforecast.core.common.error.AppError
import com.example.weatherforecast.core.common.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal suspend inline fun <T> apiCall(
    crossinline block: suspend () -> T,
): Result<T, AppError> =
    try {
        Result.Success(block())
    } catch (e: SocketTimeoutException) {
        Result.Failure(AppError.NetworkTimeout)
    } catch (e: UnknownHostException) {
        Result.Failure(AppError.NoNetwork)
    } catch (e: IOException) {
        Result.Failure(AppError.NoNetwork)
    } catch (e: HttpException) {
        Result.Failure(AppError.ServerError(httpCode = e.code()))
    } catch (e: SerializationException) {
        Result.Failure(AppError.DataParsingError(detail = e.message.orEmpty()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.Failure(AppError.UnknownNetworkError)
    }