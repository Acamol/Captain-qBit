package qbittorrent.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** qBittorrent reports each piece as 0 (missing), 1 (downloading) or 2 (done). */
@Serializable(with = PieceStateSerializer::class)
enum class PieceState(val value: Int) {
    MISSING(0),
    DOWNLOADING(1),
    DONE(2);

    companion object {
        /** Falls back to [MISSING] for a value this build doesn't know. */
        fun fromValue(value: Int): PieceState = entries.firstOrNull { it.value == value } ?: MISSING
    }
}

internal object PieceStateSerializer : KSerializer<PieceState> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PieceState", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): PieceState =
        PieceState.fromValue(decoder.decodeInt())

    override fun serialize(encoder: Encoder, value: PieceState) = encoder.encodeInt(value.value)
}
