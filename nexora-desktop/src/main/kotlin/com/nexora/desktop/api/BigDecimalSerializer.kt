package com.nexora.desktop.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/**
 * Reads and writes money and weight as exact decimals.
 *
 * The API sends money as a JSON number such as `12500000.50`. Letting the JSON library turn that into a Double
 * would store 12500000.5 as the nearest binary fraction, and repeated sums could drift by a paisa. Instead we
 * take the number's literal text from the JSON and build a BigDecimal from it, so the value never passes
 * through Double or Float (NFR-DATA-01).
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        // JsonDecoder gives us the raw JSON element; `content` is the number exactly as the server wrote it.
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return BigDecimal(element.jsonPrimitive.content)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        // An unquoted literal writes `45000.00` as a JSON number (not the string "45000.00"), which the API expects.
        // toPlainString avoids scientific notation such as 1E+5.
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }
}

/** Use `Decimal` instead of `BigDecimal` in DTOs so every money/weight field gets the exact serializer. */
typealias Decimal = @Serializable(with = BigDecimalSerializer::class) BigDecimal
