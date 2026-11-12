package dev.daw.weightwatcher

import android.content.Intent
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf

private const val EXTRA_DATA_TYPE = "dev.daw.weightwatcher.EXTRA_DATA_TYPE"
private val DATA_TYPE_PREF_KEY: Preferences.Key<Int> = intPreferencesKey("data_type")
private val EXTRA_DATA_TYPE_KEY: ActionParameters.Key<Int> = ActionParameters.Key(EXTRA_DATA_TYPE)

/**
 * Persisted integer IDs for Android/Glance key-value stores ([Preferences], [Intent], and
 * [ActionParameters]) that don't take custom enums directly.
 */
private val DATA_TYPES_BY_NUMBER: Map<Int, DataType> =
  DataType.entries.associateBy { it.proto.number }

internal fun dataTypePreferencesOf(dataType: DataType): Preferences =
  preferencesOf(DATA_TYPE_PREF_KEY to dataType.toNumber())

internal fun Preferences.getDataType(): DataType? = dataTypeFromNumber(this[DATA_TYPE_PREF_KEY])

internal fun MutablePreferences.setDataType(dataType: DataType) {
  this[DATA_TYPE_PREF_KEY] = dataType.toNumber()
}

internal fun dataTypeActionParameters(dataType: DataType): ActionParameters =
  actionParametersOf(EXTRA_DATA_TYPE_KEY to dataType.toNumber())

internal fun Intent.putDataType(dataType: DataType): Intent =
  putExtra(EXTRA_DATA_TYPE, dataType.toNumber())

internal fun Intent.getDataType(): DataType? = dataTypeFromNumber(getIntExtra(EXTRA_DATA_TYPE, 0))

private fun DataType.toNumber(): Int = proto.number

private fun dataTypeFromNumber(number: Int?): DataType? = DATA_TYPES_BY_NUMBER[number]
