import 'reader_scroll_mode.dart';
import 'reader_theme_mode.dart';

enum ReaderFontFamily {
  sansSerif('sans-serif', 'Sans Serif'),
  serif('serif', 'Serif'),
  monospace('monospace', 'Monospace');

  final String css;
  final String label;
  const ReaderFontFamily(this.css, this.label);
}

enum ReaderLineSpacing {
  compact(1.2, 'Compact'),
  normal(1.6, 'Normal'),
  relaxed(2.0, 'Relaxed'),
  loose(2.4, 'Loose');

  final double value;
  final String label;
  const ReaderLineSpacing(this.value, this.label);
}

class ReaderPreferences {
  final double fontScale;
  final ReaderThemeMode themeMode;
  final ReaderScrollMode scrollMode;
  final ReaderFontFamily fontFamily;
  final ReaderLineSpacing lineSpacing;

  const ReaderPreferences({
    this.fontScale = 1.0,
    this.themeMode = ReaderThemeMode.system,
    this.scrollMode = ReaderScrollMode.continuous,
    this.fontFamily = ReaderFontFamily.sansSerif,
    this.lineSpacing = ReaderLineSpacing.normal,
  });

  ReaderPreferences copyWith({
    double? fontScale,
    ReaderThemeMode? themeMode,
    ReaderScrollMode? scrollMode,
    ReaderFontFamily? fontFamily,
    ReaderLineSpacing? lineSpacing,
  }) {
    return ReaderPreferences(
      fontScale: fontScale ?? this.fontScale,
      themeMode: themeMode ?? this.themeMode,
      scrollMode: scrollMode ?? this.scrollMode,
      fontFamily: fontFamily ?? this.fontFamily,
      lineSpacing: lineSpacing ?? this.lineSpacing,
    );
  }
}
