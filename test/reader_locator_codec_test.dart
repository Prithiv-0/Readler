import 'package:readler/core/reader/locator/reader_locator_codec.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ReaderLocatorCodec', () {
    group('EPUB scroll percent', () {
      test('encodes percent within range', () {
        expect(
          ReaderLocatorCodec.encodeEpubScrollPercent(0.5),
          'epub-scroll:0.5',
        );
      });

      test('clamps percent above 1', () {
        expect(
          ReaderLocatorCodec.encodeEpubScrollPercent(1.5),
          'epub-scroll:1.0',
        );
      });

      test('clamps percent below 0', () {
        expect(
          ReaderLocatorCodec.encodeEpubScrollPercent(-0.5),
          'epub-scroll:0.0',
        );
      });

      test('decodes valid locator', () {
        expect(
          ReaderLocatorCodec.decodeEpubScrollPercent('epub-scroll:0.75'),
          0.75,
        );
      });

      test('returns null for null', () {
        expect(ReaderLocatorCodec.decodeEpubScrollPercent(null), isNull);
      });

      test('returns null for wrong prefix', () {
        expect(
          ReaderLocatorCodec.decodeEpubScrollPercent('pdf-page:3'),
          isNull,
        );
      });
    });

    group('PDF page index', () {
      test('encodes page index', () {
        expect(ReaderLocatorCodec.encodePdfPageIndex(5), 'pdf-page:5');
      });

      test('clamps negative page index to 0', () {
        expect(ReaderLocatorCodec.encodePdfPageIndex(-1), 'pdf-page:0');
      });

      test('decodes valid locator', () {
        expect(ReaderLocatorCodec.decodePdfPageIndex('pdf-page:11'), 11);
      });

      test('returns null for null', () {
        expect(ReaderLocatorCodec.decodePdfPageIndex(null), isNull);
      });

      test('returns null for wrong prefix', () {
        expect(
          ReaderLocatorCodec.decodePdfPageIndex('epub-scroll:0.5'),
          isNull,
        );
      });

      test('clamps negative decoded page to 0', () {
        expect(ReaderLocatorCodec.decodePdfPageIndex('pdf-page:-3'), 0);
      });
    });
  });
}
