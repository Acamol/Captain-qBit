package dev.yashgarg.qbit.ui.server

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import dev.yashgarg.qbit.data.QbitRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class TorrentAddSessionTest {
    private val hash = "a".repeat(40)
    private val marker = "preview"

    @Test
    fun `existing torrents are rejected before posting an add`() = runTest {
        val client =
            mock<QbitRepository> {
                onBlocking { getTorrentTags(hash) } doReturn Ok(emptyList())
            }
        val session = TorrentAddSession(client, hash, marker)

        assertTrue(
            session.add { error("must not submit a duplicate") }.getError()
                is TorrentAlreadyPresent,
        )
        assertTrue(session.discard().isOk)
        verify(client, never()).removeTorrents(any(), any())
    }

    @Test
    fun `successful add response does not claim another client's torrent`() = runTest {
        val client =
            mock<QbitRepository> {
                onBlocking { getTorrentTags(hash) }.doReturn(Ok(null), Ok(listOf("user-tag")))
            }
        val session = TorrentAddSession(client, hash, marker)

        val added = session.add { Ok(Unit) }

        assertTrue(added.getError() is TorrentAlreadyPresent)
        assertTrue(session.stop().isErr)
        assertTrue(session.discard().isOk)
        verify(client, never()).toggleTorrentsState(any(), any())
        verify(client, never()).removeTorrents(any(), any())
    }

    @Test
    fun `a rejected skip keeps the torrent stopped until a retry succeeds`() = runTest {
        val rejection = IllegalStateException()
        val client =
            mock<QbitRepository> {
                onBlocking { getTorrentTags(hash) }.doReturn(Ok(null), Ok(listOf(marker)))
                onBlocking { toggleTorrentsState(listOf(hash), pause = true) } doReturn Ok(Unit)
                onBlocking { toggleTorrentsState(listOf(hash), pause = false) } doReturn Ok(Unit)
                onBlocking { setFilePriority(hash, listOf(1), 0) } doReturn Err(rejection)
            }
        val session = TorrentAddSession(client, hash, marker)

        assertTrue(session.add { Ok(Unit) }.isOk)
        assertTrue(session.stop().isOk)
        assertEquals(rejection, session.applySelectionAndStart(listOf(1), false).getError()?.cause)
        verify(client, times(4)).setFilePriority(hash, listOf(1), 0)
        verify(client, never()).toggleTorrentsState(listOf(hash), pause = false)

        client.stub { onBlocking { setFilePriority(hash, listOf(1), 0) } doReturn Ok(Unit) }
        assertTrue(session.applySelectionAndStart(listOf(1), false).isOk)
        verify(client).toggleTorrentsState(listOf(hash), pause = false)
    }
}
