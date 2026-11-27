package dev.yashgarg.qbit.ui.logs

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.yashgarg.qbit.MainDispatcherRule
import dev.yashgarg.qbit.data.QbitRepository
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import qbittorrent.models.LogEntry

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val first = listOf(LogEntry(1, "First server", 1, LogEntry.TYPE_INFO))
    private val second = listOf(LogEntry(2, "Second server", 2, LogEntry.TYPE_INFO))

    @Test
    fun `switching servers clears the previous server entries while loading`() = runTest {
        val response = CompletableDeferred<Result<List<LogEntry>, Throwable>>()
        var requests = 0
        val repository =
            mock<QbitRepository> {
                onBlocking { getLogs() } doSuspendableAnswer
                    {
                        if (requests++ == 0) Ok(first) else response.await()
                    }
            }
        val viewModel = LogsViewModel(repository, mock())
        viewModel.loadServer(1)
        assertEquals(first, viewModel.uiState.value.entries)

        viewModel.loadServer(2)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertTrue(viewModel.uiState.value.loading)

        response.complete(Ok(second))
        runCurrent()
        assertEquals(second, viewModel.uiState.value.entries)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun `a late response from the previous server cannot overwrite current logs`() = runTest {
        lateinit var finishOldRequest: Continuation<Result<List<LogEntry>, Throwable>>
        var requests = 0
        val repository =
            mock<QbitRepository> {
                onBlocking { getLogs() } doSuspendableAnswer
                    {
                        if (requests++ == 0)
                            suspendCoroutine<Result<List<LogEntry>, Throwable>> {
                                finishOldRequest = it
                            }
                        else Ok(second)
                    }
            }
        val viewModel = LogsViewModel(repository, mock())
        viewModel.loadServer(1)
        viewModel.loadServer(2)
        assertEquals(second, viewModel.uiState.value.entries)

        finishOldRequest.resume(Ok(first))
        runCurrent()
        assertEquals(second, viewModel.uiState.value.entries)
    }

    @Test
    fun `returning to the same server refreshes without blanking its visible logs`() = runTest {
        val response = CompletableDeferred<Result<List<LogEntry>, Throwable>>()
        var requests = 0
        val repository =
            mock<QbitRepository> {
                onBlocking { getLogs() } doSuspendableAnswer
                    {
                        if (requests++ == 0) Ok(first) else response.await()
                    }
            }
        val viewModel = LogsViewModel(repository, mock())
        viewModel.loadServer(1)
        viewModel.loadServer(1)
        assertEquals(first, viewModel.uiState.value.entries)
        assertTrue(viewModel.uiState.value.refreshing)

        response.complete(Ok(second))
        runCurrent()
        assertEquals(second, viewModel.uiState.value.entries)
        assertFalse(viewModel.uiState.value.refreshing)
    }
}
