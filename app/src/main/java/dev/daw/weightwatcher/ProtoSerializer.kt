package dev.daw.weightwatcher

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.GeneratedMessageLite
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * A generic DataStore [Serializer] for any generated Protobuf Lite message.
 *
 * `T` is bounded by [GeneratedMessageLite] rather than [com.google.protobuf.MessageLite] because
 * only the former's `getParserForType` returns a `Parser<T>`.
 */
class ProtoSerializer<T : GeneratedMessageLite<T, *>>(override val defaultValue: T) :
  Serializer<T> {
  override suspend fun readFrom(input: InputStream): T =
    try {
      defaultValue.parserForType.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read proto.", exception)
    }

  override suspend fun writeTo(t: T, output: OutputStream) = t.writeTo(output)
}
