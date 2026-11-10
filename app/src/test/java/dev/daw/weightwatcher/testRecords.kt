package dev.daw.weightwatcher

import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import java.time.Instant
import java.time.ZoneOffset

/** Returns a record of [dataType] taken at [time] (an interval record ending at [time]). */
fun sampleRecordOf(dataType: DataType, time: Instant): Record {
  val startTime: Instant = time - 1.hours
  return when (dataType) {
    DataType.WEIGHT ->
      WeightRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        weight = Mass.kilograms(70.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.STEPS ->
      StepsRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        count = 100,
        metadata = Metadata.manualEntry(),
      )
    DataType.SLEEP ->
      SleepSessionRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        metadata = Metadata.manualEntry(),
      )
    DataType.EXERCISE ->
      ExerciseSessionRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        metadata = Metadata.manualEntry(),
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
      )
    DataType.HEART_RATE ->
      HeartRateRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        samples = listOf(HeartRateRecord.Sample(time = time, beatsPerMinute = 60)),
        metadata = Metadata.manualEntry(),
      )
    DataType.RESTING_HEART_RATE ->
      RestingHeartRateRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        beatsPerMinute = 55,
        metadata = Metadata.manualEntry(),
      )
    DataType.BODY_FAT ->
      BodyFatRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        percentage = Percentage(15.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.VO2_MAX ->
      Vo2MaxRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        vo2MillilitersPerMinuteKilogram = 45.0,
        metadata = Metadata.manualEntry(),
      )
    DataType.BLOOD_PRESSURE ->
      BloodPressureRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        systolic = Pressure.millimetersOfMercury(120.0),
        diastolic = Pressure.millimetersOfMercury(80.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.DATA_TYPE_UNSPECIFIED,
    DataType.UNRECOGNIZED -> throw IllegalArgumentException("Unsupported DataType: $dataType")
  }
}
