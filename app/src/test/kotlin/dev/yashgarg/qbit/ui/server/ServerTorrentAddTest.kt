package dev.yashgarg.qbit.ui.server

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModelStore
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.yashgarg.qbit.MainDispatcherRule
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.manager.PendingTorrentIntent
import dev.yashgarg.qbit.data.models.AppPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class ServerTorrentAddTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val viewModels = ViewModelStore()
    private val hash = "a".repeat(40)
    private val url = "magnet:?xt=urn:btih:$hash"
    private val addResult = CompletableDeferred<Result<Unit, Throwable>>()
    private val prefs =
        object : DataStore<AppPreferences> {
            override val data = MutableStateFlow(AppPreferences(notifyOnAdded = true))

            override suspend fun updateData(
                transform: suspend (AppPreferences) -> AppPreferences,
            ): AppPreferences = transform(data.value).also { data.value = it }
        }
    private lateinit var marker: String
    private val client =
        mock<QbitRepository> {
            onBlocking { getTorrentTags(hash) } doReturn Ok(null)
            onBlocking {
                addTorrentUrl(eq(url), isNull(), isNull(), eq(false), isNull(), any())
            } doSuspendableAnswer
                {
                    marker = it.getArgument(5)
                    addResult.await()
                }

            onBlocking { getTorrentFiles(hash) } doReturn Ok(emptyList())
            onBlocking { toggleTorrentsState(eq(listOf(hash)), any()) } doReturn Ok(Unit)
            onBlocking { removeTorrents(listOf(hash), false) } doReturn Ok(Unit)
            onBlocking { deleteTags(any()) } doReturn Ok(Unit)
        }

    private fun completeAdd() {
        client.stub { onBlocking { getTorrentTags(hash) } doReturn Ok(listOf(marker)) }
        addResult.complete(Ok(Unit))
    }

    private fun viewModel(): ServerViewModel {
        val repository = mock<QbitRepository> { onBlocking { forCurrentClient() } doReturn client }
        val dao = mock<ConfigDao> { on { getConfigs() } doReturn flowOf(emptyList()) }
        val manager = mock<ClientManager> { on { configStatus } doReturn MutableSharedFlow() }
        val application =
            mock<Application> {
                on {
                    getString(CommonR.string.status_add_torrent_url_failure, *emptyArray<Any>())
                } doReturn "Add failed"
                on {
                    getString(CommonR.string.status_add_torrent_file_success, *emptyArray<Any>())
                } doReturn "Added"
                on {
                    getString(
                        eq(CommonR.string.status_torrent_added_tag_cleanup_failed),
                        any<String>(),
                    )
                } doReturn "Added with a temporary tag"
            }
        return ServerViewModel(repository, prefs, dao, manager, PendingTorrentIntent(), application)
            .also { viewModels.put("server", it) }
    }

    @After
    fun clearViewModel() {
        viewModels.clear()
    }

    @Test
    fun `confirm waits for a successful magnet add before stopping and resuming it`() = runTest {
        val model = viewModel()
        assertTrue(model.prepareMagnetSelection(url))
        model.confirmAdd(emptySet(), null, null, paused = false, autoTmm = false)
        assertFalse(model.addConfirmed.value)
        assertTrue(model.confirmingAdd.value)
        verify(client, never()).toggleTorrentsState(any(), any())

        completeAdd()
        runCurrent()

        assertTrue(model.addConfirmed.value)
        assertFalse(model.confirmingAdd.value)
        verify(client).toggleTorrentsState(listOf(hash), pause = true)
        verify(client).toggleTorrentsState(listOf(hash), pause = false)
    }

    @Test
    fun `confirm surfaces an in-flight add failure without reporting success`() = runTest {
        val model = viewModel()
        model.prepareMagnetSelection(url)
        model.confirmAdd(emptySet(), null, null, paused = false, autoTmm = false)
        addResult.complete(Err(IllegalStateException()))
        runCurrent()

        assertFalse(model.addConfirmed.value)
        assertFalse(model.confirmingAdd.value)
        assertEquals("Add failed", model.fileSelection.value?.confirmationError)
        verify(client, never()).toggleTorrentsState(any(), any())
    }

    @Test
    fun `cancelling while the add is in flight waits to remove the owned preview`() = runTest {
        val model = viewModel()
        model.prepareMagnetSelection(url)
        assertTrue(prefs.data.value.notifOwnAdds.isNotEmpty())
        model.cancelFileSelection()
        verify(client, never()).removeTorrents(any(), any())

        completeAdd()
        runCurrent()

        verify(client).removeTorrents(listOf(hash), false)
        assertTrue(prefs.data.value.notifOwnAdds.isEmpty())
        verify(client, never()).toggleTorrentsState(any(), any())
    }

    @Test
    fun `persistent marker failure warns without repeating or failing the add`() = runTest {
        val model = viewModel()
        client.stub { onBlocking { deleteTags(any()) } doReturn Err(IllegalStateException()) }
        val status = async { model.status.first() }
        model.prepareMagnetSelection(url)
        model.confirmAdd(emptySet(), null, null, paused = false, autoTmm = false)
        completeAdd()
        advanceUntilIdle()

        assertTrue(model.addConfirmed.value)
        assertEquals("Added with a temporary tag", status.await())
        verify(client, times(2)).deleteTags(listOf(marker))
        verify(client).toggleTorrentsState(listOf(hash), pause = false)
    }
}
