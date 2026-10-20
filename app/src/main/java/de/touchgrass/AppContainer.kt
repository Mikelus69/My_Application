package de.touchgrass

import android.content.Context
import de.touchgrass.data.AuthInterceptor
import de.touchgrass.data.AuthSession
import de.touchgrass.data.DataStoreTokenStore
import de.touchgrass.data.SportApi
import de.touchgrass.data.authDataStore
import de.touchgrass.data.createSportApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class AppContainer(context: Context, baseUrl: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val session = AuthSession(DataStoreTokenStore(context.authDataStore), scope)

    val api: SportApi = createSportApi(
        baseUrl,
        OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build(),
    )
}
