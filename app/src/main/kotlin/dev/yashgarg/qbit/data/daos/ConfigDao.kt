package dev.yashgarg.qbit.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.yashgarg.qbit.data.models.ServerConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM configs") fun getConfigs(): Flow<List<ServerConfig>>

    @Query("SELECT * FROM configs WHERE config_id = :index")
    fun getConfigAtIndex(index: Int = 0): ServerConfig?

    @Query("SELECT * FROM configs WHERE config_id = :id") fun getConfigById(id: Int): ServerConfig?

    @Query("SELECT COALESCE(MAX(config_id), -1) FROM configs") fun maxConfigId(): Int

    @Query("DELETE FROM configs WHERE config_id = :id") fun deleteConfig(id: Int)

    @Query("DELETE FROM configs") fun clearConfigs()

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun addConfig(config: ServerConfig)
}
