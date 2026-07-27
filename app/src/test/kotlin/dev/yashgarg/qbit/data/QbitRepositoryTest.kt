package dev.yashgarg.qbit.data

import com.github.michaelbull.result.get
import dev.yashgarg.qbit.Constants
import dev.yashgarg.qbit.FakeClientManager
import dev.yashgarg.qbit.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        // Integration tests against an in-JVM Ktor MockEngine ([FakeClientManager]) — no real
        // qBittorrent server or env vars needed, so they always run (incl. CI).
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

    @Test
    fun `rss items load as an empty tree before any feed is added`() = runTest {
        val result = repository.getRssItems()
        assertTrue(result.isOk)
        assertTrue(result.get().orEmpty().isEmpty())
    }

    @Test
    fun `adding an rss feed is reflected in the next items fetch`() = runTest {
        assertTrue(repository.addRssFeed("https://example.org/feed.xml").isOk)

        val items = repository.getRssItems().get().orEmpty()
        assertTrue(items.any { it.name == "Test Feed" })
    }

    @Test
    fun `setting an rss rule is reflected in the next rules fetch`() = runTest {
        val rule = qbittorrent.models.RssRule(mustContain = "1080p")
        assertTrue(repository.setRssRule("test-rule", rule).isOk)

        val rules = repository.getRssRules().get().orEmpty()
        assertTrue(rules["test-rule"]?.mustContain == "1080p")
    }
}
