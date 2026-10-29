package dev.yashgarg.qbit.data.backup

import dev.yashgarg.qbit.data.manager.AppLockSettings
import dev.yashgarg.qbit.data.manager.LockWeakening
import dev.yashgarg.qbit.data.models.AppPreferences
import dev.yashgarg.qbit.data.models.ConnectionType
import dev.yashgarg.qbit.data.models.ServerConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefGroupTest {

    private val privacy =
        AppPreferences(
            appLockEnabled = true,
            appLockTimeoutMs = 0L,
            appLockOnScreenOff = false,
            hideInRecents = true,
            blockScreenshots = true,
        )

    @Test
    fun `privacy group round-trips every privacy setting`() {
        val restored =
            AppPreferences()
                .overlayGroup(PrefGroup.PRIVACY, privacy.extractGroups(setOf(PrefGroup.PRIVACY)))

        assertEquals(privacy, restored)
    }

    @Test
    fun `other groups leave privacy settings alone`() {
        val snapshot = privacy.extractGroups(PrefGroup.entries.toSet() - PrefGroup.PRIVACY)

        assertFalse(snapshot.appLockEnabled)
        assertFalse(snapshot.hideInRecents)
    }

    @Test
    fun `backup from before groups existed does not carry privacy`() {
        val legacy = ConfigBackup(servers = emptyList(), preferences = AppPreferences())

        val groups = legacy.availablePrefGroups()

        assertFalse(PrefGroup.PRIVACY in groups)
        assertTrue(PrefGroup.APPEARANCE in groups)
    }

    @Test
    fun `privacy group is written to its own field so older versions can read the backup`() {
        val backup =
            configBackupOf(
                servers = emptyList(),
                prefs = privacy,
                groups = setOf(PrefGroup.APPEARANCE, PrefGroup.PRIVACY),
                categoryColors = null,
            )

        val json = Json.encodeToString(ConfigBackup.serializer(), backup)

        assertEquals(setOf(PrefGroup.APPEARANCE), backup.preferenceGroups)
        assertFalse(backup.preferences!!.appLockEnabled)
        assertTrue(backup.privacyPreferences!!.appLockEnabled)
        assertFalse("\"PRIVACY\"" in json)
        assertEquals(setOf(PrefGroup.APPEARANCE, PrefGroup.PRIVACY), backup.availablePrefGroups())
    }

    @Test
    fun `privacy-only backup leaves the shared preferences empty`() {
        val backup =
            configBackupOf(emptyList(), privacy, setOf(PrefGroup.PRIVACY), categoryColors = null)

        assertEquals(null, backup.preferences)
        assertEquals(setOf(PrefGroup.PRIVACY), backup.availablePrefGroups())
    }

    /** The shape of a backup as the release before the privacy group read it. */
    @Serializable
    private enum class LegacyGroup {
        APPEARANCE,
        NOTIFICATIONS,
        FILTERS,
    }

    @Serializable
    private data class LegacyBackup(
        val servers: List<ServerConfig>,
        val preferences: AppPreferences? = null,
        val preferenceGroups: Set<LegacyGroup> = emptySet(),
        val categoryColors: Map<String, Int>? = null,
    )

    private val lenientJson = Json { ignoreUnknownKeys = true }

    private fun server() =
        ServerConfig(
            configId = 1,
            serverName = "Home",
            baseUrl = "host",
            username = "",
            password = "",
            connectionType = ConnectionType.HTTP,
        )

    @Test
    fun `an older version reads a backup that carries the privacy group`() {
        val backup =
            configBackupOf(
                servers = listOf(server()),
                prefs = privacy,
                groups = PrefGroup.entries.toSet(),
                categoryColors = null,
            )

        val legacy =
            lenientJson.decodeFromString<LegacyBackup>(
                lenientJson.encodeToString(ConfigBackup.serializer(), backup),
            )

        assertEquals(
            setOf(LegacyGroup.APPEARANCE, LegacyGroup.NOTIFICATIONS, LegacyGroup.FILTERS),
            legacy.preferenceGroups,
        )
        assertFalse(legacy.preferences!!.appLockEnabled)
    }

    @Test
    fun `an older version restores nothing from a privacy-only backup`() {
        val backup =
            configBackupOf(
                listOf(server()),
                privacy,
                setOf(PrefGroup.PRIVACY),
                categoryColors = null,
            )

        val legacy =
            lenientJson.decodeFromString<LegacyBackup>(
                lenientJson.encodeToString(ConfigBackup.serializer(), backup),
            )

        // An older version treats a backup with preferences but no groups as carrying all of them.
        assertEquals(null, legacy.preferences)
        assertTrue(legacy.preferenceGroups.isEmpty())
    }

    private val strictLock = AppLockSettings(enabled = true, timeoutMs = 0L, lockOnScreenOff = true)

    private fun backupWithLock(
        enabled: Boolean = true,
        timeoutMs: Long = 0L,
        lockOnScreenOff: Boolean = true,
    ) =
        configBackupOf(
            listOf(server()),
            privacy.copy(
                appLockEnabled = enabled,
                appLockTimeoutMs = timeoutMs,
                appLockOnScreenOff = lockOnScreenOff,
            ),
            PrefGroup.entries.toSet(),
            categoryColors = null,
        )

    private val all = PrefGroup.entries.toSet()

    @Test
    fun `a backup with the lock off is a lock being turned off`() {
        assertEquals(
            LockWeakening.TURNED_OFF,
            backupWithLock(enabled = false).appLockWeakening(all, strictLock),
        )
    }

    @Test
    fun `a backup that locks later or not on screen off loosens the lock`() {
        assertEquals(
            LockWeakening.LOOSENED,
            backupWithLock(timeoutMs = AppLockSettings.TIMEOUT_NEVER)
                .appLockWeakening(all, strictLock),
        )
        assertEquals(
            LockWeakening.LOOSENED,
            backupWithLock(lockOnScreenOff = false).appLockWeakening(all, strictLock),
        )
        // Any negative timeout never locks, not just the one the app writes for "never".
        assertEquals(
            LockWeakening.LOOSENED,
            backupWithLock(timeoutMs = -2L).appLockWeakening(all, strictLock),
        )
    }

    @Test
    fun `a backup with the same or a stricter lock needs no confirmation`() {
        val relaxed = strictLock.copy(timeoutMs = 60_000L, lockOnScreenOff = false)

        assertEquals(null, backupWithLock().appLockWeakening(all, strictLock))
        assertEquals(null, backupWithLock().appLockWeakening(all, relaxed))
    }

    @Test
    fun `nothing needs confirming when the lock is already off`() {
        val off = strictLock.copy(enabled = false)

        assertEquals(null, backupWithLock(enabled = false).appLockWeakening(all, off))
    }

    @Test
    fun `nothing needs confirming when the privacy group isn't imported`() {
        val withoutPrivacy = all - PrefGroup.PRIVACY

        assertEquals(
            null,
            backupWithLock(enabled = false).appLockWeakening(withoutPrivacy, strictLock),
        )
    }

    @Test
    fun `a backup from before the privacy group never weakens the lock`() {
        val legacy = ConfigBackup(servers = listOf(server()), preferences = AppPreferences())

        assertEquals(null, legacy.appLockWeakening(all, strictLock))
    }
}
