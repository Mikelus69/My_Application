package de.touchgrass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.AppContainer
import de.touchgrass.data.AuthState
import de.touchgrass.ui.auth.AuthNavigation

@Composable
fun TouchGrassRoot(container: AppContainer) {
    val authState by container.session.state.collectAsStateWithLifecycle()

    when (authState) {
        AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AuthState.LoggedOut -> AuthNavigation(container)
        is AuthState.LoggedIn -> TouchGrassNavigation(container)
    }
}
