package dev.yashgarg.qbit.ui.navigation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Bridges navigation between the Compose `NavHost` and screens that are still hosted as XML
 * Fragments (via `AndroidFragment`). Those fragments can't reach the Compose `NavController`, so
 * they emit a [NavCommand] here and [dev.yashgarg.qbit.ui.navigation.QbitNavHost] executes it.
 *
 * A `@Singleton` (rather than a ViewModel) so the same instance is shared by the composable host
 * and by injected Fragments/MainActivity without ViewModel-owner matching.
 *
 * Backed by a [Channel] (not a SharedFlow): the very first command — MainActivity routing an
 * existing server to the list on launch — is emitted before the NavHost starts collecting, and a
 * Channel buffers it until then. A replay-0 SharedFlow would drop it (leaving Home spinning).
 */
@Singleton
class AppNavigator @Inject constructor() {
    private val _commands = Channel<NavCommand>(Channel.BUFFERED)
    val commands: Flow<NavCommand> = _commands.receiveAsFlow()

    fun navigate(command: NavCommand) {
        _commands.trySend(command)
    }
}

sealed interface NavCommand {
    /** Open the add/edit server form. serverId >= 0 edits that server; -1 adds a new one. */
    data class OpenConfig(val serverId: Int = -1) : NavCommand

    /**
     * Go to the main torrent list, making it the back-stack root (used on first-run/config-save).
     */
    data object OpenServerAsRoot : NavCommand

    /** Bring the torrent list forward without clearing it (e.g. an incoming torrent intent). */
    data object PopToServer : NavCommand

    data class OpenTorrent(val hash: String) : NavCommand

    data object OpenSettings : NavCommand

    data object OpenServerList : NavCommand

    data object OpenVersion : NavCommand

    /** Up / system-back within the Compose back stack. */
    data object Back : NavCommand
}
