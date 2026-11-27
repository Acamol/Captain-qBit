package dev.yashgarg.qbit.ui.settings

import androidx.datastore.core.DataStore
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import dev.yashgarg.qbit.MainDispatcherRule
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.AppLockManager
import dev.yashgarg.qbit.data.manager.AppLockSettings
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.ui.server.SpeedLimitChanges
import dev.yashgarg.qbit.ui.server.SpeedLimits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import qbittorrent.models.AltSpeedSchedule
import qbittorrent.models.SchedulerDays

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val prefs = MutableStateFlow(AppPreferences(activeServerId = 1))
    private val prefsStore =
        object : DataStore<AppPreferences> {
            override val data: Flow<AppPreferences> = prefs

            override suspend fun updateData(
                transform: suspend (t: AppPreferences) -> AppPreferences,
            ): AppPreferences = transform(prefs.value).also { prefs.value = it }
        }

    private fun server(id: Int) =
        ServerConfig(
            configId = id,
            serverName = "Server $id",
            baseUrl = "host",
            username = "",
            password = "",
            connectionType = ConnectionType.HTTP,
        )

    private val configDao: ConfigDao = mock {
        on { getConfigs() } doReturn MutableStateFlow(listOf(server(1), server(2)))
    }

    private val schedule = AltSpeedSchedule(true, 60, 120, SchedulerDays.EVERY_DAY)

    private lateinit var repository: QbitRepository

    @Before
    fun setUp() = runTest {
        repository = mock {
            onBlocking { getAutoTmmEnabled() } doReturn Ok(true)
            onBlocking { getRssRefreshInterval() } doReturn Ok(30)
            onBlocking { getRssMaxArticlesPerFeed() } doReturn Ok(50)
            onBlocking { getRssProcessingEnabled() } doReturn Ok(true)
            onBlocking { getRssAutoDownloadingEnabled() } doReturn Ok(true)
            onBlocking { isQueueingEnabled() } doReturn Ok(true)
            onBlocking { getGlobalDownloadLimit() } doReturn Ok(0)
            onBlocking { getGlobalUploadLimit() } doReturn Ok(0)
            onBlocking { getAltSpeedLimits() } doReturn Ok(0 to 0)
            onBlocking { getAltSpeedSchedule() } doReturn Ok(schedule)
        }
    }

    private val appLockManager = mock<AppLockManager>()

    private fun viewModel() = SettingsViewModel(prefsStore, repository, appLockManager, configDao)

    @Test
    fun `appearance controls follow saved preferences and preserve other settings`() = runTest {
        prefs.value =
            prefs.value.copy(
                dynamicColors = true,
                addTorrentCategory = "linux",
                compactTorrentList = false,
            )
        val appearance = viewModel()
        val otherConsumer = viewModel()

        assertEquals(true, appearance.showServerOverview.value)
        appearance.setShowServerOverview(false)

        assertEquals(false, prefs.value.showServerOverview)
        assertEquals(false, otherConsumer.showServerOverview.value)
        assertEquals(true, prefs.value.dynamicColors)
        assertEquals("linux", prefs.value.addTorrentCategory)

        appearance.setCompactTorrentList(true)

        assertEquals(true, prefs.value.compactTorrentList)
        assertEquals(true, otherConsumer.compactTorrentList.value)
        assertEquals(true, prefs.value.dynamicColors)
        assertEquals("linux", prefs.value.addTorrentCategory)

        prefsStore.updateData { it.copy(compactTorrentList = false) }

        assertEquals(false, appearance.compactTorrentList.value)
        assertEquals(false, otherConsumer.compactTorrentList.value)
        prefsStore.updateData { it.copy(showServerOverview = true) }
        assertEquals(true, appearance.showServerOverview.value)
        assertEquals(true, otherConsumer.showServerOverview.value)
    }

    @Test
    fun `opening Settings reads nothing from the server`() = runTest {
        viewModel()

        verify(repository, times(0)).getAutoTmmEnabled()
    }

    @Test
    fun `each server page reads only what it shows`() = runTest {
        viewModel().loadServerOptions()

        verify(repository).getAutoTmmEnabled()
        verify(repository).isQueueingEnabled()
        verify(repository, never()).getGlobalDownloadLimit()

        viewModel().loadSpeedLimits()

        verify(repository).getGlobalDownloadLimit()
        verify(repository).getAltSpeedLimits()
        verify(repository).getAutoTmmEnabled()
    }

    @Test
    fun `a page reads once, not again when it is shown again after a rotation`() = runTest {
        val viewModel = viewModel()

        viewModel.loadServerOptions()
        viewModel.loadServerOptions()

        verify(repository, times(1)).getAutoTmmEnabled()
        assertEquals(true, viewModel.autoTmmEnabled.value)
    }

    @Test
    fun `a partial speed limit read reports failure and a manual retry loads both caps`() =
        runTest {
            val failure = IllegalStateException("offline")
            repository.stub {
                onBlocking { getGlobalDownloadLimit() } doReturn Ok(1500)
                onBlocking { getGlobalUploadLimit() }.doReturn(Err(failure), Ok(730))
            }
            val vm = viewModel()
            vm.loadSpeedLimits()
            assertNull(vm.globalLimits.value?.get())
            assertEquals(failure, vm.globalLimits.value?.getError())
            vm.setGlobalLimits(SpeedLimitChanges(0, 0))
            verify(repository, never()).setGlobalDownloadLimit(any())
            verify(repository, never()).setGlobalUploadLimit(any())

            vm.retrySpeedLimits(alternate = false)
            assertEquals(SpeedLimits(1500, 730), vm.globalLimits.value?.get())
            verify(repository, times(1)).getAltSpeedLimits()
        }

    @Test
    fun `an unavailable alternate limit can retry without reloading the global limit`() = runTest {
        val failure = IllegalStateException("unsupported")
        repository.stub {
            onBlocking { getAltSpeedLimits() }.doReturn(Err(failure), Ok(2048 to 1024))
        }
        val vm = viewModel()
        vm.loadSpeedLimits()
        assertEquals(failure, vm.altLimits.value?.getError())
        vm.setAltLimits(SpeedLimitChanges(0, 0))
        verify(repository, never()).setAltSpeedLimits(any(), any())

        vm.retrySpeedLimits(alternate = true)
        assertEquals(SpeedLimits(2048, 1024), vm.altLimits.value?.get())
        verify(repository, times(1)).getGlobalDownloadLimit()
    }

    @Test
    fun `edits use the opened baseline and never rewrite the other cap`() = runTest {
        repository.stub {
            onBlocking { getGlobalDownloadLimit() } doReturn Ok(1500)
            onBlocking { getGlobalUploadLimit() } doReturn Ok(730)
            onBlocking { setGlobalUploadLimit(any()) } doReturn Ok(Unit)
            onBlocking { setGlobalDownloadLimit(any()) } doReturn Ok(Unit)
        }
        val vm = viewModel()
        vm.loadSpeedLimits()
        val opened = requireNotNull(vm.globalLimits.value?.get())
        vm.setGlobalLimits(SpeedLimitChanges(null, 8192))
        vm.setGlobalLimits(opened.changes("3", opened.uploadText))
        assertEquals(SpeedLimits(3072, 8192), vm.globalLimits.value?.get())
        verify(repository).setGlobalDownloadLimit(3072)
        verify(repository, times(1)).setGlobalUploadLimit(any())
    }

    @Test
    fun `returning to Options refreshes server values without reading speed limits`() = runTest {
        repository.stub {
            onBlocking { getAutoTmmEnabled() }.doReturn(Ok(true), Ok(false))
        }
        val viewModel = viewModel()

        viewModel.loadServerOptions()
        assertEquals(true, viewModel.autoTmmEnabled.value)

        viewModel.loadServerOptions(refresh = true)

        assertEquals(false, viewModel.autoTmmEnabled.value)
        verify(repository, times(2)).getAutoTmmEnabled()
        verify(repository, never()).getGlobalDownloadLimit()
    }

    @Test
    fun `every visit reads afresh, as each has a view model of its own`() = runTest {
        repository.stub {
            onBlocking { getAutoTmmEnabled() }.doReturn(Err(Exception("offline")), Ok(true))
        }

        viewModel().loadServerOptions()
        val nextVisit = viewModel()
        nextVisit.loadServerOptions()

        verify(repository, times(2)).getAutoTmmEnabled()
        assertEquals(true, nextVisit.autoTmmEnabled.value)
    }

    @Test
    fun `the speed schedule is read as the page shows it`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.altSpeedSchedule.collect {}
        }

        assertEquals(schedule, viewModel.altSpeedSchedule.value)
        verify(repository, times(1)).getAltSpeedSchedule()
    }

    private fun lockOn() {
        prefs.value =
            prefs.value.copy(
                appLockEnabled = true,
                appLockTimeoutMs = 0L,
                appLockOnScreenOff = true,
            )
    }

    @Test
    fun `a longer lock timeout asks to authenticate first`() = runTest {
        lockOn()

        viewModel().setAppLockTimeoutMs("Confirm", AppLockSettings.TIMEOUT_NEVER)

        verify(appLockManager).authenticate(eq("Confirm"), any(), any())
        assertEquals(0L, prefs.value.appLockTimeoutMs)
    }

    @Test
    fun `no longer locking on screen off asks to authenticate first`() = runTest {
        lockOn()

        viewModel().setAppLockOnScreenOff("Confirm", false)

        verify(appLockManager).authenticate(eq("Confirm"), any(), any())
        assertEquals(true, prefs.value.appLockOnScreenOff)
    }

    @Test
    fun `a stricter lock is applied without asking`() = runTest {
        prefs.value =
            prefs.value.copy(
                appLockEnabled = true,
                appLockTimeoutMs = 60_000L,
                appLockOnScreenOff = false,
            )
        val viewModel = viewModel()

        viewModel.setAppLockTimeoutMs("Confirm", 0L)
        viewModel.setAppLockOnScreenOff("Confirm", true)

        verify(appLockManager, never()).authenticate(any(), any(), any())
        assertEquals(0L, prefs.value.appLockTimeoutMs)
        assertEquals(true, prefs.value.appLockOnScreenOff)
    }
}
