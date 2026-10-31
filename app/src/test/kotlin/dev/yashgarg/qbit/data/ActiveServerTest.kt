package dev.yashgarg.qbit.data

import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.data.models.activeServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveServerTest {
    private fun server(id: Int) =
        ServerConfig(
            configId = id,
            serverName = "Server $id",
            baseUrl = "host",
            username = "",
            password = "",
            connectionType = ConnectionType.HTTP,
        )

    private val servers = listOf(server(4), server(7))

    @Test
    fun `the stored server is the active one`() {
        assertEquals(7, servers.activeServer(7)?.configId)
    }

    @Test
    fun `with no server stored, the first one is active`() {
        assertEquals(4, servers.activeServer(-1)?.configId)
    }

    @Test
    fun `once the stored server is deleted, the first one left is active`() {
        assertEquals(4, servers.activeServer(9)?.configId)
    }

    @Test
    fun `with no servers there is no active one`() {
        assertNull(emptyList<ServerConfig>().activeServer(4))
    }
}
