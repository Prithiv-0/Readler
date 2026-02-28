import '../model/book_format.dart';

class ImportedBookFile {
  final String filePath;
  final BookFormat format;
  final String displayName;

  const ImportedBookFile({
    required this.filePath,
    required this.format,
    required this.displayName,
  });
}
