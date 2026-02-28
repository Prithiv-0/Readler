import 'package:sqflite/sqflite.dart';

import '../model/book_format.dart';
import '../model/book_metadata.dart';
import '../model/reading_progress.dart';
import 'app_database.dart';

class BookDao {
  final Database _db;

  BookDao(this._db);

  Stream<List<BookMetadata>> observeLibrary() async* {
    // Emit current snapshot then poll is not ideal; in a real app you'd use
    // a change-notifier pattern. Here we yield the current list once and
    // rely on the ViewModel to re-query after mutations.
    yield await _queryLibrary();
  }

  Future<List<BookMetadata>> _queryLibrary() async {
    final rows = await _db.query(
      'books',
      orderBy: 'COALESCE(lastOpenedAtEpochMs, 0) DESC, title ASC',
    );
    return rows.map(_rowToMetadata).toList();
  }

  Future<List<BookMetadata>> getLibrary() => _queryLibrary();

  Future<Map<String, dynamic>?> getBookRaw(String bookId) async {
    final rows =
        await _db.query('books', where: 'id = ?', whereArgs: [bookId], limit: 1);
    return rows.isEmpty ? null : rows.first;
  }

  Future<BookMetadata?> getBook(String bookId) async {
    final row = await getBookRaw(bookId);
    return row == null ? null : _rowToMetadata(row);
  }

  Future<void> upsert(Map<String, dynamic> book) async {
    await _db.insert('books', book,
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> updateProgress(
      String bookId, String locator, double percent, int updatedAtEpochMs) async {
    await _db.update(
      'books',
      {
        'progressLocator': locator,
        'progressPercent': percent,
        'progressUpdatedAtEpochMs': updatedAtEpochMs,
      },
      where: 'id = ?',
      whereArgs: [bookId],
    );
  }

  Future<void> updateLastOpened(String bookId, int openedAtEpochMs) async {
    await _db.update(
      'books',
      {'lastOpenedAtEpochMs': openedAtEpochMs},
      where: 'id = ?',
      whereArgs: [bookId],
    );
  }

  Future<ReadingProgress?> getReadingProgress(String bookId) async {
    final row = await getBookRaw(bookId);
    if (row == null) return null;
    final locator = row['progressLocator'] as String?;
    final updatedAt = row['progressUpdatedAtEpochMs'] as int?;
    if (locator == null || updatedAt == null) return null;
    return ReadingProgress(
      bookId: bookId,
      locator: locator,
      percent: (row['progressPercent'] as num?)?.toDouble() ?? 0.0,
      updatedAtEpochMs: updatedAt,
    );
  }

  BookMetadata _rowToMetadata(Map<String, dynamic> row) {
    return BookMetadata(
      id: row['id'] as String,
      title: row['title'] as String,
      author: row['author'] as String?,
      format: AppDatabase.stringToFormat(row['format'] as String),
      filePath: row['filePath'] as String,
      coverImagePath: row['coverImagePath'] as String?,
      lastOpenedAtEpochMs: row['lastOpenedAtEpochMs'] as int?,
    );
  }

  /// Helper to build a row map for upsert from domain values.
  static Map<String, dynamic> toRow({
    required String id,
    required String title,
    String? author,
    required BookFormat format,
    required String filePath,
    String? coverImagePath,
    int? lastOpenedAtEpochMs,
    String? progressLocator,
    double progressPercent = 0,
    int? progressUpdatedAtEpochMs,
  }) {
    return {
      'id': id,
      'title': title,
      'author': author,
      'format': AppDatabase.formatToString(format),
      'filePath': filePath,
      'coverImagePath': coverImagePath,
      'lastOpenedAtEpochMs': lastOpenedAtEpochMs,
      'progressLocator': progressLocator,
      'progressPercent': progressPercent,
      'progressUpdatedAtEpochMs': progressUpdatedAtEpochMs,
    };
  }
}
