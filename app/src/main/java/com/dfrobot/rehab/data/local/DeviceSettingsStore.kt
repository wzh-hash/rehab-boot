package com.dfrobot.rehab.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dfrobot.rehab.domain.model.DeviceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_settings")

private val KEY_HOST = stringPreferencesKey("host")
private val KEY_PORT = intPreferencesKey("port")
private val KEY_IOT_ID = stringPreferencesKey("iot_id")
private val KEY_IOT_PWD = stringPreferencesKey("iot_pwd")
private val KEY_TOPIC = stringPreferencesKey("topic")
private val KEY_BODY_WEIGHT = doublePreferencesKey("body_weight")
private val KEY_P25 = intPreferencesKey("p25")
private val KEY_P50 = intPreferencesKey("p50")
private val KEY_P75 = intPreferencesKey("p75")
private val KEY_CONNECTION_ENABLED = booleanPreferencesKey("connection_enabled")

class DeviceSettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settingsFlow: Flow<DeviceSettings> = dataStore.data.map { prefs ->
        DeviceSettings(
            host = prefs[KEY_HOST] ?: "iot.dfrobot.com.cn",
            port = prefs[KEY_PORT] ?: 1883,
            iotId = prefs[KEY_IOT_ID] ?: "",
            iotPwd = prefs[KEY_IOT_PWD] ?: "",
            topic = prefs[KEY_TOPIC] ?: "",
        )
    }

    val weightPercentagesFlow: Flow<Pair<Double, Triple<Int, Int, Int>>> = dataStore.data.map { prefs ->
        (prefs[KEY_BODY_WEIGHT] ?: 60.0) to Triple(
            prefs[KEY_P25] ?: 25,
            prefs[KEY_P50] ?: 50,
            prefs[KEY_P75] ?: 75,
        )
    }

    val connectionEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CONNECTION_ENABLED] ?: false
    }

    suspend fun saveSettings(settings: DeviceSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_HOST] = settings.host
            prefs[KEY_PORT] = settings.port
            prefs[KEY_IOT_ID] = settings.iotId
            prefs[KEY_IOT_PWD] = settings.iotPwd
            prefs[KEY_TOPIC] = settings.topic
        }
    }

    suspend fun saveWeightPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_BODY_WEIGHT] = bodyWeightKg
            prefs[KEY_P25] = p25
            prefs[KEY_P50] = p50
            prefs[KEY_P75] = p75
        }
    }

    suspend fun setConnectionEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_CONNECTION_ENABLED] = enabled }
    }
}
