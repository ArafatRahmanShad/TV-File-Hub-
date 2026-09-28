package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tv_file_hub_prefs")

class AppPreferences(private val context: Context) {

  companion object {
    private val KEY_DEFAULT_DESTINATION = stringPreferencesKey("default_destination")
    private val KEY_USB_HDD_TREE_URI = stringPreferencesKey("usb_hdd_tree_uri")
    private val KEY_USB_HDD_NAME = stringPreferencesKey("usb_hdd_name")
    private val KEY_WEB_SERVER_PORT = intPreferencesKey("web_server_port")
    private val KEY_WEB_SERVER_ENABLED = booleanPreferencesKey("web_server_enabled")
    private val KEY_SEEDING_RATIO_TARGET = floatPreferencesKey("seeding_ratio_target")
    private val KEY_AUTO_RESUME_PLAYBACK = booleanPreferencesKey("auto_resume_playback")
    private val KEY_UPLOAD_LIMIT_KB = intPreferencesKey("upload_limit_kb")
  }

  val defaultDestination: Flow<String> = context.dataStore.data.map { prefs ->
    prefs[KEY_DEFAULT_DESTINATION] ?: "INTERNAL"
  }

  val usbHddTreeUri: Flow<String?> = context.dataStore.data.map { prefs ->
    prefs[KEY_USB_HDD_TREE_URI]
  }

  val usbHddName: Flow<String> = context.dataStore.data.map { prefs ->
    prefs[KEY_USB_HDD_NAME] ?: "USB Drive"
  }

  val webServerPort: Flow<Int> = context.dataStore.data.map { prefs ->
    prefs[KEY_WEB_SERVER_PORT] ?: 8765
  }

  val webServerEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
    prefs[KEY_WEB_SERVER_ENABLED] ?: true
  }

  val seedingRatioTarget: Flow<Float> = context.dataStore.data.map { prefs ->
    prefs[KEY_SEEDING_RATIO_TARGET] ?: 1.5f
  }

  val autoResumePlayback: Flow<Boolean> = context.dataStore.data.map { prefs ->
    prefs[KEY_AUTO_RESUME_PLAYBACK] ?: true
  }

  val uploadLimitKb: Flow<Int> = context.dataStore.data.map { prefs ->
    prefs[KEY_UPLOAD_LIMIT_KB] ?: 0
  }

  suspend fun setDefaultDestination(destination: String) {
    context.dataStore.edit { prefs ->
      prefs[KEY_DEFAULT_DESTINATION] = destination
    }
  }

  suspend fun setUsbHddTreeUri(uri: String?, name: String = "USB Drive") {
    context.dataStore.edit { prefs ->
      if (uri == null) {
        prefs.remove(KEY_USB_HDD_TREE_URI)
      } else {
        prefs[KEY_USB_HDD_TREE_URI] = uri
        prefs[KEY_USB_HDD_NAME] = name
      }
    }
  }

  suspend fun setWebServerPort(port: Int) {
    context.dataStore.edit { prefs ->
      prefs[KEY_WEB_SERVER_PORT] = port
    }
  }

  suspend fun setWebServerEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[KEY_WEB_SERVER_ENABLED] = enabled
    }
  }

  suspend fun setSeedingRatioTarget(ratio: Float) {
    context.dataStore.edit { prefs ->
      prefs[KEY_SEEDING_RATIO_TARGET] = ratio
    }
  }

  suspend fun setAutoResumePlayback(resume: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[KEY_AUTO_RESUME_PLAYBACK] = resume
    }
  }

  suspend fun setUploadLimitKb(limit: Int) {
    context.dataStore.edit { prefs ->
      prefs[KEY_UPLOAD_LIMIT_KB] = limit
    }
  }

  suspend fun getUsbTreeUriSync(): String? {
    return context.dataStore.data.map { it[KEY_USB_HDD_TREE_URI] }.first()
  }

  suspend fun getDefaultDestinationSync(): String {
    return context.dataStore.data.map { it[KEY_DEFAULT_DESTINATION] ?: "INTERNAL" }.first()
  }
}
