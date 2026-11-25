package dev.yashgarg.qbit.ui.server

import kotlin.math.sqrt

internal fun downloadWaveCyclesPerSecond(downloadSpeed: Long, selectedSize: Long): Float {
    if (downloadSpeed <= 0 || selectedSize <= 0) return 0f

    // One cycle/s at 1% of the selected size/s. Compress the range so slow transfers still move,
    // without making fast transfers distracting. Use total selected size, not bytes remaining,
    // so the wave doesn't accelerate just because the download is nearly finished.
    val percentPerSecond = downloadSpeed.toDouble() / selectedSize * 100
    return sqrt(percentPerSecond).coerceIn(0.15, 2.0).toFloat()
}
