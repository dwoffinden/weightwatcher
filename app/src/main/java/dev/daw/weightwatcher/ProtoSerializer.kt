package dev.daw.weightwatcher

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import java.io.InputStream
import java.io.OutputStream

/** A generic DataStore [Serializer] for any Protobuf [MessageLite]. */
class ProtoSerializer<T : MessageLite>(
  override val defaultValue: T,
  private val parser: Parser<T>,
) : Serializer<T> {
  override suspend fun readFrom(input: InputStream): T =
    try {
      parser.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read proto.", exception)
    }

  override suspend fun writeTo(t: T, output: OutputStream) = t.writeTo(output)
}
