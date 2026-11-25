package dev.yashgarg.qbit.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import qbittorrent.models.TorrentFile

class TransformUtilTest {
    @Test
    fun `partial files retain downloaded bytes including unwanted files`() {
        val tree =
            TransformUtil.transformFilesToTree(
                listOf(
                    file(0, "partial.bin", 1000, 0.25f),
                    file(1, ".unwanted/skipped.bin", 200, 0.5f),
                ),
                0,
            )

        assertEquals(250L, tree.single { it.name == "partial.bin" }.progress)
        assertEquals(100L, tree.single { it.name == "skipped.bin" }.progress)
    }

    @Test
    fun `nested folder progress is weighted by file size rather than file count`() {
        val folder =
            TransformUtil.transformFilesToTree(
                    listOf(
                        file(0, "folder/done.bin", 1000, 1f),
                        file(1, "folder/nested/missing.bin", 3000, 0f),
                    ),
                    0,
                )
                .single()

        assertEquals(4000L, folder.size)
        assertEquals(1000L, folder.progress)
        assertEquals(0L, folder.children!!.single { it.name == "nested" }.progress)
    }

    private fun file(index: Int, name: String, size: Long, progress: Float) =
        TorrentFile(
            index = index,
            name = name,
            size = size,
            progress = progress,
            priority = 1,
            pieceRange = listOf(0, 1),
            availability = 1f,
        )
}
