package com.dfrobot.rehab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dfrobot.rehab.R
import com.dfrobot.rehab.ui.history.HistoryRoute
import com.dfrobot.rehab.ui.monitor.MonitorRoute
import com.dfrobot.rehab.ui.settings.SettingsRoute

object Routes {
    const val MONITOR = "monitor"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val titleRes: Int,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.MONITOR, R.string.nav_monitor, R.string.top_bar_monitor, Icons.Filled.FavoriteBorder),
    BottomDestination(Routes.HISTORY, R.string.nav_history, R.string.top_bar_history, Icons.Filled.DateRange),
    BottomDestination(Routes.SETTINGS, R.string.nav_settings, R.string.top_bar_settings, Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.MONITOR
    val currentTitle = bottomDestinations
        .firstOrNull { it.route == currentRoute }
        ?.titleRes
        ?: R.string.top_bar_monitor

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(currentTitle),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                bottomDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.MONITOR,
            ) {
                composable(Routes.MONITOR) { MonitorRoute() }
                composable(Routes.HISTORY) { HistoryRoute() }
                composable(Routes.SETTINGS) { SettingsRoute() }
            }
        }
    }
}
