package dev.yashgarg.qbit.data.manager

import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import qbittorrent.QBittorrentClient
import qbittorrent.models.MainData

@OptIn(ExperimentalCoroutinesApi::class)
class ClientSettingsTest {
    @Test
    fun `each workspace gets data and health from the same installed client`() = runTest {
        val data = mock<MainData>()
        val offline = IllegalStateException("offline")
        val first =
            mock<QBittorrentClient> {
                on { observeMainData() } doReturn flowOf(data)
                on { observeMainDataError() } doReturn MutableStateFlow(null)
            }
        val replacement =
            mock<QBittorrentClient> {
                on { observeMainDataError() } doReturn MutableStateFlow(offline)
            }
        val manager =
            mock<ClientManager> {
                onBlocking { checkAndGetClient() }.doReturn(first, replacement)
            }
        val repository = QbitRepository(manager)
        val before = requireNotNull(repository.forCurrentClient())
        val after = requireNotNull(repository.forCurrentClient())
        assertSame(data, before.observeMainData().first())
        assertEquals(null, before.observeMainDataError().first())
        assertSame(offline, after.observeMainDataError().first())
    }

    @Test
    fun `only changes affecting the resolved active client signal a replacement`() = runTest {
        val a =
            ServerConfig(
                1,
                "One",
                "one",
                username = "",
                password = "",
                connectionType = ConnectionType.HTTP,
            )
        val b = a.copy(configId = 2, baseUrl = "two")
        val configs = MutableStateFlow(listOf(a, b))
        val prefs = MutableStateFlow(AppPreferences(activeServerId = 1, syncIntervalMs = 1000))
        val changes = mutableListOf<ClientSettings>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            clientSettings(configs, prefs).toList(changes)
        }
        runCurrent()
        configs.value =
            listOf(b.copy(password = "updated"), a.copy(position = 1, serverName = "renamed"))
        runCurrent()
        assertEquals(1, changes.size)
        prefs.value = prefs.value.copy(syncIntervalMs = 2000)
        runCurrent()
        configs.value = listOf(b, a.copy(path = "/new"))
        runCurrent()
        prefs.value = prefs.value.copy(activeServerId = 2)
        runCurrent()
        assertEquals(listOf(1000L, 2000L, 2000L, 2000L), changes.map { it.interval })
        assertEquals(listOf("one", "one", "one", "two"), changes.map { it.config?.baseUrl })
        assertEquals("/new", changes[2].config?.path)
    }
}
