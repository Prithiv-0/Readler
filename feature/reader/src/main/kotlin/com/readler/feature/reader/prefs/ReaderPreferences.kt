package com.readler.feature.reader.prefs

enum class ReaderFontFamily(val css: String, val label: String) {
    SANS_SERIF("sans-serif", "Sans Serif"),
    SERIF("serif", "Serif"),
    MONOSPACE("monospace", "Monospace")
}

enum class ReaderLineSpacing(val value: Float, val label: String) {
    COMPACT(1.2f, "Compact"),
    NORMAL(1.6f, "Normal"),
    RELAXED(2.0f, "Relaxed"),
    LOOSE(2.4f, "Loose")
}

data class ReaderPreferences(
    val fontScale: Float = 1f,
    val themeMode: ReaderThemeMode = ReaderThemeMode.SYSTEM,
    val scrollMode: ReaderScrollMode = ReaderScrollMode.CONTINUOUS,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SANS_SERIF,
    val lineSpacing: ReaderLineSpacing = ReaderLineSpacing.NORMAL
)
