package de.touchgrass

import android.content.Context
import de.touchgrass.data.AuthInterceptor
import de.touchgrass.data.AuthSession
import de.touchgrass.data.DataStoreTimerStore
import de.touchgrass.data.DataStoreTokenStore
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerStore
import de.touchgrass.data.authDataStore
import de.touchgrass.data.createSportApi
import de.touchgrass.data.httpClient
import de.touchgrass.data.timerDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context, baseUrl: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val session = AuthSession(DataStoreTokenStore(context.authDataStore), scope)

    val timerStore: TimerStore = DataStoreTimerStore(context.timerDataStore)

    val api: SportApi = createSportApi(baseUrl, httpClient(AuthInterceptor(session)))
}
