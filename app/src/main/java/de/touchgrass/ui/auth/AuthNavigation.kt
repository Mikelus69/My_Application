package de.touchgrass.ui.auth

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.touchgrass.AppContainer
import kotlinx.serialization.Serializable

@Serializable object LoginRoute
@Serializable object RegisterRoute

@Composable
fun AuthNavigation(container: AppContainer) {
    val navController = rememberNavController()

    Scaffold { innerPadding ->
        NavHost(navController, startDestination = LoginRoute, modifier = Modifier.padding(innerPadding)) {
            composable<LoginRoute> {
                LoginScreen(
                    viewModel = viewModel { LoginViewModel(container.api, container.session) },
                    onRegisterClick = { navController.navigate(RegisterRoute) },
                )
            }
            composable<RegisterRoute> {
                RegisterScreen(
                    viewModel = viewModel { RegisterViewModel(container.api, container.session) },
                    onLoginClick = { navController.popBackStack() },
                )
            }
        }
    }
}
