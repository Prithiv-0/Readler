import 'package:flutter_test/flutter_test.dart';

import 'package:readler/core/model/book_format.dart';
import 'package:readler/core/model/book_metadata.dart';
import 'package:readler/core/model/reading_progress.dart';
import 'package:readler/core/model/opened_book.dart';
import 'package:readler/core/model/book_search_result.dart';

void main() {
  group('BookFormat', () {
    test('has epub and pdf values', () {
      expect(BookFormat.values, contains(BookFormat.epub));
      expect(BookFormat.values, contains(BookFormat.pdf));
    });
  });

  group('BookMetadata', () {
    test('creates with required fields', () {
      final meta = BookMetadata(
        id: '1',
        title: 'Test Book',
        format: BookFormat.epub,
        filePath: '/path/to/book.epub',
      );
      expect(meta.id, '1');
      expect(meta.title, 'Test Book');
      expect(meta.author, isNull);
      expect(meta.format, BookFormat.epub);
      expect(meta.filePath, '/path/to/book.epub');
      expect(meta.coverImagePath, isNull);
      expect(meta.lastOpenedAtEpochMs, isNull);
    });

    test('copyWith preserves unchanged fields', () {
      final meta = BookMetadata(
        id: '1',
        title: 'Test',
        format: BookFormat.pdf,
        filePath: '/p.pdf',
        author: 'Author',
      );
      final updated = meta.copyWith(title: 'New Title');
      expect(updated.title, 'New Title');
      expect(updated.author, 'Author');
      expect(updated.id, '1');
    });
  });

  group('ReadingProgress', () {
    test('stores progress data', () {
      final progress = ReadingProgress(
        bookId: 'b1',
        locator: 'epub-scroll:0.5',
        percent: 0.5,
        updatedAtEpochMs: 12345,
      );
      expect(progress.bookId, 'b1');
      expect(progress.locator, 'epub-scroll:0.5');
      expect(progress.percent, 0.5);
      expect(progress.updatedAtEpochMs, 12345);
    });
  });

  group('OpenedBook', () {
    test('holds metadata and optional start locator', () {
      final meta = BookMetadata(
        id: '1',
        title: 'Test',
        format: BookFormat.epub,
        filePath: '/p.epub',
      );
      final opened = OpenedBook(metadata: meta);
      expect(opened.metadata.id, '1');
      expect(opened.startLocator, isNull);

      final openedWithLocator = OpenedBook(
        metadata: meta,
        startLocator: 'epub-scroll:0.25',
      );
      expect(openedWithLocator.startLocator, 'epub-scroll:0.25');
    });
  });

  group('BookSearchResult', () {
    test('stores search result data', () {
      final result = BookSearchResult(
        locator: 'pdf-page:5',
        snippet: 'Jump to page 6',
        percent: 0.0,
      );
      expect(result.locator, 'pdf-page:5');
      expect(result.snippet, 'Jump to page 6');
      expect(result.percent, 0.0);
    });
  });
}
