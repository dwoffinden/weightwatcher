package dev.daw.weightwatcher

import com.google.common.truth.Truth.assertThat
import java.util.EnumMap
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EnumMapsTest {

  @Test
  fun emptyEnumMap_isEmpty() {
    val map: EnumMap<DataType, String> = emptyEnumMap()
    assertThat(map).isEmpty()
  }

  @Test
  fun emptyEnumMap_iteratesInEnumOrder() {
    val map: EnumMap<DataType, Int> = emptyEnumMap()

    map[DataType.WEIGHT] = 1
    map[DataType.STEPS] = 2

    assertThat(map).containsExactly(DataType.STEPS, 2, DataType.WEIGHT, 1).inOrder()
  }
}
