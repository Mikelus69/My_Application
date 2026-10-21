package de.touchgrass.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.touchgrass.AppContainer
import de.touchgrass.ui.activities.ActivityDetailScreen
import de.touchgrass.ui.activities.ActivityDetailViewModel
import de.touchgrass.ui.activities.HistoryScreen
import de.touchgrass.ui.activities.HistoryViewModel
import de.touchgrass.ui.map.MapScreen
import de.touchgrass.ui.map.MapViewModel
import de.touchgrass.ui.profile.ProfileScreen
import de.touchgrass.ui.profile.ProfileViewModel
import de.touchgrass.ui.profile.SettingsScreen
import de.touchgrass.ui.profile.SettingsViewModel
import de.touchgrass.ui.record.RecordScreen
import de.touchgrass.ui.record.RecordViewModel
import de.touchgrass.ui.record.TimerScreen
import de.touchgrass.ui.record.TimerTabLabel
import de.touchgrass.ui.record.TimerViewModel
import de.touchgrass.ui.status.ServerStatusCard
import kotlinx.serialization.Serializable

@Serializable object MapRoute
@Serializable object RecordGraph
@Serializable object TimerRoute
@Serializable data class RecordFormRoute(val fromTimer: Boolean)
@Serializable object AchievementsRoute
@Serializable object ProfileGraph
@Serializable object ProfileRoute
@Serializable object SettingsRoute
@Serializable object HistoryRoute
@Serializable data class ActivityDetailRoute(val id: Long)

private data class Tab<T : Any>(val route: T, val emoji: String, val label: String)

private val tabs = listOf(
    Tab(MapRoute, "🗺️", "Karte"),
    Tab(RecordGraph, "➕", "Aktivität"),
    Tab(AchievementsRoute, "🏆", "Erfolge"),
    Tab(ProfileGraph, "👤", "Profil"),
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
                        label = {
                            if (tab.route == RecordGraph) TimerTabLabel(container.timerStore, tab.label) else Text(tab.label)
                        },
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
                MapScreen(viewModel = viewModel { MapViewModel(container.api) })
            }
            navigation<RecordGraph>(startDestination = TimerRoute) {
                composable<TimerRoute> {
                    TimerScreen(
                        viewModel = viewModel { TimerViewModel(container.api, container.timerStore) },
                        onOpenForm = { navController.navigate(RecordFormRoute(fromTimer = true)) },
                        onRecordManually = { navController.navigate(RecordFormRoute(fromTimer = false)) },
                    )
                }
                composable<RecordFormRoute> { entry ->
                    val fromTimer = entry.toRoute<RecordFormRoute>().fromTimer
                    RecordScreen(
                        viewModel = viewModel { RecordViewModel(container.api, container.timerStore, fromTimer) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable<AchievementsRoute> { PlaceholderScreen("Erfolge", "Achievements, Wochenziel und Leaderboard kommen in M4.") }
            navigation<ProfileGraph>(startDestination = ProfileRoute) {
                composable<ProfileRoute> {
                    ProfileScreen(
                        viewModel = viewModel { ProfileViewModel(container.api) },
                        onOpenHistory = { navController.navigate(HistoryRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        extra = { ServerStatusCard(container.api) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        viewModel = viewModel { SettingsViewModel(container.api, container.session) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<HistoryRoute> {
                    HistoryScreen(
                        viewModel = viewModel { HistoryViewModel(container.api) },
                        onOpen = { id -> navController.navigate(ActivityDetailRoute(id)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<ActivityDetailRoute> { entry ->
                    val id = entry.toRoute<ActivityDetailRoute>().id
                    ActivityDetailScreen(
                        viewModel = viewModel { ActivityDetailViewModel(container.api, id) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
