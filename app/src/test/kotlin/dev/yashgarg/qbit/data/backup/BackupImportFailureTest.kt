package dev.yashgarg.qbit.data.backup

import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import java.security.KeyStoreException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class BackupImportFailureTest {
    @Test
    fun `replace leaves original servers untouched if a later credential cannot be encrypted`() =
        runTest {
            val dao = mock<ConfigDao>()
            val manager = BackupManager(mock(), dao, mock(), mock())
            val server =
                ServerConfig(
                    configId = 1,
                    serverName = "One",
                    baseUrl = "localhost",
                    username = "",
                    password = "",
                    connectionType = ConnectionType.HTTP,
                )
            val servers = listOf(server, server.copy(configId = 2, password = "example"))
            val failure = runCatching {
                manager.applyImport(
                    ConfigBackup(servers),
                    servers,
                    emptySet(),
                    false,
                    ImportMode.REPLACE,
                )
            }
                .exceptionOrNull()

            assertTrue(failure is KeyStoreException)
            verifyNoInteractions(dao)
        }
}
