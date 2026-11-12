package dev.daw.weightwatcher

import dev.daw.weightwatcher.SyncSettingsKt.stateEntry
import java.util.EnumMap

/** Returns the stored [DataTypeState] for [dataType], or `null` if none is stored. */
internal fun SyncSettings.stateFor(dataType: DataType): DataTypeState? =
  statesByTypeList.lastOrNull { it.key == dataType.proto }?.value

/** Returns a fresh [EnumMap] of the stored [DataTypeState]s indexed by [DataType]. */
internal fun SyncSettings.statesByType(): EnumMap<DataType, DataTypeState> {
  val states: EnumMap<DataType, DataTypeState> = emptyEnumMap()
  for (entry in statesByTypeList) {
    val dataType: DataType = DataType.fromProto(entry.key) ?: continue
    states[dataType] = entry.value
  }
  return states
}

/** Replaces `states_by_type` with [states]. */
internal fun SyncSettingsKt.Dsl.setStatesByType(states: Map<DataType, DataTypeState>) {
  statesByType.clear()
  for ((dataType, state) in states) {
    statesByType += stateEntry {
      key = dataType.proto
      value = state
    }
  }
}
