import '../storage/imported_book_file.dart';

class BookImportMetadata {
  final String? title;
  final String? author;
  final String? coverImagePath;

  const BookImportMetadata({this.title, this.author, this.coverImagePath});
}

abstract class BookMetadataExtractor {
  Future<BookImportMetadata> extract(ImportedBookFile importedFile);
}
