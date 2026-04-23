package com.example.weatherforecast.core.common.result

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Failure -> this
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> =
    when (this) {
        is Result.Success -> transform(data)
        is Result.Failure -> this
    }

inline fun <T, E> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> = apply {
    if (this is Result.Success) action(data)
}

inline fun <T, E> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> = apply {
    if (this is Result.Failure) action(error)
}

fun <T, E> Result<T, E>.getOrNull(): T? = (this as? Result.Success)?.data
fun <T, E> Result<T, E>.errorOrNull(): E? = (this as? Result.Failure)?.error