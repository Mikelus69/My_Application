package de.touchgrass

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import de.touchgrass.data.AuthInterceptor
import de.touchgrass.data.AuthSession
import de.touchgrass.data.AuthState
import de.touchgrass.data.DataStoreTimerStore
import de.touchgrass.data.DataStoreTokenStore
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerStore
import de.touchgrass.data.authDataStore
import de.touchgrass.data.createSportApi
import de.touchgrass.data.httpClient
import de.touchgrass.data.imageClient
import de.touchgrass.data.timerDataStore
import de.touchgrass.ui.photo.AndroidPhotoPreparer
import de.touchgrass.ui.photo.PhotoPreparer
import de.touchgrass.ui.photo.cameraDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppContainer(context: Context, baseUrl: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Nicht in filesDir: Fotos auf dem Handy sollen nicht in die Datensicherung von Android.
    private val photoDir = File(context.noBackupFilesDir, "photos")

    val session = AuthSession(DataStoreTokenStore(context.authDataStore), scope)

    val timerStore: TimerStore = DataStoreTimerStore(context.timerDataStore)

    val photoPreparer: PhotoPreparer = AndroidPhotoPreparer(context, photoDir)

    private val okHttpClient = httpClient(AuthInterceptor(session))

    val api: SportApi = createSportApi(baseUrl, okHttpClient)

    private val imageHttpClient = imageClient(okHttpClient)

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient })) }
        .build()

    init {
        // Coil zeigt Bilder aus dem Zwischenspeicher ohne Anfrage an den Server, also ohne Login. Deshalb leert jedes Abmelden ihn
        // und löscht die Fotos auf dem Handy; abgemeldet gibt es kein Formular und keine Details, die sie noch brauchen.
        // Das läuft auch bei jedem Start ohne Anmeldung, ein Fehler dabei darf die App also nicht beenden.
        scope.launch {
            session.state.filter { it == AuthState.LoggedOut }.collect {
                imageLoader.memoryCache?.clear()
                withContext(Dispatchers.IO) {
                    try {
                        imageLoader.diskCache?.clear()
                    } catch (e: Exception) {
                        Log.w("TouchGrass", "Bildzwischenspeicher nicht geleert", e)
                    }
                    photoDir.deleteRecursively()
                    cameraDir(context).deleteRecursively()
                }
            }
        }
    }
}
