import '../../core/model/opened_book.dart';
import '../repository/book_repository.dart';

class OpenBookUseCase {
  final BookRepository _bookRepository;

  OpenBookUseCase(this._bookRepository);

  Future<OpenedBook> call(String bookId) => _bookRepository.openBook(bookId);
}
