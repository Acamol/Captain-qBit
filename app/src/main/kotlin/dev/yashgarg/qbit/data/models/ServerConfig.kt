package dev.yashgarg.qbit.data.models

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Keep
@Serializable
@Entity(tableName = "configs")
data class ServerConfig(
    @PrimaryKey @ColumnInfo("config_id") val configId: Int,
    val serverName: String,
    val baseUrl: String,
    val port: Int? = null,
    val path: String? = null,
    val username: String,
    val password: String,
    val connectionType: ConnectionType,
    val basicAuthUsername: String? = null,
    val basicAuthPassword: String? = null,
    val position: Int = 0,
    // Base64-encoded DER of a self-signed certificate the user has explicitly approved for this
    // server only (see ClientManager.buildOkHttpClient). Not a hash: HandshakeCertificates needs
    // an actual certificate object to add as a trust anchor.
    val pinnedCertificate: String? = null,
)
