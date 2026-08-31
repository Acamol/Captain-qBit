package dev.yashgarg.qbit.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.config.ConfigScreen
import dev.yashgarg.qbit.ui.home.HomeScreen
import dev.yashgarg.qbit.ui.logs.LogsScreen
import dev.yashgarg.qbit.ui.rss.RssArticlesScreen
import dev.yashgarg.qbit.ui.rss.RssRuleEditorScreen
import dev.yashgarg.qbit.ui.rss.RssScreen
import dev.yashgarg.qbit.ui.server.ServerScreen
import dev.yashgarg.qbit.ui.serverlist.ServerListScreen
import dev.yashgarg.qbit.ui.settings.SettingsScreen
import dev.yashgarg.qbit.ui.torrent.TorrentDetailsScreen
import dev.yashgarg.qbit.ui.version.VersionScreen

/**
 * A Material3 [Scaffold]/[androidx.compose.material3.ModalDrawerSheet] with no `topBar`/`bottomBar`
 * of its own defaults to reserving [WindowInsets.safeDrawing]'s edge insets anyway - a real problem
 * whenever a sibling or parent component (the outer [QbitNavHost] Scaffold's own bottomBar, or a
 * screen's own topBar) already reserves that same edge, since the space then gets double-counted.
 * Use this to opt out of the redundant side(s); see each call site for which edge(s) actually need
 * it - zeroing an edge nothing else protects (e.g. a drawer's top inset) reintroduces a real gap.
 */
val NoWindowInsets = WindowInsets(0, 0, 0, 0)

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
    // we take over for the "press back twice to exit" behavior. HOME is the root on first run;
    // once a server is configured, Server/RSS/Settings are the three top-level tabs
    // (OpenServerAsRoot pops HOME), so all four count. Any other screen falls through to the
    // NavHost, which pops back toward whichever tab pushed it.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val topLevelRoutes = setOf(Routes.SERVER, Routes.RSS, Routes.SETTINGS)
    val atRoot = route == Routes.HOME || route in topLevelRoutes
    BackHandler(enabled = atRoot) { onExitDoubleBack() }

    // The NavHost default is a ~700ms crossfade, which feels sluggish; use a quick fade.
    val fadeSpec = tween<Float>(durationMillis = 180)
    Scaffold(
        modifier = modifier,
        // No topBar here - each destination's own nested Scaffold already handles the status-bar
        // inset via its own topBar. See NoWindowInsets kdoc.
        contentWindowInsets = NoWindowInsets,
        bottomBar = {
            if (route in topLevelRoutes) QbitBottomNavigationBar(navController, route)
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
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
            composable(Routes.RSS) { RssScreen(appNavigator = appNavigator) }

            composable(
                route = Routes.RSS_ARTICLES_PATTERN,
                arguments =
                    listOf(navArgument(Routes.ARG_RSS_ITEM_PATH) { type = NavType.StringType }),
            ) {
                // itemPath reaches RssViewModel via the destination's SavedStateHandle.
                RssArticlesScreen(appNavigator = appNavigator)
            }

            composable(
                route = Routes.RSS_RULE_EDITOR_PATTERN,
                arguments =
                    listOf(
                        navArgument(Routes.ARG_RSS_RULE_NAME) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
            ) {
                // ruleName (null = new rule) reaches RssViewModel via the destination's
                // SavedStateHandle.
                RssRuleEditorScreen(appNavigator = appNavigator)
            }

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
                arguments =
                    listOf(navArgument(Routes.ARG_TORRENT_HASH) { type = NavType.StringType }),
            ) {
                // torrentHash reaches TorrentDetailsViewModel via the destination's
                // SavedStateHandle.
                TorrentDetailsScreen(appNavigator = appNavigator)
            }
        }
    }
}

private data class TopLevelDestination(val route: String, val label: Int, val icon: ImageVector)

private val TOP_LEVEL_DESTINATIONS =
    listOf(
        TopLevelDestination(
            Routes.SERVER,
            CommonR.string.torrents_title,
            Icons.AutoMirrored.Filled.List,
        ),
        TopLevelDestination(Routes.RSS, CommonR.string.rss_title, Icons.Filled.RssFeed),
        TopLevelDestination(Routes.SETTINGS, CommonR.string.settings_label, Icons.Filled.Settings),
    )

@Composable
private fun QbitBottomNavigationBar(navController: NavController, currentRoute: String?) {
    NavigationBar {
        TOP_LEVEL_DESTINATIONS.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(Routes.SERVER) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
            )
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
            // popBackStack(SERVER, inclusive = false) returns false both when SERVER isn't in the
            // back stack AND when we're already sitting on it (nothing above it to pop) — the
            // latter must not fall through to navigate(SERVER), which would push a redundant new
            // instance and wipe out any state (e.g. an add-torrent dialog just opened by this same
            // intent) that the current instance holds.
            if (currentDestination?.route != Routes.SERVER) {
                if (!popBackStack(Routes.SERVER, inclusive = false)) navigate(Routes.SERVER)
            }
        }
        is NavCommand.OpenTorrent -> navigate(Routes.torrentDetails(command.hash))
        NavCommand.OpenServerList -> navigate(Routes.SERVERS)
        NavCommand.OpenVersion -> navigate(Routes.VERSION)
        NavCommand.OpenLogs -> navigate(Routes.LOGS)
        is NavCommand.OpenRssArticles -> navigate(Routes.rssArticles(command.itemPath))
        is NavCommand.OpenRssRuleEditor -> navigate(Routes.rssRuleEditor(command.ruleName))
        NavCommand.Back -> {
            if (!navigateUp()) popBackStack()
        }
    }
}
