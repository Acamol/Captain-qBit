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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class TorrentAddSessionTest {
    private val hash = "a".repeat(40)
    private val marker = "preview"

    @Test
    fun `reselecting a skipped file restores its priority before a retry`() = runTest {
        val client =
            mock<QbitRepository> {
                onBlocking { getTorrentTags(hash) }.doReturn(Ok(null), Ok(listOf(marker)))
                onBlocking { setFilePriority(eq(hash), eq(listOf(1)), any()) } doReturn Ok(Unit)
                onBlocking { toggleTorrentsState(listOf(hash), false) }
                    .doReturn(Err(IllegalStateException()), Ok(Unit))
            }
        val session = TorrentAddSession(client, hash, marker)
        assertTrue(session.add { Ok(Unit) }.isOk)
        assertTrue(session.applySelectionAndStart(listOf(1), false).isErr)
        assertTrue(session.applySelectionAndStart(emptyList(), false).isOk)
        verify(client).setFilePriority(hash, listOf(1), 1)
    }

    @Test
    fun `retry recovers the tagged torrent after a lost ownership response`() = runTest {
        val client =
            mock<QbitRepository> {
                onBlocking { getTorrentTags(hash) }
                    .doReturn(Ok(null), Err(IllegalStateException()), Ok(listOf(marker)))
            }
        val session = TorrentAddSession(client, hash, marker)
        assertTrue(session.add { Ok(Unit) }.isErr)
        assertTrue(session.add { error("must not submit twice") }.isOk)
    }

    @Test
    fun `discard checks the marker even if ownership response was lost`() = runTest {
        val client =
            mock<QbitRepository> {
                onBlocking { getTorrentTags(hash) }
                    .doReturn(Ok(null), Err(IllegalStateException()), Ok(listOf(marker)))
                onBlocking { removeTorrents(listOf(hash), false) } doReturn Ok(Unit)
                onBlocking { deleteTags(listOf(marker)) } doReturn Ok(Unit)
            }
        val session = TorrentAddSession(client, hash, marker)
        assertTrue(session.add { Ok(Unit) }.isErr)
        assertTrue(session.discard().isOk)
        verify(client).removeTorrents(listOf(hash), false)
    }

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
