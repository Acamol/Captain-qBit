package dev.yashgarg.qbit.ui.server

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Category colors are stored as a single ARGB seed and rendered as tonal Material 3 chips. Hue and
 * chroma come from the seed in OKLCH, a perceptual color space; lightness comes from the theme, so
 * every seed stays readable in both light and dark mode.
 */
internal object CategoryColors {
    data class Lch(val l: Double, val c: Double, val h: Double)

    data class ChipColors(val container: Int, val content: Int)

    const val SWATCH_LIGHTNESS = 0.68
    const val MAX_CHROMA = 0.16
    private const val PRESET_CHROMA = 0.14

    private val presetHues = listOf(25, 50, 80, 110, 145, 175, 200, 235, 265, 295, 325, 355)

    val presets: List<Int> = presetHues.map {
        fromLch(SWATCH_LIGHTNESS, PRESET_CHROMA, it.toDouble())
    }

    /** The seed as a picker swatch: its hue and chroma at a fixed, mid lightness. */
    fun swatch(seed: Int): Int {
        val lch = toLch(seed)
        return fromLch(SWATCH_LIGHTNESS, min(lch.c, MAX_CHROMA), lch.h)
    }

    fun chip(seed: Int, dark: Boolean): ChipColors {
        val lch = toLch(seed)
        val c = min(lch.c, MAX_CHROMA)
        return if (dark) {
            ChipColors(fromLch(0.36, c * 0.55, lch.h), fromLch(0.92, c * 0.3, lch.h))
        } else {
            ChipColors(fromLch(0.92, c * 0.4, lch.h), fromLch(0.32, c * 0.8, lch.h))
        }
    }

    /**
     * Picks the swatch color farthest from every color in [others]. With [avoid] (the current pick,
     * on "Pick another"), that color is treated as taken too, so the result moves away from it.
     * Without anything to avoid, [name] seeds a stable hue so new categories don't all start red.
     */
    fun autoPick(name: String, others: Collection<Int>, avoid: Int? = null): Int {
        val taken = others + listOfNotNull(avoid)
        if (taken.isEmpty()) {
            val hue = Math.floorMod(name.hashCode(), 360).toDouble()
            return fromLch(SWATCH_LIGHTNESS, PRESET_CHROMA, hue)
        }
        val takenAb = taken.map(::chipAb)
        return (0 until 360 step 2)
            .map { fromLch(SWATCH_LIGHTNESS, PRESET_CHROMA, it.toDouble()) }
            .maxBy { candidate ->
                val ab = chipAb(candidate)
                takenAb.minOf { hypot(ab.first - it.first, ab.second - it.second) }
            }
    }

    // Distance is measured on what the chip shows: hue and (capped) chroma, not lightness.
    private fun chipAb(color: Int): Pair<Double, Double> {
        val lch = toLch(color)
        val c = min(lch.c, MAX_CHROMA)
        val rad = Math.toRadians(lch.h)
        return c * cos(rad) to c * sin(rad)
    }

    fun toLch(argb: Int): Lch {
        val r = linear((argb shr 16 and 0xFF) / 255.0)
        val g = linear((argb shr 8 and 0xFF) / 255.0)
        val b = linear((argb and 0xFF) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val okL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val okA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val okB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        val hue = Math.toDegrees(atan2(okB, okA)).let { if (it < 0) it + 360 else it }
        return Lch(okL, hypot(okA, okB), hue)
    }

    /** OKLCH to opaque ARGB, lowering chroma until the color fits in sRGB. */
    fun fromLch(l: Double, c: Double, h: Double): Int {
        rgbOrNull(l, c, h)?.let {
            return it
        }
        var low = 0.0
        var high = c
        repeat(20) {
            val mid = (low + high) / 2
            if (rgbOrNull(l, mid, h) != null) low = mid else high = mid
        }
        return rgbOrNull(l, low, h) ?: rgbOrNull(l, 0.0, h)!!
    }

    private fun rgbOrNull(l: Double, c: Double, h: Double): Int? {
        val rad = Math.toRadians(h)
        val a = c * cos(rad)
        val b = c * sin(rad)
        val l3 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m3 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s3 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val channels =
            listOf(
                4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
                -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
                -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3,
            )
        if (channels.any { it < -EPSILON || it > 1 + EPSILON }) return null
        val (r, g, bl) = channels.map { (gamma(it.coerceIn(0.0, 1.0)) * 255).roundToInt() }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun gamma(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055

    private const val EPSILON = 1e-4

    fun toHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6) return null
        return hex.toIntOrNull(16)?.let { it or (0xFF shl 24) }
    }
}
