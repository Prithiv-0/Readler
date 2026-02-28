import '../../core/model/book_search_result.dart';
import '../repository/book_repository.dart';

class SearchInBookUseCase {
  final BookRepository _bookRepository;

  SearchInBookUseCase(this._bookRepository);

  Future<List<BookSearchResult>> call(String bookId, String query) =>
      _bookRepository.searchInBook(bookId, query);
}
