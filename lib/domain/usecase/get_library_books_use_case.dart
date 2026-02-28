import '../../core/model/book_metadata.dart';
import '../repository/book_repository.dart';

class GetLibraryBooksUseCase {
  final BookRepository _bookRepository;

  GetLibraryBooksUseCase(this._bookRepository);

  Stream<List<BookMetadata>> call() => _bookRepository.observeLibrary();
}
