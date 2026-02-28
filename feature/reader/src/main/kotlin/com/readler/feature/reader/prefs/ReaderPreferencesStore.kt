package com.readler.feature.reader.prefs

import kotlinx.coroutines.flow.StateFlow

interface ReaderPreferencesStore {
    val preferences: StateFlow<ReaderPreferences>

    suspend fun updateFontScale(fontScale: Float)
    suspend fun updateThemeMode(themeMode: ReaderThemeMode)
    suspend fun updateScrollMode(scrollMode: ReaderScrollMode)
    suspend fun updateFontFamily(fontFamily: ReaderFontFamily)
    suspend fun updateLineSpacing(lineSpacing: ReaderLineSpacing)
}
