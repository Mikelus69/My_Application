package de.touchgrass.data

import de.touchgrass.data.dto.ErrorDto
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

class ApiException(val code: String, override val message: String, val field: String? = null) : Exception(message)

suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: HttpException) {
        throw e.toApiException()
    } catch (e: IOException) {
        throw ApiException("NETWORK", "Server nicht erreichbar")
    } catch (e: SerializationException) {
        throw ApiException("BAD_RESPONSE", "Unerwartete Antwort vom Server")
    }

private fun HttpException.toApiException(): ApiException {
    val error = response()?.errorBody()?.string()
        ?.let { body -> runCatching { apiJson.decodeFromString<ErrorDto>(body) }.getOrNull() }
    return if (error != null) {
        ApiException(error.error, error.message, error.field)
    } else {
        ApiException("HTTP_${code()}", "Serverfehler ${code()}")
    }
}
