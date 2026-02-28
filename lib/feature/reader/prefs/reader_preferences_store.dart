import 'package:shared_preferences/shared_preferences.dart';

import 'reader_preferences.dart';
import 'reader_scroll_mode.dart';
import 'reader_theme_mode.dart';

class ReaderPreferencesStore {
  static const _keyFontScale = 'font_scale';
  static const _keyThemeMode = 'theme_mode';
  static const _keyScrollMode = 'scroll_mode';
  static const _keyFontFamily = 'font_family';
  static const _keyLineSpacing = 'line_spacing';

  ReaderPreferences _current = const ReaderPreferences();
  ReaderPreferences get current => _current;

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    _current = ReaderPreferences(
      fontScale: (prefs.getDouble(_keyFontScale) ?? 1.0).clamp(0.75, 2.0),
      themeMode: _parseEnum(
        prefs.getString(_keyThemeMode),
        ReaderThemeMode.values,
        ReaderThemeMode.system,
      ),
      scrollMode: _parseEnum(
        prefs.getString(_keyScrollMode),
        ReaderScrollMode.values,
        ReaderScrollMode.continuous,
      ),
      fontFamily: _parseEnum(
        prefs.getString(_keyFontFamily),
        ReaderFontFamily.values,
        ReaderFontFamily.sansSerif,
      ),
      lineSpacing: _parseEnum(
        prefs.getString(_keyLineSpacing),
        ReaderLineSpacing.values,
        ReaderLineSpacing.normal,
      ),
    );
  }

  Future<void> updateFontScale(double fontScale) async {
    final clamped = fontScale.clamp(0.75, 2.0);
    _current = _current.copyWith(fontScale: clamped);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keyFontScale, clamped);
  }

  Future<void> updateThemeMode(ReaderThemeMode themeMode) async {
    _current = _current.copyWith(themeMode: themeMode);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyThemeMode, themeMode.name);
  }

  Future<void> updateScrollMode(ReaderScrollMode scrollMode) async {
    _current = _current.copyWith(scrollMode: scrollMode);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyScrollMode, scrollMode.name);
  }

  Future<void> updateFontFamily(ReaderFontFamily fontFamily) async {
    _current = _current.copyWith(fontFamily: fontFamily);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyFontFamily, fontFamily.name);
  }

  Future<void> updateLineSpacing(ReaderLineSpacing lineSpacing) async {
    _current = _current.copyWith(lineSpacing: lineSpacing);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyLineSpacing, lineSpacing.name);
  }

  T _parseEnum<T extends Enum>(String? value, List<T> values, T defaultValue) {
    if (value == null) return defaultValue;
    try {
      return values.firstWhere((e) => e.name == value);
    } catch (_) {
      return defaultValue;
    }
  }
}
