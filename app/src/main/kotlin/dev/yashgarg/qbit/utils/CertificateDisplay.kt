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
 * Validity is worth surfacing because approving a certificate outside its window cannot help: chain
 * validation still rejects it, so without this the user would re-approve the same certificate
 * against the same failure indefinitely.
 */
fun X509Certificate.hasExpired(): Boolean = notAfter.before(Date())

/**
 * The other half of [hasExpired]: a certificate generated while the server's clock was wrong starts
 * in the future, and is rejected until then just as firmly as an expired one.
 */
fun X509Certificate.isNotYetValid(): Boolean = notBefore.after(Date())

/** Fixed pattern rather than a locale-dependent one, so the digit order can't surprise in RTL. */
fun X509Certificate.validUntilText(): String = formatDate(notAfter)

/** Start of the validity window, shown when a certificate is not valid yet. */
fun X509Certificate.validFromText(): String = formatDate(notBefore)

private fun formatDate(date: Date): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(date)
