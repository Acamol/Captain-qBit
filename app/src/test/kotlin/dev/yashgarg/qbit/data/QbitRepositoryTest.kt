package dev.yashgarg.qbit.data

import dev.yashgarg.qbit.Constants
import dev.yashgarg.qbit.FakeClientManager
import dev.yashgarg.qbit.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QbitRepositoryTest {
    private lateinit var repository: QbitRepository
    private val clientManager = FakeClientManager()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        // These are integration tests: they hit a real qBittorrent server configured via the
        // base_url/password env vars. Skip (rather than fail) when that env isn't set, e.g. on CI.
        assumeTrue(
            "Set base_url and password env vars (and point them at a reachable qBittorrent server)" +
                " to run QbitRepository integration tests",
            !System.getenv("base_url").isNullOrBlank() &&
                !System.getenv("password").isNullOrBlank(),
        )
        repository = QbitRepository(clientManager)
    }

    @Test
    fun checkClientConnected() = runTest {
        assertTrue(repository.getVersion().isOk)
        assertTrue(repository.getApiVersion().isOk)
    }

    @Test
    fun checkAddTorrentSuccess() = runTest {
        assertTrue(repository.addTorrentUrl(Constants.magnetUrl).isOk)

        val data = repository.observeMainData().first()
        assertTrue(data.torrents.containsKey(Constants.magnetHash))
    }

    @Test
    fun checkRemoveTorrentSuccess() = runTest {
        assertTrue(repository.removeTorrents(listOf(Constants.magnetHash)).isOk)

        val data = repository.observeMainData().first()
        assertFalse(data.torrents.containsKey(Constants.magnetHash))
    }
}
