package dev.yashgarg.qbit.ui.config

import androidx.lifecycle.SavedStateHandle
import com.github.michaelbull.result.getError
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.ServerConfig
import java.security.cert.X509Certificate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ConfigCredentialSaveTest {
    @Test
    fun `unavailable Android Keystore fails the save before writing any credentials`() = runTest {
        val dao = mock<ConfigDao>()
        val model = ConfigViewModel(dao, mock(), SavedStateHandle())
        val result =
            model.insert(
                "Home",
                "localhost",
                "",
                "",
                "https",
                "user",
                "example",
                null,
                null,
                null,
            )

        assertNotNull(result.getError())
        verify(dao, never()).addConfig(any())
    }

    @Test
    fun `approval is scoped to the normalized HTTPS host and port`() {
        val der = byteArrayOf(1, 2, 3)
        val certificate = mock<X509Certificate> { on { encoded } doReturn der }
        val review =
            ConfigCertificateReview(ConfigCertificateEndpoint("example.com", 443), certificate)

        assertArrayEquals(
            der,
            review.approvedDerFor(ConfigCertificateEndpoint.from("EXAMPLE.COM ", "", "https")),
        )
        assertNull(
            review.approvedDerFor(ConfigCertificateEndpoint.from("example.com", "8443", "https")),
        )
        assertNull(
            review.approvedDerFor(ConfigCertificateEndpoint.from("example.com", "443", "http")),
        )
    }

    @Test
    fun `saving a different host does not persist the earlier approval`() = runTest {
        val dao = mock<ConfigDao>()
        val model = ConfigViewModel(dao, mock(), SavedStateHandle())
        val review = ConfigCertificateReview(ConfigCertificateEndpoint("old.example", 443), mock())
        val result =
            model.insert(
                "Home",
                "new.example",
                "",
                "",
                "https",
                "",
                "",
                null,
                null,
                null,
                review,
            )

        assertNull(result.getError())
        val config = argumentCaptor<ServerConfig>()
        verify(dao).addConfig(config.capture())
        assertNull(config.firstValue.pinnedCertificate)
    }
}
