package com.readler.feature.reader.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedReaderPreferencesStore(
    context: Context
) : ReaderPreferencesStore {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(load())
    override val preferences: StateFlow<ReaderPreferences> = _preferences.asStateFlow()

    override suspend fun updateFontScale(fontScale: Float) {
        val clamped = fontScale.coerceIn(0.75f, 2.0f)
        val updated = _preferences.value.copy(fontScale = clamped)
        _preferences.value = updated
        prefs.edit().putFloat(KEY_FONT_SCALE, clamped).apply()
    }

    override suspend fun updateThemeMode(themeMode: ReaderThemeMode) {
        val updated = _preferences.value.copy(themeMode = themeMode)
        _preferences.value = updated
        prefs.edit().putString(KEY_THEME_MODE, themeMode.name).apply()
    }

    override suspend fun updateScrollMode(scrollMode: ReaderScrollMode) {
        val updated = _preferences.value.copy(scrollMode = scrollMode)
        _preferences.value = updated
        prefs.edit().putString(KEY_SCROLL_MODE, scrollMode.name).apply()
    }

    override suspend fun updateFontFamily(fontFamily: ReaderFontFamily) {
        val updated = _preferences.value.copy(fontFamily = fontFamily)
        _preferences.value = updated
        prefs.edit().putString(KEY_FONT_FAMILY, fontFamily.name).apply()
    }

    override suspend fun updateLineSpacing(lineSpacing: ReaderLineSpacing) {
        val updated = _preferences.value.copy(lineSpacing = lineSpacing)
        _preferences.value = updated
        prefs.edit().putString(KEY_LINE_SPACING, lineSpacing.name).apply()
    }

    private fun load(): ReaderPreferences {
        val fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f).coerceIn(0.75f, 2.0f)
        val themeMode = prefs.getString(KEY_THEME_MODE, ReaderThemeMode.SYSTEM.name)
            ?.let { runCatching { ReaderThemeMode.valueOf(it) }.getOrNull() }
            ?: ReaderThemeMode.SYSTEM
        val scrollMode = prefs.getString(KEY_SCROLL_MODE, ReaderScrollMode.CONTINUOUS.name)
            ?.let { runCatching { ReaderScrollMode.valueOf(it) }.getOrNull() }
            ?: ReaderScrollMode.CONTINUOUS
        val fontFamily = prefs.getString(KEY_FONT_FAMILY, ReaderFontFamily.SANS_SERIF.name)
            ?.let { runCatching { ReaderFontFamily.valueOf(it) }.getOrNull() }
            ?: ReaderFontFamily.SANS_SERIF
        val lineSpacing = prefs.getString(KEY_LINE_SPACING, ReaderLineSpacing.NORMAL.name)
            ?.let { runCatching { ReaderLineSpacing.valueOf(it) }.getOrNull() }
            ?: ReaderLineSpacing.NORMAL

        return ReaderPreferences(
            fontScale = fontScale,
            themeMode = themeMode,
            scrollMode = scrollMode,
            fontFamily = fontFamily,
            lineSpacing = lineSpacing
        )
    }

    private companion object {
        const val PREF_NAME = "readler_reader_preferences"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SCROLL_MODE = "scroll_mode"
        const val KEY_FONT_FAMILY = "font_family"
        const val KEY_LINE_SPACING = "line_spacing"
    }
}
