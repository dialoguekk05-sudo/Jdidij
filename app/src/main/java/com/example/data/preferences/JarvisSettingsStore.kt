package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_preferences"
)

enum class JarvisLanguageMode(val label: String, val speechLocale: String) {
    BILINGUAL("Hindi + English", "hi-IN"),
    HINDI("हिन्दी (Hindi)", "hi-IN"),
    ENGLISH("English (India)", "en-IN")
}

enum class VoiceGender(val label: String) {
    FEMALE("Female (Neural)"),
    MALE("Male (Deep)")
}

data class JarvisSettings(
    val languageMode: JarvisLanguageMode = JarvisLanguageMode.BILINGUAL,
    val voiceGender: VoiceGender = VoiceGender.MALE,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val continuousVoiceMode: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val backgroundWakeService: Boolean = false,
    val saveConversationHistory: Boolean = true,
    val autoMemoryFacts: Boolean = true,
    val preferDirectCall: Boolean = false,
    val lowRamOptimized: Boolean = false
)

class JarvisSettingsStore(private val context: Context) {

    private object Keys {
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val CONTINUOUS_VOICE = booleanPreferencesKey("continuous_voice")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val BACKGROUND_WAKE_SERVICE = booleanPreferencesKey("background_wake_service")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val AUTO_MEMORY = booleanPreferencesKey("auto_memory")
        val PREFER_DIRECT_CALL = booleanPreferencesKey("prefer_direct_call")
        val LOW_RAM_OPTIMIZED = booleanPreferencesKey("low_ram_optimized")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val langName = prefs[Keys.LANGUAGE_MODE] ?: JarvisLanguageMode.BILINGUAL.name
        val genderName = prefs[Keys.VOICE_GENDER] ?: VoiceGender.MALE.name
        JarvisSettings(
            languageMode = runCatching { JarvisLanguageMode.valueOf(langName) }
                .getOrDefault(JarvisLanguageMode.BILINGUAL),
            voiceGender = runCatching { VoiceGender.valueOf(genderName) }
                .getOrDefault(VoiceGender.MALE),
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 1.0f,
            continuousVoiceMode = prefs[Keys.CONTINUOUS_VOICE] ?: false,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            backgroundWakeService = prefs[Keys.BACKGROUND_WAKE_SERVICE] ?: false,
            saveConversationHistory = prefs[Keys.SAVE_HISTORY] ?: true,
            autoMemoryFacts = prefs[Keys.AUTO_MEMORY] ?: true,
            preferDirectCall = prefs[Keys.PREFER_DIRECT_CALL] ?: false,
            lowRamOptimized = prefs[Keys.LOW_RAM_OPTIMIZED] ?: false
        )
    }

    suspend fun setLanguageMode(mode: JarvisLanguageMode) {
        context.jarvisDataStore.edit { it[Keys.LANGUAGE_MODE] = mode.name }
    }

    suspend fun setVoiceGender(gender: VoiceGender) {
        context.jarvisDataStore.edit { it[Keys.VOICE_GENDER] = gender.name }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.jarvisDataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.5f) }
    }

    suspend fun setSpeechPitch(pitch: Float) {
        context.jarvisDataStore.edit { it[Keys.SPEECH_PITCH] = pitch.coerceIn(0.7f, 1.4f) }
    }

    suspend fun setContinuousVoiceMode(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.CONTINUOUS_VOICE] = enabled }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setBackgroundWakeService(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.BACKGROUND_WAKE_SERVICE] = enabled }
    }

    suspend fun setSaveConversationHistory(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.SAVE_HISTORY] = enabled }
    }

    suspend fun setAutoMemoryFacts(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.AUTO_MEMORY] = enabled }
    }

    suspend fun setPreferDirectCall(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.PREFER_DIRECT_CALL] = enabled }
    }

    suspend fun setLowRamOptimized(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.LOW_RAM_OPTIMIZED] = enabled }
    }
}
