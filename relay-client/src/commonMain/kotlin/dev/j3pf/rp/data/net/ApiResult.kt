package dev.j3pf.rp.data.net

/** Resultado de una llamada HTTP al relay. */
sealed interface ApiResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : ApiResult<T>

    /** Respuesta no 2xx. `409` = `already_resolved`. */
    data class Http(
        val code: Int,
        val body: String,
    ) : ApiResult<Nothing> {
        val isConflict get() = code == 409
        val isUnauthorized get() = code == 401 || code == 403
    }

    data class Network(
        val error: Throwable,
    ) : ApiResult<Nothing>
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Ok)?.value

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Ok -> ApiResult.Ok(transform(value))
        is ApiResult.Http -> this
        is ApiResult.Network -> this
    }
