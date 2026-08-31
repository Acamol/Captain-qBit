package dev.yashgarg.qbit.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.yashgarg.qbit.common.R as CommonR

/**
 * A resolver for [Throwable.friendlyMessage], for call sites that need it inside a non-composable
 * callback (a Toast/Snackbar triggered by a one-shot event) rather than directly in composition.
 * Every possible message is resolved upfront via [stringResource] instead of querying
 * `LocalContext.current` inside the callback.
 */
@Composable
fun rememberFriendlyMessageResolver(): (Int) -> String {
    val messages =
        mapOf(
            CommonR.string.error_connection_failed to
                stringResource(CommonR.string.error_connection_failed),
            CommonR.string.error_connection_timed_out to
                stringResource(CommonR.string.error_connection_timed_out),
            CommonR.string.error_server_unreachable to
                stringResource(CommonR.string.error_server_unreachable),
            CommonR.string.error_host_not_found to
                stringResource(CommonR.string.error_host_not_found),
            CommonR.string.error_ssl to stringResource(CommonR.string.error_ssl),
            CommonR.string.error_ssl_untrusted_certificate to
                stringResource(CommonR.string.error_ssl_untrusted_certificate),
            CommonR.string.error_torrent_already_exists to
                stringResource(CommonR.string.error_torrent_already_exists),
            CommonR.string.error_authentication_failed to
                stringResource(CommonR.string.error_authentication_failed),
        )
    return { id -> messages[id].orEmpty() }
}
