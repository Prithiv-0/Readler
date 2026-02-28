import 'dart:typed_data';

import 'imported_book_file.dart';

abstract class BookFileStorage {
  Future<ImportedBookFile> importBook(String sourcePath);
  Future<Uint8List> readFile(String filePath);
}
