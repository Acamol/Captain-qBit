package dev.yashgarg.qbit.ui.rss

import android.app.Application
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.yashgarg.qbit.MainDispatcherRule
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.ui.navigation.Routes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import qbittorrent.models.RssItem
import qbittorrent.models.RssRule
import qbittorrent.models.parseRssTree

@OptIn(ExperimentalCoroutinesApi::class)
class RssViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val viewModels = ViewModelStore()

    @Before
    fun allowLifecycleObservation() {
        ArchTaskExecutor.getInstance()
            .setDelegate(
                object : TaskExecutor() {
                    override fun executeOnDiskIO(runnable: Runnable) = runnable.run()

                    override fun postToMainThread(runnable: Runnable) = runnable.run()

                    override fun isMainThread() = true
                },
            )
    }

    @After
    fun resetExecutor() {
        viewModels.clear()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    private val application =
        mock<Application> {
            on {
                getString(CommonR.string.status_rss_rules_load_failure, *emptyArray<Any>())
            } doReturn "Rules unavailable"
            on {
                getString(CommonR.string.status_rss_load_failure, *emptyArray<Any>())
            } doReturn "Feeds unavailable"
            on {
                getString(CommonR.string.status_rss_rule_saved, *emptyArray<Any>())
            } doReturn "Rule saved"
            on {
                getString(CommonR.string.status_rss_save_rule_failure, *emptyArray<Any>())
            } doReturn "Save failed"
            on { getString(CommonR.string.unknown_error, *emptyArray<Any>()) } doReturn "Failed"
        }
    private val feedUrl = "https://example.org/feed.xml"
    private val items =
        parseRssTree(
            Json.parseToJsonElement(
                    """{"News":{"Example":{"uid":"1","url":"$feedUrl","articles":[]}}}""",
                )
                .jsonObject,
        )
    private val repository =
        mock<QbitRepository> {
            onBlocking { getRssItems() } doReturn Ok(items)
            onBlocking { getRssRules() } doReturn Err(IllegalStateException())
            onBlocking { getRssRefreshInterval() } doReturn Ok(30)
            onBlocking { getRssProcessingEnabled() } doReturn Ok(true)
            onBlocking { getRssAutoDownloadingEnabled() } doReturn Ok(false)
        }

    private fun viewModel(editingName: String? = null) =
        RssViewModel(
                repository,
                SavedStateHandle(mapOf(Routes.ARG_RSS_RULE_NAME to editingName)),
                application,
            )
            .also {
                viewModels.put("rss", it)
            }

    @Test
    fun `failed matching article lookups complete the callback and can be retried`() = runTest {
        val matches = mapOf(feedUrl to listOf("Episode 1"))
        repository.stub {
            onBlocking { getRssMatchingArticles("weekly") }
                .doReturn(Err(IllegalStateException()), Ok(matches))
        }
        val model = viewModel("weekly")
        val results = mutableListOf<Map<String, List<String>>?>()

        model.loadMatchingArticles("weekly") { results.add(it) }
        model.loadMatchingArticles("weekly") { results.add(it) }

        assertEquals(listOf(null, matches), results)
    }

    @Test
    fun `feeds stay ready while rules load and after rules fail`() = runTest {
        val response = CompletableDeferred<Result<Map<String, RssRule>, Throwable>>()
        repository.stub {
            onBlocking { getRssRules() } doSuspendableAnswer { response.await() }
        }
        val viewModel = viewModel()
        assertEquals(RssLoad.Ready, viewModel.uiState.value.itemsLoad)
        assertEquals(RssLoad.Loading, viewModel.uiState.value.rulesLoad)

        response.complete(Err(IllegalStateException()))
        runCurrent()

        assertEquals(RssLoad.Failed("Rules unavailable"), viewModel.uiState.value.rulesLoad)
        assertEquals(RssLoad.Ready, viewModel.uiState.value.itemsLoad)
        assertEquals(feedUrl, viewModel.uiState.value.items.flattenFeeds().single().url)
    }

    @Test
    fun `editing cannot overwrite a rule before a successful retry finds the original`() = runTest {
        val response = CompletableDeferred<Result<Map<String, RssRule>, Throwable>>()
        repository.stub { onBlocking { getRssRules() } doSuspendableAnswer { response.await() } }
        val viewModel = viewModel("weekly")
        var saved = false
        fun trySave(rule: RssRule) = viewModel.saveEditorRule("weekly", rule) { saved = true }

        trySave(RssRule())
        response.complete(Err(IllegalStateException()))
        runCurrent()
        trySave(RssRule())

        repository.stub { onBlocking { getRssRules() } doReturn Ok(emptyMap()) }
        viewModel.refreshRules()
        assertEquals(RssLoad.Ready, viewModel.uiState.value.rulesLoad)
        trySave(RssRule())
        verify(repository, never()).setRssRule(any(), any())
        assertFalse(saved)

        val original =
            Json.decodeFromString<RssRule>(
                """{"mustContain":"weekly","smartFilter":true,"previouslyMatchedEpisodes":["1x01"]}""",
            )
        repository.stub {
            onBlocking { getRssRules() } doReturn Ok(mapOf("weekly" to original))
            onBlocking { setRssRule(any(), any()) } doReturn Ok(Unit)
        }
        viewModel.refreshRules()
        trySave(RssRule())
        verify(repository, never()).setRssRule(any(), any())
        val edited = viewModel.uiState.value.rules.getValue("weekly").copy(ignoreDays = 3)
        trySave(edited)
        verify(repository).setRssRule("weekly", edited)
        assertTrue(saved)
    }

    @Test
    fun `new rule cannot replace a known name`() = runTest {
        repository.stub { onBlocking { getRssRules() } doReturn Ok(mapOf("weekly" to RssRule())) }
        val viewModel = viewModel()
        viewModel.saveEditorRule(" weekly ", RssRule()) { error("must not save") }
        verify(repository, never()).setRssRule(any(), any())
    }

    @Test
    fun `list toggle preserves server matches received since the list was loaded`() = runTest {
        val original =
            Json.decodeFromString<RssRule>(
                """{"enabled":true,"mustContain":"series","previouslyMatchedEpisodes":["1x01"]}""",
            )
        repository.stub { onBlocking { getRssRules() } doReturn Ok(mapOf("series" to original)) }
        val viewModel = viewModel()
        val latest =
            Json.decodeFromString<RssRule>(
                """{"enabled":true,"mustContain":"series","previouslyMatchedEpisodes":["1x01","1x02"]}""",
            )
        repository.stub {
            onBlocking { getRssRules() } doReturn Ok(mapOf("series" to latest))
            onBlocking { setRssRule(any(), any()) } doReturn Ok(Unit)
        }
        viewModel.toggleRule("series", false)
        val posted = argumentCaptor<RssRule>()
        verify(repository).setRssRule(eq("series"), posted.capture())
        assertEquals(
            Json.encodeToJsonElement(RssRule.serializer(), latest.copy(enabled = false)),
            Json.encodeToJsonElement(RssRule.serializer(), posted.firstValue),
        )
    }

    @Test
    fun `feed picker only changes listed URLs and cannot apply an incomplete feed load`() =
        runTest {
            val response = CompletableDeferred<Result<List<RssItem>, Throwable>>()
            repository.stub {
                onBlocking { getRssItems() } doSuspendableAnswer { response.await() }
                onBlocking { getRssRules() } doReturn Ok(mapOf("weekly" to RssRule()))
            }
            val viewModel = viewModel("weekly")
            val assigned = listOf("https://example.org/unlisted.xml", feedUrl)
            fun unchecked() =
                viewModel.uiState.value.let { state ->
                    state.applyFeedSelection(
                        assigned,
                        state.items.flattenFeeds().map { it.url },
                        emptySet(),
                    )
                }
            assertTrue(viewModel.uiState.value.canSaveRule("weekly", "weekly"))
            assertNull(unchecked())
            response.complete(Err(IllegalStateException()))
            runCurrent()
            assertEquals(RssLoad.Failed("Feeds unavailable"), viewModel.uiState.value.itemsLoad)
            assertNull(unchecked())
            repository.stub { onBlocking { getRssItems() } doReturn Ok(items) }
            viewModel.refreshItems()
            assertEquals(listOf(assigned.first()), unchecked())
        }
}
