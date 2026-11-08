package dev.yashgarg.qbit.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import qbittorrent.models.MainData
import qbittorrent.models.Torrent

class LegacyServerMainDataTest {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    // The key set an older server (Web API 2.5.1) sends: no content_path or seeding_time.
    private val legacyMainData =
        """
        {
          "rid": 1,
          "full_update": true,
          "torrents": {
            "abc123": {
              "added_on": 1, "amount_left": 0, "auto_tmm": false, "availability": -1,
              "category": "", "completed": 10, "completion_on": 2, "dl_limit": -1, "dlspeed": 0,
              "downloaded": 10, "downloaded_session": 0, "eta": 8640000, "f_l_piece_prio": false,
              "force_start": false, "last_activity": 3, "magnet_uri": "", "max_ratio": -1,
              "max_seeding_time": -1, "name": "legacy", "num_complete": 1, "num_incomplete": 0,
              "num_leechs": 0, "num_seeds": 0, "priority": 0, "progress": 1, "ratio": 0,
              "ratio_limit": -2, "save_path": "/dl", "seeding_time_limit": -2,
              "seen_complete": 4, "seq_dl": false, "size": 10, "state": "checkingResumeData",
              "super_seeding": false, "tags": "", "time_active": 5, "total_size": 10,
              "tracker": "", "up_limit": -1, "uploaded": 0, "uploaded_session": 0, "upspeed": 0
            }
          },
          "server_state": {
            "alltime_dl": 1, "alltime_ul": 1, "average_time_queue": 0,
            "connection_status": "connected", "dht_nodes": 0, "dl_info_data": 0,
            "dl_info_speed": 0, "dl_rate_limit": 0, "free_space_on_disk": 1,
            "global_ratio": "-", "queued_io_jobs": 0, "queueing": false,
            "read_cache_hits": "0", "read_cache_overload": "0", "refresh_interval": 1500,
            "total_buffers_size": 0, "total_peer_connections": 0, "total_queued_size": 0,
            "total_wasted_session": 0, "up_info_data": 0, "up_info_speed": 0,
            "up_rate_limit": 0, "use_alt_speed_limits": false, "write_cache_overload": "0"
          }
        }
        """
            .trimIndent()

    @Test
    fun `main data from a server without newer fields decodes`() {
        val data = json.decodeFromString(MainData.serializer(), legacyMainData)

        val torrent = data.torrents.getValue("abc123")
        assertEquals(Torrent.State.CHECKING_RESUME_DATA, torrent.state)
        assertEquals("", torrent.contentPath)
        assertEquals(0L, torrent.seedingTime)
    }
}
