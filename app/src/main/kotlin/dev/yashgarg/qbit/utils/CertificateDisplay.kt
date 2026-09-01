package dev.yashgarg.qbit.utils

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** For displaying a certificate to the user before they decide whether to trust it. */
fun X509Certificate.sha256Fingerprint(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }

/**
 * Expiry is worth surfacing because approving an expired certificate cannot help: chain validation
 * still rejects it, so without this the user would re-approve the same certificate against the same
 * failure indefinitely.
 */
fun X509Certificate.hasExpired(): Boolean = notAfter.before(Date())

/** Fixed pattern rather than a locale-dependent one, so the digit order can't surprise in RTL. */
fun X509Certificate.validUntilText(): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(notAfter)
