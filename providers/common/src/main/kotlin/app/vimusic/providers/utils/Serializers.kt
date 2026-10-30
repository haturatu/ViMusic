package app.vimusic.providers.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

object Iso8601DateSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("Iso8601LocalDateTime", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder) = LocalDateTime.parse(decoder.decodeString().removeSuffix("Z"))
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
}

typealias SerializableIso8601Date =
@Serializable(with = Iso8601DateSerializer::class)
LocalDateTime

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
}

typealias SerializableUUID =
@Serializable(with = UUIDSerializer::class)
UUID
