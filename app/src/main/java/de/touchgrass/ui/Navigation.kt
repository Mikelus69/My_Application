package de.touchgrass.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.touchgrass.AppContainer
import de.touchgrass.ui.status.ServerStatusCard
import kotlinx.serialization.Serializable

@Serializable object MapRoute
@Serializable object RecordRoute
@Serializable object AchievementsRoute
@Serializable object ProfileRoute

private data class Tab<T : Any>(val route: T, val emoji: String, val label: String)

private val tabs = listOf(
    Tab(MapRoute, "🗺️", "Karte"),
    Tab(RecordRoute, "➕", "Aktivität"),
    Tab(AchievementsRoute, "🏆", "Erfolge"),
    Tab(ProfileRoute, "👤", "Profil"),
)

@Composable
fun TouchGrassNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(tab.emoji) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MapRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<MapRoute> {
                PlaceholderScreen("Karte", "Die Karte kommt in M3.") {
                    ServerStatusCard(container.api)
                }
            }
            composable<RecordRoute> { PlaceholderScreen("Aktivität", "Formular kommt in M2, Timer in M3.") }
            composable<AchievementsRoute> { PlaceholderScreen("Erfolge", "Achievements, Wochenziel und Leaderboard kommen in M4.") }
            composable<ProfileRoute> { PlaceholderScreen("Profil", "Profil kommt in M1, Statistik in M5.") }
        }
    }
}
