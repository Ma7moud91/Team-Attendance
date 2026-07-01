package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val THEME_KEY = stringPreferencesKey("theme")
        val LANGUAGE_KEY = stringPreferencesKey("language")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { it[THEME_KEY] ?: "SYSTEM" }
    val languageFlow: Flow<String> = context.dataStore.data.map { it[LANGUAGE_KEY] ?: "en" }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME_KEY] = theme }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[LANGUAGE_KEY] = language }
    }
}
