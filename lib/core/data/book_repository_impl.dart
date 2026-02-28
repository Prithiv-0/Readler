import 'dart:io';

import 'package:archive/archive.dart';
import 'package:uuid/uuid.dart';

import '../database/book_dao.dart';
import '../model/book_format.dart';
import '../model/book_metadata.dart';
import '../model/book_search_result.dart';
import '../model/opened_book.dart';
import '../model/reading_progress.dart';
import '../reader/locator/reader_locator_codec.dart';
import '../storage/book_file_storage.dart';
import '../../domain/repository/book_repository.dart';
import 'importer/book_metadata_extractor.dart';

class BookRepositoryImpl implements BookRepository {
  final BookDao _bookDao;
  final BookFileStorage _fileStorage;
  final BookMetadataExtractor _metadataExtractor;

  BookRepositoryImpl({
    required BookDao bookDao,
    required BookFileStorage fileStorage,
    required BookMetadataExtractor metadataExtractor,
  })  : _bookDao = bookDao,
        _fileStorage = fileStorage,
        _metadataExtractor = metadataExtractor;

  @override
  Stream<List<BookMetadata>> observeLibrary() => _bookDao.observeLibrary();

  @override
  Future<BookMetadata> importBook(String sourceUri) async {
    final importedFile = await _fileStorage.importBook(sourceUri);
    final extractedMetadata = await _metadataExtractor.extract(importedFile);
    final now = DateTime.now().millisecondsSinceEpoch;
    final fallbackTitle = importedFile.displayName;

    final id = const Uuid().v4();
    final row = BookDao.toRow(
      id: id,
      title: extractedMetadata.title ?? fallbackTitle,
      author: extractedMetadata.author,
      format: importedFile.format,
      filePath: importedFile.filePath,
      coverImagePath: extractedMetadata.coverImagePath,
      lastOpenedAtEpochMs: now,
    );

    await _bookDao.upsert(row);
    final inserted = await _bookDao.getBook(id);
    return inserted!;
  }

  @override
  Future<OpenedBook> openBook(String bookId) async {
    final meta = await _bookDao.getBook(bookId);
    if (meta == null) throw StateError('Book not found: $bookId');

    final now = DateTime.now().millisecondsSinceEpoch;
    await _bookDao.updateLastOpened(bookId, now);

    final raw = await _bookDao.getBookRaw(bookId);
    final startLocator = raw?['progressLocator'] as String?;

    return OpenedBook(
      metadata: meta.copyWith(lastOpenedAtEpochMs: now),
      startLocator: startLocator,
    );
  }

  @override
  Future<void> saveReadingProgress(ReadingProgress progress) async {
    await _bookDao.updateProgress(
      progress.bookId,
      progress.locator,
      progress.percent.clamp(0.0, 1.0),
      progress.updatedAtEpochMs,
    );
  }

  @override
  Future<ReadingProgress?> getReadingProgress(String bookId) {
    return _bookDao.getReadingProgress(bookId);
  }

  @override
  Future<List<BookSearchResult>> searchInBook(
      String bookId, String query) async {
    final normalizedQuery = query.trim();
    if (normalizedQuery.isEmpty) return [];

    final raw = await _bookDao.getBookRaw(bookId);
    if (raw == null) return [];

    final formatStr = raw['format'] as String;
    final format = formatStr.toUpperCase() == 'EPUB'
        ? BookFormat.epub
        : BookFormat.pdf;
    final filePath = raw['filePath'] as String;

    switch (format) {
      case BookFormat.epub:
        return _searchInEpub(filePath, normalizedQuery);
      case BookFormat.pdf:
        return _searchInPdf(normalizedQuery);
    }
  }

  List<BookSearchResult> _searchInEpub(String filePath, String query) {
    try {
      final file = File(filePath);
      if (!file.existsSync()) return [];

      final bytes = file.readAsBytesSync();
      final archive = ZipDecoder().decodeBytes(bytes);
      final lowerQuery = query.toLowerCase();

      final chapterTexts = archive.files
          .where((f) =>
              !f.isFile == false &&
              (f.name.endsWith('.xhtml') ||
                  f.name.endsWith('.html') ||
                  f.name.endsWith('.htm')))
          .map((f) => String.fromCharCodes(f.content as List<int>))
          .map((html) => html
              .replaceAll(RegExp(r'<[^>]+>'), ' ')
              .replaceAll(RegExp(r'\s+'), ' ')
              .trim())
          .where((t) => t.isNotEmpty)
          .toList();

      final plainText = chapterTexts.join('\n');
      if (plainText.isEmpty) return [];

      final lowerText = plainText.toLowerCase();
      final results = <BookSearchResult>[];
      var fromIndex = 0;

      while (results.length < 20) {
        final index = lowerText.indexOf(lowerQuery, fromIndex);
        if (index < 0) break;

        final percent = plainText.length <= 1
            ? 0.0
            : index / (plainText.length - 1);
        final start = (index - 50).clamp(0, plainText.length);
        final end =
            (index + query.length + 70).clamp(0, plainText.length);
        final snippet = plainText.substring(start, end).trim();

        results.add(BookSearchResult(
          locator: ReaderLocatorCodec.encodeEpubScrollPercent(percent),
          snippet: snippet,
          percent: percent,
        ));

        fromIndex = index + query.length;
      }

      return results;
    } catch (_) {
      return [];
    }
  }

  List<BookSearchResult> _searchInPdf(String query) {
    final normalized = query.trim().toLowerCase();
    int? pageNumber;
    if (normalized.startsWith('page ')) {
      pageNumber = int.tryParse(normalized.substring(5));
    } else {
      pageNumber = int.tryParse(normalized);
    }

    if (pageNumber != null && pageNumber > 0) {
      final pageIndex = pageNumber - 1;
      return [
        BookSearchResult(
          locator: ReaderLocatorCodec.encodePdfPageIndex(pageIndex),
          snippet: 'Jump to page $pageNumber',
          percent: 0,
        ),
      ];
    }

    return [];
  }
}
