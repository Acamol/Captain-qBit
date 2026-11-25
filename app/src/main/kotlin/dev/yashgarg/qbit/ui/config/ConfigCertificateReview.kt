package dev.yashgarg.qbit.ui.config

import java.security.cert.X509Certificate

internal data class ConfigCertificateEndpoint(val host: String, val port: Int) {
    companion object {
        fun from(host: String, port: String, connectionType: String): ConfigCertificateEndpoint? {
            if (!connectionType.equals("https", ignoreCase = true)) return null
            val resolvedPort = if (port.isBlank()) 443 else port.trim().toIntOrNull() ?: return null
            return ConfigCertificateEndpoint(host.trim().lowercase(), resolvedPort)
        }
    }
}

internal data class ConfigCertificateReview(
    val endpoint: ConfigCertificateEndpoint,
    val certificate: X509Certificate,
) {
    fun approvedDerFor(endpoint: ConfigCertificateEndpoint?): ByteArray? =
        if (endpoint == this.endpoint) certificate.encoded else null
}
