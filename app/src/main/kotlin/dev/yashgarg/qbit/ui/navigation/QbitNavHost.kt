package dev.yashgarg.qbit.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.yashgarg.qbit.ui.config.ConfigScreen
import dev.yashgarg.qbit.ui.home.HomeScreen
import dev.yashgarg.qbit.ui.logs.LogsScreen
import dev.yashgarg.qbit.ui.server.ServerScreen
import dev.yashgarg.qbit.ui.serverlist.ServerListScreen
import dev.yashgarg.qbit.ui.settings.SettingsScreen
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsScreen
import dev.yashgarg.qbit.ui.version.VersionScreen

/**
 * The app's Compose-navigation host. Every destination is a native composable; still-decoupled
 * callers (e.g. MainActivity, DialogFragments) drive navigation via [AppNavigator] rather than
 * reaching this [NavController] directly.
 */
@Composable
fun QbitNavHost(
    appNavigator: AppNavigator,
    onExitDoubleBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    androidx.compose.runtime.LaunchedEffect(navController) {
        appNavigator.commands.collect { command -> navController.execute(command) }
    }

    // Root back handling: NavHost pops the back stack automatically; only at an effective root do
    // we
    // take over for the "press back twice to exit" behavior. The root is HOME on first run, but
    // once
    // a server is configured the Server list becomes the root (OpenServerAsRoot pops HOME), so both
    // count. Any other screen falls through to the NavHost, which pops back toward the Server list.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val atRoot = route == Routes.HOME || route == Routes.SERVER
    BackHandler(enabled = atRoot) { onExitDoubleBack() }

    // The NavHost default is a ~700ms crossfade, which feels sluggish; use a quick fade.
    val fadeSpec = tween<Float>(durationMillis = 180)
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(fadeSpec) },
        exitTransition = { fadeOut(fadeSpec) },
        popEnterTransition = { fadeIn(fadeSpec) },
        popExitTransition = { fadeOut(fadeSpec) },
    ) {
        composable(Routes.HOME) { HomeScreen(appNavigator = appNavigator) }
        composable(Routes.SERVERS) { ServerListScreen(appNavigator = appNavigator) }
        composable(Routes.SERVER) { ServerScreen(appNavigator = appNavigator) }
        composable(Routes.SETTINGS) { SettingsScreen(appNavigator = appNavigator) }
        composable(Routes.VERSION) { VersionScreen(appNavigator = appNavigator) }
        composable(Routes.LOGS) { LogsScreen(appNavigator = appNavigator) }

        composable(
            route = Routes.CONFIG_PATTERN,
            arguments =
                listOf(
                    navArgument(Routes.ARG_SERVER_ID) {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                ),
        ) {
            // serverId reaches ConfigViewModel via the destination's SavedStateHandle.
            ConfigScreen(appNavigator = appNavigator)
        }

        composable(
            route = Routes.TORRENT_DETAILS_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_TORRENT_HASH) { type = NavType.StringType }),
        ) {
            // torrentHash reaches TorrentDetailsViewModel via the destination's SavedStateHandle.
            TorrentDetailsScreen(appNavigator = appNavigator)
        }
    }
}

private fun NavController.execute(command: NavCommand) {
    when (command) {
        is NavCommand.OpenConfig -> navigate(Routes.config(command.serverId))
        NavCommand.OpenServerAsRoot ->
            navigate(Routes.SERVER) {
                popUpTo(Routes.HOME) { inclusive = true }
                launchSingleTop = true
            }
        NavCommand.PopToServer -> {
            if (!popBackStack(Routes.SERVER, inclusive = false)) navigate(Routes.SERVER)
        }
        is NavCommand.OpenTorrent -> navigate(Routes.torrentDetails(command.hash))
        NavCommand.OpenSettings -> navigate(Routes.SETTINGS)
        NavCommand.OpenServerList -> navigate(Routes.SERVERS)
        NavCommand.OpenVersion -> navigate(Routes.VERSION)
        NavCommand.OpenLogs -> navigate(Routes.LOGS)
        NavCommand.Back -> {
            if (!navigateUp()) popBackStack()
        }
    }
}
