package dev.daw.weightwatcher

import com.google.common.truth.Truth.assertThat
import dev.daw.weightwatcher.DataTypeState.Status
import dev.daw.weightwatcher.SyncSettings.DataType as ProtoDataType
import dev.daw.weightwatcher.SyncSettings.StateEntry
import java.util.EnumMap
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SyncStateExtensionsTest {

  @Test
  fun stateFor_presentType_returnsLastMatchingState() {
    val olderWeightState = dataTypeState { status = Status.NO_PERMISSIONS }
    val newerWeightState = dataTypeState { status = Status.OK }
    val settings = syncSettings {
      statesByType += stateEntry(ProtoDataType.WEIGHT to olderWeightState)
      statesByType +=
        stateEntry(ProtoDataType.STEPS to dataTypeState { status = Status.NO_PERMISSIONS })
      statesByType += stateEntry(ProtoDataType.WEIGHT to newerWeightState)
    }

    assertThat(settings.stateFor(DataType.WEIGHT)).isEqualTo(newerWeightState)
  }

  @Test
  fun stateFor_absentType_returnsNull() {
    val settings = syncSettings {
      statesByType += stateEntry(ProtoDataType.WEIGHT to dataTypeState { status = Status.OK })
    }
    assertThat(settings.stateFor(DataType.STEPS)).isNull()
  }

  @Test
  fun statesByType_returnsKnownTypes() {
    val weightState = dataTypeState { status = Status.OK }
    val stepsState = dataTypeState { status = Status.NO_PERMISSIONS }
    val settings = syncSettings {
      statesByType += stateEntry(ProtoDataType.WEIGHT to weightState)
      statesByType += stateEntry(ProtoDataType.STEPS to stepsState)
    }

    val result: EnumMap<DataType, DataTypeState> = settings.statesByType()

    assertThat(result).containsExactly(DataType.WEIGHT, weightState, DataType.STEPS, stepsState)
  }

  @Test
  fun statesByType_skipsUnspecifiedType() {
    val settings = syncSettings {
      statesByType +=
        stateEntry(ProtoDataType.DATA_TYPE_UNSPECIFIED to dataTypeState { status = Status.OK })
    }
    assertThat(settings.statesByType()).isEmpty()
  }

  @Test
  fun setStatesByType_replacesEntriesPreservingOtherFields() {
    val weightState = dataTypeState { status = Status.OK }
    val stepsState = dataTypeState { status = Status.NO_PERMISSIONS }
    val initial = syncSettings {
      legacyWidgetsMigrated = true
      statesByType += stateEntry(ProtoDataType.SLEEP to dataTypeState { status = Status.OK })
    }

    val updated: SyncSettings = initial.copy {
      setStatesByType(mapOf(DataType.WEIGHT to weightState, DataType.STEPS to stepsState))
    }

    assertThat(updated.legacyWidgetsMigrated).isTrue()
    assertThat(updated.statesByType())
      .containsExactly(DataType.STEPS, stepsState, DataType.WEIGHT, weightState)
      .inOrder()
  }

  private fun stateEntry(entry: Pair<ProtoDataType, DataTypeState>): StateEntry =
    SyncSettingsKt.stateEntry {
      key = entry.first
      value = entry.second
    }
}
