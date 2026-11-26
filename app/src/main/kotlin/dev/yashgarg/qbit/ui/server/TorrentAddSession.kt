package dev.yashgarg.qbit.ui.server

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import dev.yashgarg.qbit.data.QbitRepository
import java.util.UUID
import kotlinx.coroutines.delay

internal class TorrentAddSession(
    val repository: QbitRepository,
    val hash: String,
    val marker: String = "captain-qbit-add-${UUID.randomUUID()}",
) {
    private val hashes = listOf(hash)
    var ownsTorrent: Boolean = false
        private set

    suspend fun add(submit: suspend (String) -> Result<Unit, Throwable>): Result<Unit, Throwable> {
        if (ownsTorrent) return verifyOwnership()
        repository
            .getTorrentTags(hash)
            .onErr {
                return Err(it)
            }
            .onOk {
                if (it != null) return Err(TorrentAlreadyPresent())
            }
        submit(marker).onErr {
            return Err(it)
        }

        // A successful POST need not create anything (e.g. another client won a duplicate add).
        // Only a new torrent carrying the tag from this request is ours to modify or remove.
        repeat(READY_ATTEMPTS) {
            repository
                .getTorrentTags(hash)
                .onErr {
                    return Err(it)
                }
                .onOk { tags ->
                    if (tags != null) {
                        if (marker !in tags) return Err(TorrentAlreadyPresent())
                        ownsTorrent = true
                        return Ok(Unit)
                    }
                }
            delay(READY_POLL_MS)
        }
        return Err(IllegalStateException("The added torrent was not found"))
    }

    suspend fun stop(): Result<Unit, Throwable> =
        verifyOwnership().andThen { repository.toggleTorrentsState(hashes, pause = true) }

    suspend fun applySelectionAndStart(
        deselectedIds: List<Int>,
        paused: Boolean,
    ): Result<Unit, Throwable> {
        verifyOwnership().onErr {
            return Err(it)
        }
        if (deselectedIds.isNotEmpty()) {
            setSkippedFiles(deselectedIds).onErr {
                return Err(FileSelectionNotApplied(it))
            }
        }
        return if (paused) Ok(Unit) else repository.toggleTorrentsState(hashes, pause = false)
    }

    private suspend fun setSkippedFiles(ids: List<Int>): Result<Unit, Throwable> {
        repeat(PRIORITY_ATTEMPTS - 1) {
            repository.setFilePriority(hash, ids, 0).onOk {
                return Ok(Unit)
            }
            delay(PRIORITY_RETRY_MS)
        }
        return repository.setFilePriority(hash, ids, 0)
    }

    suspend fun discard(onDiscarded: suspend () -> Unit = {}): Result<Unit, Throwable> {
        if (!ownsTorrent) return Ok(Unit)
        return repository
            .getTorrentTags(hash)
            .andThen { tags ->
                if (tags != null && marker in tags) repository.removeTorrents(hashes) else Ok(Unit)
            }
            .onOk { onDiscarded() }
            .andThen { releaseMarker() }
    }

    suspend fun releaseMarker(): Result<Unit, Throwable> {
        if (!ownsTorrent) return Ok(Unit)
        repository.deleteTags(listOf(marker)).onOk {
            ownsTorrent = false
            return Ok(Unit)
        }
        delay(MARKER_RETRY_MS)
        return repository.deleteTags(listOf(marker)).onOk { ownsTorrent = false }
    }

    private suspend fun verifyOwnership(): Result<Unit, Throwable> =
        repository.getTorrentTags(hash).andThen { tags ->
            if (ownsTorrent && tags != null && marker in tags) Ok(Unit)
            else Err(IllegalStateException("The torrent no longer belongs to this add session"))
        }

    private companion object {
        const val MARKER_RETRY_MS = 500L
        const val READY_ATTEMPTS = 20
        const val READY_POLL_MS = 500L
        const val PRIORITY_ATTEMPTS = 4
        const val PRIORITY_RETRY_MS = 750L
    }
}

internal class TorrentAlreadyPresent : IllegalStateException("Torrent already exists")

internal class FileSelectionNotApplied(cause: Throwable? = null) : IllegalStateException(cause)
