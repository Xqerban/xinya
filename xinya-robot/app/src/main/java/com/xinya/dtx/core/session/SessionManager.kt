package com.xinya.dtx.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xinya_session")

/**
 * 会话管理器 - 存储机器人绑定的患者ID等信息
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_PATIENT_ID = stringPreferencesKey("patient_id")
        private val KEY_PATIENT_NAME = stringPreferencesKey("patient_name")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")
        private val KEY_IS_BOUND = stringPreferencesKey("is_bound")
    }

    val patientId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PATIENT_ID] ?: ""
    }

    val patientName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PATIENT_NAME] ?: "患者"
    }

    val isBound: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_BOUND] == "true"
    }

    val deviceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID] ?: generateDeviceId()
    }

    suspend fun saveBindingInfo(patientId: String, patientName: String, deviceToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PATIENT_ID] = patientId
            prefs[KEY_PATIENT_NAME] = patientName
            prefs[KEY_DEVICE_TOKEN] = deviceToken
            prefs[KEY_IS_BOUND] = "true"
        }
    }

    suspend fun savePatientInfo(patientId: String, patientName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PATIENT_ID] = patientId
            prefs[KEY_PATIENT_NAME] = patientName
        }
    }

    suspend fun ensureDeviceId(): String {
        var id = context.dataStore.data.map { it[KEY_DEVICE_ID] }.first()
        if (id == null) {
            id = generateDeviceId()
            context.dataStore.edit { prefs -> prefs[KEY_DEVICE_ID] = id }
        }
        return id
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    private fun generateDeviceId(): String =
        "robot-${android.os.Build.MODEL.replace(" ", "-")}-${System.currentTimeMillis() % 100000}"
}
