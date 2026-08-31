package dev.yashgarg.qbit.utils

import java.security.MessageDigest
import java.security.cert.X509Certificate

/** For displaying a certificate to the user before they decide whether to trust it. */
fun X509Certificate.sha256Fingerprint(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }

/** True if [host] appears among this certificate's subject alternative names. */
fun X509Certificate.matchesHost(host: String): Boolean =
    runCatching { subjectAlternativeNames }
        .getOrNull()
        .orEmpty()
        .any { entry ->
            val value = entry.getOrNull(1) as? String
            value.equals(host, ignoreCase = true)
        }
