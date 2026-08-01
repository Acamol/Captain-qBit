package dev.yashgarg.qbit.ui.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssArticlesScreenTest {

    @Test
    fun `blank query matches everything`() {
        assertTrue(matchesQuery("Rick and Morty S01E01", ""))
        assertTrue(matchesQuery("Rick and Morty S01E01", "   "))
    }

    @Test
    fun `single word matches a substring anywhere in the title`() {
        assertTrue(matchesQuery("Rick and Morty S01E01", "morty"))
    }

    @Test
    fun `multiple words match regardless of order`() {
        assertTrue(matchesQuery("Rick and Morty S01E01", "s01e01 rick"))
    }

    @Test
    fun `all words must be present`() {
        assertFalse(matchesQuery("Rick and Morty S01E01", "rick breaking bad"))
    }

    @Test
    fun `does not require the query as one contiguous substring`() {
        assertTrue(matchesQuery("Rick and Morty S01E01", "rick s01e01"))
    }

    @Test
    fun `is case-insensitive`() {
        assertTrue(matchesQuery("Rick and Morty S01E01", "RICK MORTY"))
    }
}
