import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';

import '../model/book_format.dart';

class AppDatabase {
  static Database? _instance;

  static Future<Database> getInstance() async {
    if (_instance != null) return _instance!;
    final dbPath = await getDatabasesPath();
    _instance = await openDatabase(
      join(dbPath, 'readler.db'),
      version: 2,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
    return _instance!;
  }

  static Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE books (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        author TEXT,
        format TEXT NOT NULL,
        filePath TEXT NOT NULL,
        coverImagePath TEXT,
        lastOpenedAtEpochMs INTEGER,
        progressLocator TEXT,
        progressPercent REAL NOT NULL DEFAULT 0,
        progressUpdatedAtEpochMs INTEGER
      )
    ''');
    await db.execute('''
      CREATE TABLE highlights (
        id TEXT PRIMARY KEY,
        bookId TEXT NOT NULL,
        locator TEXT NOT NULL,
        quote TEXT NOT NULL,
        colorHex TEXT NOT NULL,
        createdAtEpochMs INTEGER NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE notes (
        id TEXT PRIMARY KEY,
        bookId TEXT NOT NULL,
        locator TEXT NOT NULL,
        text TEXT NOT NULL,
        createdAtEpochMs INTEGER NOT NULL,
        updatedAtEpochMs INTEGER NOT NULL
      )
    ''');
  }

  static Future<void> _onUpgrade(
      Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      await db.execute('''
        CREATE TABLE IF NOT EXISTS highlights (
          id TEXT PRIMARY KEY,
          bookId TEXT NOT NULL,
          locator TEXT NOT NULL,
          quote TEXT NOT NULL,
          colorHex TEXT NOT NULL,
          createdAtEpochMs INTEGER NOT NULL
        )
      ''');
      await db.execute('''
        CREATE TABLE IF NOT EXISTS notes (
          id TEXT PRIMARY KEY,
          bookId TEXT NOT NULL,
          locator TEXT NOT NULL,
          text TEXT NOT NULL,
          createdAtEpochMs INTEGER NOT NULL,
          updatedAtEpochMs INTEGER NOT NULL
        )
      ''');
    }
  }

  /// Converts a [BookFormat] to its database string representation.
  static String formatToString(BookFormat format) {
    switch (format) {
      case BookFormat.epub:
        return 'EPUB';
      case BookFormat.pdf:
        return 'PDF';
    }
  }

  /// Converts a database string back to [BookFormat].
  static BookFormat stringToFormat(String value) {
    switch (value.toUpperCase()) {
      case 'EPUB':
        return BookFormat.epub;
      case 'PDF':
        return BookFormat.pdf;
      default:
        throw ArgumentError('Unknown book format: $value');
    }
  }
}
