package dev.yashgarg.qbit.ui.server

import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import java.security.cert.X509Certificate
import java.util.Base64

internal data class CertificateTarget(
    val id: Int,
    val host: String,
    val port: Int?,
    val path: String?,
    val previousPin: String?,
)

internal fun ServerConfig.certificateTarget(): CertificateTarget? =
    if (connectionType == ConnectionType.HTTPS)
        CertificateTarget(configId, baseUrl, port, path, pinnedCertificate)
    else null

internal data class CertificateReview(
    val target: CertificateTarget,
    val certificate: X509Certificate,
) {
    fun pinIfCurrent(active: ServerConfig?, dao: ConfigDao): Boolean {
        if (active?.certificateTarget() != target) return false
        return dao.pinIfEndpointUnchanged(
            target.id,
            target.host,
            target.port,
            target.path,
            target.previousPin,
            Base64.getEncoder().encodeToString(certificate.encoded),
        ) == 1
    }
}
