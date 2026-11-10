package dev.daw.weightwatcher

import androidx.health.connect.client.permission.HealthPermission
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.KotlinTestParameters.testValues
import com.google.testing.junit.testparameterinjector.KotlinTestParameters.testValuesIn
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.time.Instant
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class DataTypesTest {

  @Test
  fun supportedDataTypes_containsEveryTrackableType() {
    assertThat(SUPPORTED_DATA_TYPES)
      .containsExactly(
        DataType.WEIGHT,
        DataType.STEPS,
        DataType.SLEEP,
        DataType.EXERCISE,
        DataType.HEART_RATE,
        DataType.RESTING_HEART_RATE,
        DataType.BODY_FAT,
        DataType.VO2_MAX,
        DataType.BLOOD_PRESSURE,
      )
  }

  @Test
  fun recordClass_supportedType_matchesRecordsOfThatType(
    @TestParameter dataType: DataType = testValuesIn(SUPPORTED_DATA_TYPES)
  ) {
    val record = sampleRecordOf(dataType, Instant.parse("2026-01-01T10:00:00Z"))

    assertThat(record).isInstanceOf(dataType.recordClass.java)
  }

  @Test
  fun recordClass_unsupportedType_throws(
    @TestParameter
    dataType: DataType = testValues(DataType.DATA_TYPE_UNSPECIFIED, DataType.UNRECOGNIZED)
  ) {
    assertFailsWith<IllegalArgumentException> { dataType.recordClass }
  }

  @Test
  fun readPermission_supportedType_isHealthConnectReadPermission(
    @TestParameter dataType: DataType = testValuesIn(SUPPORTED_DATA_TYPES)
  ) {
    assertThat(dataType.readPermission).startsWith("android.permission.health.READ_")
  }

  @Test
  fun readPermission_supportedTypes_areDistinct() {
    val permissions: List<String> = SUPPORTED_DATA_TYPES.map { it.readPermission }

    assertThat(permissions).containsNoDuplicates()
  }

  @Test
  fun readPermission_unsupportedType_throws(
    @TestParameter
    dataType: DataType = testValues(DataType.DATA_TYPE_UNSPECIFIED, DataType.UNRECOGNIZED)
  ) {
    assertFailsWith<IllegalArgumentException> { dataType.readPermission }
  }

  @Test
  fun requiredPermissions_supportedType_addsBackgroundReadPermission(
    @TestParameter dataType: DataType = testValuesIn(SUPPORTED_DATA_TYPES)
  ) {
    assertThat(dataType.requiredPermissions)
      .containsExactly(
        dataType.readPermission,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
      )
  }

  @Test
  fun displayName_supportedType_isNotBlank(
    @TestParameter dataType: DataType = testValuesIn(SUPPORTED_DATA_TYPES)
  ) {
    assertThat(dataType.displayName.trim()).isNotEmpty()
  }

  @Test
  fun displayName_supportedTypes_areDistinct() {
    val names: List<String> = SUPPORTED_DATA_TYPES.map { it.displayName }

    assertThat(names).containsNoDuplicates()
  }

  @Test
  fun displayName_unsupportedType_throws(
    @TestParameter
    dataType: DataType = testValues(DataType.DATA_TYPE_UNSPECIFIED, DataType.UNRECOGNIZED)
  ) {
    assertFailsWith<IllegalArgumentException> { dataType.displayName }
  }

  @Test
  fun nameInSentence_supportedTypes_areDistinct() {
    val names: List<String> = SUPPORTED_DATA_TYPES.map { it.nameInSentence }

    assertThat(names).containsNoDuplicates()
  }

  @Test
  fun nameInSentence_supportedType_matchesDisplayNameIgnoringCase(
    @TestParameter dataType: DataType = testValuesIn(SUPPORTED_DATA_TYPES)
  ) {
    assertThat(dataType.nameInSentence).ignoringCase().isEqualTo(dataType.displayName)
  }

  @Test
  fun nameInSentence_unsupportedType_throws(
    @TestParameter
    dataType: DataType = testValues(DataType.DATA_TYPE_UNSPECIFIED, DataType.UNRECOGNIZED)
  ) {
    assertFailsWith<IllegalArgumentException> { dataType.nameInSentence }
  }

  @Test
  fun iconRes_supportedTypes_areDistinct() {
    val icons: List<Int> = SUPPORTED_DATA_TYPES.map { it.iconRes }

    assertThat(icons).containsNoDuplicates()
  }

  @Test
  fun iconRes_unsupportedType_throws(
    @TestParameter
    dataType: DataType = testValues(DataType.DATA_TYPE_UNSPECIFIED, DataType.UNRECOGNIZED)
  ) {
    assertFailsWith<IllegalArgumentException> { dataType.iconRes }
  }
}
