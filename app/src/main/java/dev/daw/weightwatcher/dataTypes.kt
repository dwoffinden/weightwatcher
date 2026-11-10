package dev.daw.weightwatcher

import androidx.annotation.DrawableRes
import androidx.health.connect.client.permission.HealthPermission
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
import java.util.EnumSet
import kotlin.reflect.KClass

val SUPPORTED_DATA_TYPES: Set<DataType> =
  EnumSet.complementOf(EnumSet.of(DataType.DATA_TYPE_UNSPECIFIED, DataType.UNRECOGNIZED))

val DataType.recordClass: KClass<out Record>
  get() =
    when (this) {
      DataType.WEIGHT -> WeightRecord::class
      DataType.STEPS -> StepsRecord::class
      DataType.SLEEP -> SleepSessionRecord::class
      DataType.EXERCISE -> ExerciseSessionRecord::class
      DataType.HEART_RATE -> HeartRateRecord::class
      DataType.RESTING_HEART_RATE -> RestingHeartRateRecord::class
      DataType.BODY_FAT -> BodyFatRecord::class
      DataType.VO2_MAX -> Vo2MaxRecord::class
      DataType.BLOOD_PRESSURE -> BloodPressureRecord::class
      DataType.DATA_TYPE_UNSPECIFIED,
      DataType.UNRECOGNIZED -> throw IllegalArgumentException("Unsupported DataType: $this")
    }

val DataType.readPermission: String
  get() = HealthPermission.getReadPermission(recordClass)

val DataType.requiredPermissions: Set<String>
  get() = setOf(readPermission, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)

val DataType.displayName: String
  get() =
    when (this) {
      DataType.WEIGHT -> "Weight"
      DataType.STEPS -> "Steps"
      DataType.SLEEP -> "Sleep"
      DataType.EXERCISE -> "Exercise"
      DataType.HEART_RATE -> "Heart Rate"
      DataType.RESTING_HEART_RATE -> "Resting Heart Rate"
      DataType.BODY_FAT -> "Body Fat"
      DataType.VO2_MAX -> "VO₂ Max"
      DataType.BLOOD_PRESSURE -> "Blood Pressure"
      DataType.DATA_TYPE_UNSPECIFIED,
      DataType.UNRECOGNIZED -> throw IllegalArgumentException("Unsupported DataType: $this")
    }

/** The name as it appears mid-sentence, e.g. "Last heart rate log found". */
val DataType.nameInSentence: String
  get() =
    when (this) {
      DataType.WEIGHT -> "weight"
      DataType.STEPS -> "steps"
      DataType.SLEEP -> "sleep"
      DataType.EXERCISE -> "exercise"
      DataType.HEART_RATE -> "heart rate"
      DataType.RESTING_HEART_RATE -> "resting heart rate"
      DataType.BODY_FAT -> "body fat"
      DataType.VO2_MAX -> "VO₂ max"
      DataType.BLOOD_PRESSURE -> "blood pressure"
      DataType.DATA_TYPE_UNSPECIFIED,
      DataType.UNRECOGNIZED -> throw IllegalArgumentException("Unsupported DataType: $this")
    }

@get:DrawableRes
val DataType.iconRes: Int
  get() =
    when (this) {
      DataType.WEIGHT -> R.drawable.ic_monitor_weight
      DataType.STEPS -> R.drawable.ic_steps
      DataType.SLEEP -> R.drawable.ic_bedtime
      DataType.EXERCISE -> R.drawable.ic_exercise
      DataType.HEART_RATE -> R.drawable.ic_heart_rate
      DataType.RESTING_HEART_RATE -> R.drawable.ic_favorite
      DataType.BODY_FAT -> R.drawable.ic_percent
      DataType.VO2_MAX -> R.drawable.ic_air
      DataType.BLOOD_PRESSURE -> R.drawable.ic_blood_pressure
      DataType.DATA_TYPE_UNSPECIFIED,
      DataType.UNRECOGNIZED -> throw IllegalArgumentException("Unsupported DataType: $this")
    }
