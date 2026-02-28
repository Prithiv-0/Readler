import 'package:flutter_test/flutter_test.dart';

import 'package:readler/domain/repository/ai_capability.dart';
import 'package:readler/domain/repository/ai_queued_request.dart';
import 'package:readler/feature/reader/prefs/reader_preferences.dart';
import 'package:readler/feature/reader/prefs/reader_theme_mode.dart';
import 'package:readler/feature/reader/prefs/reader_scroll_mode.dart';

void main() {
  group('AiCapability', () {
    test('canRun is true when all conditions met', () {
      const cap = AiCapability(
        enabled: true,
        hasApiKey: true,
        hasNetwork: true,
      );
      expect(cap.canRun, isTrue);
    });

    test('canRun is false when disabled', () {
      const cap = AiCapability(
        enabled: false,
        hasApiKey: true,
        hasNetwork: true,
      );
      expect(cap.canRun, isFalse);
    });

    test('canRun is false when no API key', () {
      const cap = AiCapability(
        enabled: true,
        hasApiKey: false,
        hasNetwork: true,
      );
      expect(cap.canRun, isFalse);
    });

    test('canRun is false when no network', () {
      const cap = AiCapability(
        enabled: true,
        hasApiKey: true,
        hasNetwork: false,
      );
      expect(cap.canRun, isFalse);
    });
  });

  group('AiQueuedRequest', () {
    test('generates requestId and timestamp', () {
      final request = AiQueuedRequest(
        bookId: 'b1',
        bookTitle: 'Test Book',
        type: AiRequestType.question,
        prompt: 'What is the theme?',
      );
      expect(request.requestId, isNotEmpty);
      expect(request.createdAtEpochMs, greaterThan(0));
      expect(request.bookId, 'b1');
      expect(request.prompt, 'What is the theme?');
    });
  });

  group('ReaderPreferences', () {
    test('defaults are correct', () {
      const prefs = ReaderPreferences();
      expect(prefs.fontScale, 1.0);
      expect(prefs.themeMode, ReaderThemeMode.system);
      expect(prefs.scrollMode, ReaderScrollMode.continuous);
      expect(prefs.fontFamily, ReaderFontFamily.sansSerif);
      expect(prefs.lineSpacing, ReaderLineSpacing.normal);
    });

    test('copyWith preserves unchanged fields', () {
      const prefs = ReaderPreferences();
      final updated = prefs.copyWith(fontScale: 1.5);
      expect(updated.fontScale, 1.5);
      expect(updated.themeMode, ReaderThemeMode.system);
      expect(updated.scrollMode, ReaderScrollMode.continuous);
    });
  });

  group('ReaderFontFamily', () {
    test('has correct CSS values', () {
      expect(ReaderFontFamily.sansSerif.css, 'sans-serif');
      expect(ReaderFontFamily.serif.css, 'serif');
      expect(ReaderFontFamily.monospace.css, 'monospace');
    });
  });

  group('ReaderLineSpacing', () {
    test('has correct float values', () {
      expect(ReaderLineSpacing.compact.value, 1.2);
      expect(ReaderLineSpacing.normal.value, 1.6);
      expect(ReaderLineSpacing.relaxed.value, 2.0);
      expect(ReaderLineSpacing.loose.value, 2.4);
    });
  });
}
