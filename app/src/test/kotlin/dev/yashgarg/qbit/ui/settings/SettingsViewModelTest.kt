package dev.yashgarg.qbit.ui.settings

import androidx.datastore.core.DataStore
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import dev.yashgarg.qbit.MainDispatcherRule
import dev.yashgarg.qbit.data.QbitRepository
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.manager.AppLockManager
import dev.yashgarg.qbit.data.manager.AppLockSettings
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
