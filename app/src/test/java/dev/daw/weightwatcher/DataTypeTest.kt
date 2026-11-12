package dev.daw.weightwatcher

import androidx.health.connect.client.permission.HealthPermission
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.KotlinTestParameters.testValues
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import dev.daw.weightwatcher.SyncSettings.DataType as ProtoDataType
import java.time.Instant
import java.util.EnumSet
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class DataTypeTest {

  @Test
  fun proto_coversEveryDefinedProtoDataType() {
    val definedProtoTypes: Set<ProtoDataType> =
      EnumSet.complementOf(
        EnumSet.of(ProtoDataType.DATA_TYPE_UNSPECIFIED, ProtoDataType.UNRECOGNIZED)
      )

    assertThat(DataType.entries.map { it.proto }).containsExactlyElementsIn(definedProtoTypes)
  }

  @Test
  fun protoName_matchesEnumName(@TestParameter dataType: DataType) {
    assertThat(dataType.proto.name).isEqualTo(dataType.name)
  }

  @Test
  fun fromProto_validProto_returnsDataType(@TestParameter dataType: DataType) {
    assertThat(DataType.fromProto(dataType.proto)).isEqualTo(dataType)
  }

  @Test
  fun fromProto_unsupportedProto_returnsNull(
    @TestParameter
    proto: ProtoDataType =
      testValues(ProtoDataType.DATA_TYPE_UNSPECIFIED, ProtoDataType.UNRECOGNIZED)
  ) {
    assertThat(DataType.fromProto(proto)).isNull()
  }

  @Test
  fun recordClass_matchesRecordsOfThatType(@TestParameter dataType: DataType) {
    val record = sampleRecordOf(dataType, Instant.parse("2026-01-01T10:00:00Z"))

    assertThat(record).isInstanceOf(dataType.recordClass.java)
  }

  @Test
  fun readPermission_matchesEnumName(@TestParameter dataType: DataType) {
    assertThat(dataType.readPermission).isEqualTo("android.permission.health.READ_${dataType.name}")
  }

  @Test
  fun readPermission_areDistinct() {
    val permissions: List<String> = DataType.entries.map { it.readPermission }

    assertThat(permissions).containsNoDuplicates()
  }

  @Test
  fun requiredPermissions_addsBackgroundReadPermission(@TestParameter dataType: DataType) {
    assertThat(dataType.requiredPermissions)
      .containsExactly(
        dataType.readPermission,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
      )
  }

  @Test
  fun displayName_isNotBlank(@TestParameter dataType: DataType) {
    assertThat(dataType.displayName.trim()).isNotEmpty()
  }

  @Test
  fun displayName_areDistinct() {
    val names: List<String> = DataType.entries.map { it.displayName }

    assertThat(names).containsNoDuplicates()
  }

  @Test
  fun nameInSentence_matchesDisplayNameIgnoringCase(@TestParameter dataType: DataType) {
    assertThat(dataType.nameInSentence).ignoringCase().isEqualTo(dataType.displayName)
  }

  @Test
  fun nameInSentence_areDistinct() {
    val names: List<String> = DataType.entries.map { it.nameInSentence }

    assertThat(names).containsNoDuplicates()
  }

  @Test
  fun iconRes_areDistinct() {
    val icons: List<Int> = DataType.entries.map { it.iconRes }

    assertThat(icons).containsNoDuplicates()
  }
}
