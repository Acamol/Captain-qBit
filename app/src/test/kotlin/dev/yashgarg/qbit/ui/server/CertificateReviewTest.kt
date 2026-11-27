package dev.yashgarg.qbit.ui.server

import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import java.security.cert.X509Certificate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class CertificateReviewTest {
    private val config =
        ServerConfig(
            configId = 7,
            serverName = "Home",
            baseUrl = "server.local",
            port = 444,
            path = "/qbit",
            username = "",
            password = "",
            connectionType = ConnectionType.HTTPS,
        )
    private val certificate = mock<X509Certificate> { on { encoded } doReturn byteArrayOf(1, 2, 3) }
    private val review = CertificateReview(requireNotNull(config.certificateTarget()), certificate)

    @Test
    fun `a review cannot pin after switching server or changing any endpoint component`() {
        val dao = mock<ConfigDao>()
        for (current in
            listOf(
                null,
                config.copy(configId = 8),
                config.copy(baseUrl = "other"),
                config.copy(port = 555),
                config.copy(path = "/new"),
                config.copy(connectionType = ConnectionType.HTTP),
                config.copy(pinnedCertificate = "other pin"),
            )) {
            assertFalse(review.pinIfCurrent(current, dao))
        }
        verifyNoInteractions(dao)
    }

    @Test
    fun `approval changes only the pin conditionally on the reviewed endpoint`() {
        val dao =
            mock<ConfigDao> {
                on {
                    pinIfEndpointUnchanged(7, "server.local", 444, "/qbit", null, "AQID")
                } doReturn 1
            }
        assertTrue(review.pinIfCurrent(config.copy(username = "new", position = 2), dao))
        verify(dao).pinIfEndpointUnchanged(7, "server.local", 444, "/qbit", null, "AQID")
    }
}
