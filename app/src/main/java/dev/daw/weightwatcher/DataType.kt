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
import dev.daw.weightwatcher.SyncSettings.DataType as ProtoDataType
import kotlin.reflect.KClass

enum class DataType(
  internal val proto: ProtoDataType,
  val recordClass: KClass<out Record>,
  val displayName: String,
  @all:DrawableRes val iconRes: Int,
  /** The name as it appears mid-sentence, e.g. "Last heart rate log found". */
  val nameInSentence: String = displayName.lowercase(),
) {
  // keep-sorted start block=yes
  ACTIVE_CALORIES_BURNED(
    ProtoDataType.ACTIVE_CALORIES_BURNED,
    ActiveCaloriesBurnedRecord::class,
    "Active Calories",
    R.drawable.ic_active_calories,
  ),
  ACTIVITY_INTENSITY(
    ProtoDataType.ACTIVITY_INTENSITY,
    ActivityIntensityRecord::class,
    "Activity Intensity",
    R.drawable.ic_activity_intensity,
  ),
  BLOOD_GLUCOSE(
    ProtoDataType.BLOOD_GLUCOSE,
    BloodGlucoseRecord::class,
    "Blood Glucose",
    R.drawable.ic_blood_glucose,
  ),
  BLOOD_PRESSURE(
    ProtoDataType.BLOOD_PRESSURE,
    BloodPressureRecord::class,
    "Blood Pressure",
    R.drawable.ic_blood_pressure,
  ),
  BODY_FAT(ProtoDataType.BODY_FAT, BodyFatRecord::class, "Body Fat", R.drawable.ic_percent),
  DISTANCE(ProtoDataType.DISTANCE, DistanceRecord::class, "Distance", R.drawable.ic_distance),
  ELEVATION_GAINED(
    ProtoDataType.ELEVATION_GAINED,
    ElevationGainedRecord::class,
    "Elevation Gained",
    R.drawable.ic_elevation_gained,
  ),
  EXERCISE(
    ProtoDataType.EXERCISE,
    ExerciseSessionRecord::class,
    "Exercise",
    R.drawable.ic_exercise,
  ),
  FLOORS_CLIMBED(
    ProtoDataType.FLOORS_CLIMBED,
    FloorsClimbedRecord::class,
    "Floors Climbed",
    R.drawable.ic_floors_climbed,
  ),
  HEART_RATE(
    ProtoDataType.HEART_RATE,
    HeartRateRecord::class,
    "Heart Rate",
    R.drawable.ic_heart_rate,
  ),
  HEART_RATE_VARIABILITY(
    ProtoDataType.HEART_RATE_VARIABILITY,
    HeartRateVariabilityRmssdRecord::class,
    "HRV",
    R.drawable.ic_hrv,
    nameInSentence = "HRV",
  ),
  HEIGHT(ProtoDataType.HEIGHT, HeightRecord::class, "Height", R.drawable.ic_height),
  HYDRATION(ProtoDataType.HYDRATION, HydrationRecord::class, "Water", R.drawable.ic_hydration),
  MINDFULNESS(
    ProtoDataType.MINDFULNESS,
    MindfulnessSessionRecord::class,
    "Mindfulness",
    R.drawable.ic_mindfulness,
  ),
  NUTRITION(ProtoDataType.NUTRITION, NutritionRecord::class, "Nutrition", R.drawable.ic_nutrition),
  OXYGEN_SATURATION(
    ProtoDataType.OXYGEN_SATURATION,
    OxygenSaturationRecord::class,
    "SpO₂",
    R.drawable.ic_oxygen_saturation,
    nameInSentence = "SpO₂",
  ),
  RESPIRATORY_RATE(
    ProtoDataType.RESPIRATORY_RATE,
    RespiratoryRateRecord::class,
    "Breathing Rate",
    R.drawable.ic_respiratory_rate,
  ),
  RESTING_HEART_RATE(
    ProtoDataType.RESTING_HEART_RATE,
    RestingHeartRateRecord::class,
    "Resting Heart Rate",
    R.drawable.ic_favorite,
  ),
  SKIN_TEMPERATURE(
    ProtoDataType.SKIN_TEMPERATURE,
    SkinTemperatureRecord::class,
    "Skin Temperature",
    R.drawable.ic_skin_temperature,
  ),
  SLEEP(ProtoDataType.SLEEP, SleepSessionRecord::class, "Sleep", R.drawable.ic_bedtime),
  STEPS(ProtoDataType.STEPS, StepsRecord::class, "Steps", R.drawable.ic_steps),
  TOTAL_CALORIES_BURNED(
    ProtoDataType.TOTAL_CALORIES_BURNED,
    TotalCaloriesBurnedRecord::class,
    "Total Calories",
    R.drawable.ic_total_calories,
  ),
  VO2_MAX(
    ProtoDataType.VO2_MAX,
    Vo2MaxRecord::class,
    "VO₂ Max",
    R.drawable.ic_air,
    nameInSentence = "VO₂ max",
  ),
  WEIGHT(ProtoDataType.WEIGHT, WeightRecord::class, "Weight", R.drawable.ic_monitor_weight);

  // keep-sorted end

  companion object {
    private val BY_PROTO: Map<ProtoDataType, DataType> =
      entries.associateByTo(emptyEnumMap()) { it.proto }

    /**
     * Returns the [DataType] corresponding to [proto], or `null` if [proto] is unset or unknown.
     */
    internal fun fromProto(proto: ProtoDataType): DataType? = BY_PROTO[proto]
  }

  val readPermission: String
    get() = HealthPermission.getReadPermission(recordClass)
}
