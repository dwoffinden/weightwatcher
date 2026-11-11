package dev.daw.weightwatcher

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ActivityIntensityRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.units.Volume
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
    DataType.HEART_RATE_VARIABILITY ->
      HeartRateVariabilityRmssdRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        heartRateVariabilityMillis = 42.0,
        metadata = Metadata.manualEntry(),
      )
    DataType.OXYGEN_SATURATION ->
      OxygenSaturationRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        percentage = Percentage(98.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.RESPIRATORY_RATE ->
      RespiratoryRateRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        rate = 14.0,
        metadata = Metadata.manualEntry(),
      )
    DataType.SKIN_TEMPERATURE ->
      SkinTemperatureRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        metadata = Metadata.manualEntry(),
        deltas = listOf(SkinTemperatureRecord.Delta(startTime, TemperatureDelta.celsius(0.2))),
      )
    DataType.DISTANCE ->
      DistanceRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        distance = Length.meters(1000.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.ACTIVE_CALORIES_BURNED ->
      ActiveCaloriesBurnedRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        energy = Energy.kilocalories(250.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.TOTAL_CALORIES_BURNED ->
      TotalCaloriesBurnedRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        energy = Energy.kilocalories(2000.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.FLOORS_CLIMBED ->
      FloorsClimbedRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        floors = 5.0,
        metadata = Metadata.manualEntry(),
      )
    DataType.ELEVATION_GAINED ->
      ElevationGainedRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        elevation = Length.meters(50.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.ACTIVITY_INTENSITY ->
      ActivityIntensityRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        metadata = Metadata.manualEntry(),
        activityIntensityType = ActivityIntensityRecord.ACTIVITY_INTENSITY_TYPE_MODERATE,
      )
    DataType.HYDRATION ->
      HydrationRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        volume = Volume.milliliters(500.0),
        metadata = Metadata.manualEntry(),
      )
    DataType.NUTRITION ->
      NutritionRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        metadata = Metadata.manualEntry(),
        energy = Energy.kilocalories(500.0),
      )
    DataType.BLOOD_GLUCOSE ->
      BloodGlucoseRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        metadata = Metadata.manualEntry(),
        level = BloodGlucose.millimolesPerLiter(5.5),
      )
    DataType.MINDFULNESS ->
      MindfulnessSessionRecord(
        startTime = startTime,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time,
        endZoneOffset = ZoneOffset.UTC,
        metadata = Metadata.manualEntry(),
        mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
      )
    DataType.HEIGHT ->
      HeightRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        height = Length.meters(1.75),
        metadata = Metadata.manualEntry(),
      )
    DataType.DATA_TYPE_UNSPECIFIED,
    DataType.UNRECOGNIZED -> throw IllegalArgumentException("Unsupported DataType: $dataType")
  }
}
