package dev.yashgarg.qbit.data.backup

import androidx.datastore.core.DataStore
import dev.yashgarg.qbit.data.daos.ConfigDao
import dev.yashgarg.qbit.data.models.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class BackupCategoryColorsTest {
    private val prefs = MutableStateFlow(AppPreferences())
    private val prefsStore =
        object : DataStore<AppPreferences> {
            override val data: Flow<AppPreferences> = prefs

            override suspend fun updateData(
                transform: suspend (t: AppPreferences) -> AppPreferences,
            ): AppPreferences = transform(prefs.value).also { prefs.value = it }
        }
    private val dao = mock<ConfigDao> { on { getConfigs() } doReturn flowOf(emptyList()) }
    private val manager = BackupManager(mock(), dao, prefsStore, mock())

    private suspend fun restore(backup: ConfigBackup) {
        val json = Json { ignoreUnknownKeys = true }
        val decoded =
            json.decodeFromString(
                ConfigBackup.serializer(),
                json.encodeToString(ConfigBackup.serializer(), backup),
            )
        manager.applyImport(decoded, emptyList(), emptySet(), true, ImportMode.MERGE)
    }

    @Test
    fun `restores colors together with which ones are auto`() = runTest {
        val colors = mapOf("movies" to 0xFF336699.toInt(), "tv" to 0xFF993366.toInt())
        prefs.value =
            AppPreferences(categoryColors = mapOf("old" to 1), autoCategoryColors = setOf("old"))

        restore(configBackupOf(emptyList(), null, emptySet(), colors, setOf("tv")))

        assertEquals(colors, prefs.value.categoryColors)
        assertEquals(setOf("tv"), prefs.value.autoCategoryColors)
    }

    @Test
    fun `backup from before auto colors restores every color as user-picked`() = runTest {
        prefs.value = AppPreferences(autoCategoryColors = setOf("movies"))

        restore(ConfigBackup(emptyList(), categoryColors = mapOf("movies" to 1)))

        assertEquals(mapOf("movies" to 1), prefs.value.categoryColors)
        assertEquals(emptySet<String>(), prefs.value.autoCategoryColors)
    }
}
