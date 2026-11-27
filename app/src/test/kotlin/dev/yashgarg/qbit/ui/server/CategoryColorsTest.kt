package dev.yashgarg.qbit.ui.server

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryColorsTest {
    private fun hueGap(a: Int, b: Int): Double {
        val d = abs(CategoryColors.toLch(a).h - CategoryColors.toLch(b).h) % 360
        return minOf(d, 360 - d)
    }

    @Test
    fun `auto pick lands opposite a single existing color`() {
        val red = CategoryColors.presets[0]
        val pick = CategoryColors.autoPick("movies", listOf(red))
        assertTrue(hueGap(pick, red) > 120)
    }

    @Test
    fun `auto pick is stable for the same inputs`() {
        val others = CategoryColors.presets.take(3)
        assertEquals(
            CategoryColors.autoPick("tv", others),
            CategoryColors.autoPick("tv", others),
        )
    }

    @Test
    fun `pick another moves away from the current pick`() {
        val others = listOf(CategoryColors.presets[0])
        val first = CategoryColors.autoPick("tv", others)
        val next = CategoryColors.autoPick("tv", others, current = first)
        assertNotEquals(first, next)
    }

    @Test
    fun `pick another cycles through several distinct options`() {
        val others = CategoryColors.presets.take(4)
        val seen = mutableSetOf<Int>()
        var color = CategoryColors.autoPick("tv", others)
        repeat(8) {
            seen += color
            color = CategoryColors.autoPick("tv", others, current = color)
        }
        assertEquals(8, seen.size)
    }

    @Test
    fun `with every preset taken auto still finds a color clearly apart from all of them`() {
        val pick = CategoryColors.autoPick("tv", CategoryColors.presets)
        assertTrue(CategoryColors.presets.none { it == pick })
        val lch = CategoryColors.toLch(pick)
        assertTrue("expected a vivid color, got chroma ${lch.c}", lch.c >= 0.07)
    }

    @Test
    fun `parses and formats hex`() {
        assertEquals(0xFF3366CC.toInt(), CategoryColors.parseHex("#3366cc"))
        assertEquals(0xFF3366CC.toInt(), CategoryColors.parseHex("3366CC"))
        assertNull(CategoryColors.parseHex("#36c"))
        assertNull(CategoryColors.parseHex("zzzzzz"))
        assertEquals("#3366CC", CategoryColors.toHex(0xFF3366CC.toInt()))
    }

    @Test
    fun `tonal chips keep readable contrast in both themes`() {
        (CategoryColors.presets + 0xFFFFFF00.toInt() + 0xFF000000.toInt()).forEach { seed ->
            listOf(false, true).forEach { dark ->
                val chip = CategoryColors.chip(seed, dark)
                val gap =
                    abs(
                        CategoryColors.toLch(chip.container).l -
                            CategoryColors.toLch(chip.content).l,
                    )
                assertTrue("seed ${CategoryColors.toHex(seed)} dark=$dark", gap > 0.5)
            }
        }
    }
}
