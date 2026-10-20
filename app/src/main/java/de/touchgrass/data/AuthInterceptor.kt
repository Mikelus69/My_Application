package de.touchgrass.data

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val session: AuthSession) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.currentToken()
        val request = chain.request().newBuilder()
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        val response = chain.proceed(request)
        if (response.code == 401 && token != null) session.onUnauthorized()
        return response
    }
}
