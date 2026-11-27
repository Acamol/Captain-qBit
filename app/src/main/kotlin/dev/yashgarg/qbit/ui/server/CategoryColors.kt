package dev.yashgarg.qbit.ui.server

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Category colors are stored as a single ARGB seed and rendered as tonal Material 3 chips. Hue,
 * chroma and (within a readable band) lightness come from the seed in OKLCH, a perceptual color
 * space, so every seed stays readable in both light and dark mode.
 */
internal object CategoryColors {
    data class Lch(val l: Double, val c: Double, val h: Double)

    data class ChipColors(val container: Int, val content: Int)

    const val SWATCH_LIGHTNESS = 0.68
    const val MIN_LIGHTNESS = 0.5
    const val MAX_LIGHTNESS = 0.86
    const val MAX_CHROMA = 0.16
    private const val PRESET_CHROMA = 0.14

    private val presetHues = listOf(25, 50, 80, 110, 145, 175, 200, 235, 265, 295, 325, 355)

    val presets: List<Int> = presetHues.map {
        fromLch(SWATCH_LIGHTNESS, PRESET_CHROMA, it.toDouble())
    }

    /** The seed as a picker swatch: clamped to the lightness and chroma range chips can show. */
    fun swatch(seed: Int): Int {
        val lch = toLch(seed)
        return fromLch(lch.l.coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS), min(lch.c, MAX_CHROMA), lch.h)
    }

    fun chip(seed: Int, dark: Boolean): ChipColors {
        val lch = toLch(seed)
        val c = min(lch.c, MAX_CHROMA)
        val tone = (lch.l - MIN_LIGHTNESS) / (MAX_LIGHTNESS - MIN_LIGHTNESS)
        val t = tone.coerceIn(0.0, 1.0)
        return if (dark) {
            ChipColors(fromLch(0.26 + 0.16 * t, c * 0.7, lch.h), fromLch(0.94, c * 0.25, lch.h))
        } else {
            ChipColors(fromLch(0.80 + 0.14 * t, c * 0.5, lch.h), fromLch(0.28, c * 0.8, lch.h))
        }
    }

    /**
     * "Good" Auto candidates: every 5° of hue at a few lightness/chroma levels, keeping only colors
     * that are vivid enough not to look muddy and fit in sRGB without clipping.
     */
    private val candidates: List<Int> by lazy {
        buildList {
            for (hue in 0 until 360 step 5) {
                for (l in listOf(0.56, 0.68, 0.80)) {
                    for (c in listOf(0.08, 0.115, 0.15)) {
                        rgbOrNull(l, c, hue.toDouble())?.let(::add)
                    }
                }
            }
        }
    }

    // Candidates are scored by how far their chips (in both themes) are from the nearest taken
    // color, with a small bonus for vivid, mid-tone colors to break near-ties in favor of nicer
    // ones.
    private fun score(candidate: Int, taken: List<List<DoubleArray>>): Double {
        val chips = chipLabs(candidate)
        val distance =
            if (taken.isEmpty()) 1.0
            else taken.minOf { other -> chips.indices.minOf { dist(chips[it], other[it]) } }
        val lch = toLch(candidate)
        return distance + 0.04 * lch.c - 0.02 * abs(lch.l - SWATCH_LIGHTNESS)
    }

    /**
     * Ranked, mutually distinct Auto options for a category given the [others]' colors: the best
     * candidate first, then each next one skipping candidates that look too close to one already
     * listed. Without any other color, [name] seeds the starting hue so new categories differ.
     */
    fun autoOptions(name: String, others: Collection<Int>): List<Int> {
        val taken = others.map(::chipLabs)
        val scored = candidates.map { it to score(it, taken) }
        val ranked =
            if (taken.isEmpty()) {
                val start = Math.floorMod(name.hashCode(), 72) * 5
                scored.sortedWith(
                    compareBy<Pair<Int, Double>> {
                            Math.floorMod((toLch(it.first).h - start).roundToInt(), 360)
                        }
                        .thenByDescending { it.second },
                )
            } else {
                scored.sortedByDescending { it.second }
            }
        val options = mutableListOf<Pair<Int, List<DoubleArray>>>()
        for ((c, _) in ranked) {
            val labs = chipLabs(c)
            val tooClose = options.any { (_, o) ->
                labs.indices.any { dist(labs[it], o[it]) < MIN_GAP }
            }
            if (!tooClose) {
                options += c to labs
                if (options.size == MAX_OPTIONS) break
            }
        }
        return options.map { it.first }
    }

    /**
     * The best Auto color for [name], or with [current] ("Pick another") the next option after it,
     * cycling back to the best one at the end.
     */
    fun autoPick(name: String, others: Collection<Int>, current: Int? = null): Int {
        val options = autoOptions(name, others)
        if (current == null) return options.first()
        val labs = chipLabs(current)
        val nearest = options.indices.minBy { i -> dist(chipLabs(options[i])[0], labs[0]) }
        return options[(nearest + 1) % options.size]
    }

    private const val MIN_GAP = 0.06
    private const val MAX_OPTIONS = 12

    private fun chipLabs(color: Int): List<DoubleArray> =
        listOf(false, true).map { dark -> lab(chip(color, dark).container) }

    private fun lab(argb: Int): DoubleArray {
        val lch = toLch(argb)
        val rad = Math.toRadians(lch.h)
        return doubleArrayOf(lch.l, lch.c * cos(rad), lch.c * sin(rad))
    }

    private fun dist(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return kotlin.math.sqrt(dl * dl + da * da + db * db)
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
