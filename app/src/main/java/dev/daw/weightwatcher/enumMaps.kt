package dev.daw.weightwatcher

import java.util.EnumMap

/** Returns a new empty [EnumMap] with key type [K]. */
internal inline fun <reified K : Enum<K>, V> emptyEnumMap(): EnumMap<K, V> = EnumMap(K::class.java)
