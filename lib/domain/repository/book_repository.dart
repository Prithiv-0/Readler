import '../../core/model/book_metadata.dart';
import '../../core/model/book_search_result.dart';
import '../../core/model/opened_book.dart';
import '../../core/model/reading_progress.dart';

abstract class BookRepository {
  Stream<List<BookMetadata>> observeLibrary();
  Future<BookMetadata> importBook(String sourceUri);
  Future<OpenedBook> openBook(String bookId);
  Future<void> saveReadingProgress(ReadingProgress progress);
  Future<ReadingProgress?> getReadingProgress(String bookId);
  Future<List<BookSearchResult>> searchInBook(String bookId, String query);
}
