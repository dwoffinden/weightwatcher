package dev.daw.weightwatcher

import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Parser
import java.io.InputStream

/** Parses [bytes] with this [Parser], falling back to [onInvalid] on malformed wire data. */
internal inline fun <T> Parser<T>.parseOr(
  bytes: ByteArray,
  onInvalid: (InvalidProtocolBufferException) -> T,
): T =
  try {
    parseFrom(bytes)
  } catch (e: InvalidProtocolBufferException) {
    onInvalid(e)
  }

/** Parses [input] with this [Parser], falling back to [onInvalid] on malformed wire data. */
internal inline fun <T> Parser<T>.parseOr(
  input: InputStream,
  onInvalid: (InvalidProtocolBufferException) -> T,
): T =
  try {
    parseFrom(input)
  } catch (e: InvalidProtocolBufferException) {
    onInvalid(e)
  }
