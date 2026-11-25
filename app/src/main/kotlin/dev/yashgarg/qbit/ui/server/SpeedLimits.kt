package dev.yashgarg.qbit.ui.server

internal data class SpeedLimits(val download: Int, val upload: Int) {
    val downloadText = display(download)
    val uploadText = display(upload)

    /** A null direction was not edited: never round or rewrite its existing byte-exact cap. */
    fun changes(downloadText: String, uploadText: String): SpeedLimitChanges =
        SpeedLimitChanges(
            downloadText.takeUnless { it == this.downloadText }?.let(::bytes),
            uploadText.takeUnless { it == this.uploadText }?.let(::bytes),
        )

    private fun display(bytes: Int): String =
        if (bytes <= 0) "" else ((bytes.toLong() + 512) / 1024).coerceAtLeast(1).toString()

    private fun bytes(kib: String): Int {
        if (kib.isEmpty()) return 0
        val value = kib.toLongOrNull() ?: return Int.MAX_VALUE
        return (value.coerceAtMost(Int.MAX_VALUE.toLong()) * 1024)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }
}

internal data class SpeedLimitChanges(val download: Int?, val upload: Int?)
