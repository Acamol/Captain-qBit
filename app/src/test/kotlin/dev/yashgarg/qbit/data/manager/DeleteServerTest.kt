package dev.yashgarg.qbit.data.manager

import androidx.datastore.core.DataStore
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DeleteServerTest {
    private fun server(id: Int) =
        ServerConfig(
            configId = id,
            serverName = "Server $id",
            baseUrl = "host",
            username = "",
            password = "",
            connectionType = ConnectionType.HTTP,
        )

    private val configs = MutableStateFlow(listOf(server(1), server(2), server(3)))
    private val prefs = MutableStateFlow(AppPreferences(activeServerId = 2))

    private val configDao: ConfigDao = mock {
        on { getConfigs() } doReturn configs
        on { deleteConfig(any()) } doAnswer
            { call ->
                configs.value = configs.value.filter { it.configId != call.arguments[0] }
            }
    }

    private val prefsStore =
        object : DataStore<AppPreferences> {
            override val data: Flow<AppPreferences> = prefs

            override suspend fun updateData(
                transform: suspend (t: AppPreferences) -> AppPreferences,
            ): AppPreferences = transform(prefs.value).also { prefs.value = it }
        }

    // A cancelled scope: the client is never built, only the server list and the active id change.
    private val manager =
        ClientManagerImpl(configDao, prefsStore, CoroutineScope(Job().apply { cancel() }))

    @Test
    fun `the only server can't be deleted`() = runTest {
        configs.value = listOf(server(1))

        assertFalse(manager.deleteServer(1))
        assertEquals(listOf(server(1)), configs.value)
    }

    @Test
    fun `deleting the active server makes the first one left active`() = runTest {
        assertTrue(manager.deleteServer(2))

        assertEquals(listOf(1, 3), configs.value.map { it.configId })
        assertEquals(1, prefs.value.activeServerId)
    }

    @Test
    fun `deleting another server keeps the active one`() = runTest {
        assertTrue(manager.deleteServer(3))

        assertEquals(2, prefs.value.activeServerId)
    }

    @Test
    fun `with no server stored as active, deleting the first one makes the next one active`() =
        runTest {
            prefs.value = AppPreferences(activeServerId = -1)

            assertTrue(manager.deleteServer(1))

            assertEquals(2, prefs.value.activeServerId)
        }
}
