package dev.daw.weightwatcher

import androidx.annotation.DrawableRes
import androidx.health.connect.client.permission.HealthPermission
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
      DataType.HEART_RATE_VARIABILITY -> HeartRateVariabilityRmssdRecord::class
      DataType.OXYGEN_SATURATION -> OxygenSaturationRecord::class
      DataType.RESPIRATORY_RATE -> RespiratoryRateRecord::class
      DataType.SKIN_TEMPERATURE -> SkinTemperatureRecord::class
      DataType.DISTANCE -> DistanceRecord::class
      DataType.ACTIVE_CALORIES_BURNED -> ActiveCaloriesBurnedRecord::class
      DataType.TOTAL_CALORIES_BURNED -> TotalCaloriesBurnedRecord::class
      DataType.FLOORS_CLIMBED -> FloorsClimbedRecord::class
      DataType.ELEVATION_GAINED -> ElevationGainedRecord::class
      DataType.ACTIVITY_INTENSITY -> ActivityIntensityRecord::class
      DataType.HYDRATION -> HydrationRecord::class
      DataType.NUTRITION -> NutritionRecord::class
      DataType.BLOOD_GLUCOSE -> BloodGlucoseRecord::class
      DataType.MINDFULNESS -> MindfulnessSessionRecord::class
      DataType.HEIGHT -> HeightRecord::class
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
      DataType.HEART_RATE_VARIABILITY -> "HRV"
      DataType.OXYGEN_SATURATION -> "SpO₂"
      DataType.RESPIRATORY_RATE -> "Breathing Rate"
      DataType.SKIN_TEMPERATURE -> "Skin Temperature"
      DataType.DISTANCE -> "Distance"
      DataType.ACTIVE_CALORIES_BURNED -> "Active Calories"
      DataType.TOTAL_CALORIES_BURNED -> "Total Calories"
      DataType.FLOORS_CLIMBED -> "Floors Climbed"
      DataType.ELEVATION_GAINED -> "Elevation Gained"
      DataType.ACTIVITY_INTENSITY -> "Activity Intensity"
      DataType.HYDRATION -> "Water"
      DataType.NUTRITION -> "Nutrition"
      DataType.BLOOD_GLUCOSE -> "Blood Glucose"
      DataType.MINDFULNESS -> "Mindfulness"
      DataType.HEIGHT -> "Height"
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
      DataType.HEART_RATE_VARIABILITY -> "HRV"
      DataType.OXYGEN_SATURATION -> "SpO₂"
      DataType.RESPIRATORY_RATE -> "breathing rate"
      DataType.SKIN_TEMPERATURE -> "skin temperature"
      DataType.DISTANCE -> "distance"
      DataType.ACTIVE_CALORIES_BURNED -> "active calories"
      DataType.TOTAL_CALORIES_BURNED -> "total calories"
      DataType.FLOORS_CLIMBED -> "floors climbed"
      DataType.ELEVATION_GAINED -> "elevation gained"
      DataType.ACTIVITY_INTENSITY -> "activity intensity"
      DataType.HYDRATION -> "water"
      DataType.NUTRITION -> "nutrition"
      DataType.BLOOD_GLUCOSE -> "blood glucose"
      DataType.MINDFULNESS -> "mindfulness"
      DataType.HEIGHT -> "height"
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
      DataType.HEART_RATE_VARIABILITY -> R.drawable.ic_hrv
      DataType.OXYGEN_SATURATION -> R.drawable.ic_oxygen_saturation
      DataType.RESPIRATORY_RATE -> R.drawable.ic_respiratory_rate
      DataType.SKIN_TEMPERATURE -> R.drawable.ic_skin_temperature
      DataType.DISTANCE -> R.drawable.ic_distance
      DataType.ACTIVE_CALORIES_BURNED -> R.drawable.ic_active_calories
      DataType.TOTAL_CALORIES_BURNED -> R.drawable.ic_total_calories
      DataType.FLOORS_CLIMBED -> R.drawable.ic_floors_climbed
      DataType.ELEVATION_GAINED -> R.drawable.ic_elevation_gained
      DataType.ACTIVITY_INTENSITY -> R.drawable.ic_activity_intensity
      DataType.HYDRATION -> R.drawable.ic_hydration
      DataType.NUTRITION -> R.drawable.ic_nutrition
      DataType.BLOOD_GLUCOSE -> R.drawable.ic_blood_glucose
      DataType.MINDFULNESS -> R.drawable.ic_mindfulness
      DataType.HEIGHT -> R.drawable.ic_height
      DataType.DATA_TYPE_UNSPECIFIED,
      DataType.UNRECOGNIZED -> throw IllegalArgumentException("Unsupported DataType: $this")
    }
