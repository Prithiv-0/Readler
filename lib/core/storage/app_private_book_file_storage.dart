import 'dart:io';
import 'dart:typed_data';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

import '../model/book_format.dart';
import 'book_file_storage.dart';
import 'imported_book_file.dart';

class AppPrivateBookFileStorage implements BookFileStorage {
  @override
  Future<ImportedBookFile> importBook(String sourcePath) async {
    final sourceFile = File(sourcePath);
    if (!sourceFile.existsSync()) {
      throw FileSystemException('Source file not found', sourcePath);
    }

    final extension = p.extension(sourcePath).toLowerCase().replaceFirst('.', '');
    final format = _extensionToFormat(extension);

    final rawName = p.basenameWithoutExtension(sourcePath).trim();
    final displayName =
        rawName.isNotEmpty ? rawName : const Uuid().v4();

    final appDir = await getApplicationDocumentsDirectory();
    final booksDir = Directory(p.join(appDir.path, 'books'));
    if (!booksDir.existsSync()) {
      booksDir.createSync(recursive: true);
    }

    final targetPath = p.join(booksDir.path, '${const Uuid().v4()}.$extension');
    await sourceFile.copy(targetPath);

    return ImportedBookFile(
      filePath: targetPath,
      format: format,
      displayName: displayName,
    );
  }

  @override
  Future<Uint8List> readFile(String filePath) async {
    final file = File(filePath);
    if (!file.existsSync()) {
      throw FileSystemException('Book file missing', filePath);
    }
    return file.readAsBytes();
  }

  BookFormat _extensionToFormat(String ext) {
    switch (ext) {
      case 'epub':
        return BookFormat.epub;
      case 'pdf':
        return BookFormat.pdf;
      default:
        throw ArgumentError('Unsupported file format: $ext');
    }
  }
}
