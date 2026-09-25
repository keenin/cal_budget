package com.keenin.calbudget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keenin.calbudget.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme")

class ThemeSettings(context: Context) {
    private val dataStore = context.applicationContext.themeDataStore

    val mode: Flow<ThemeMode> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> ThemeMode.fromName(prefs[KEY]) }

    suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY] = mode.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("theme_mode")
    }
}
