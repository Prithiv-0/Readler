import '../../core/model/book_metadata.dart';
import '../repository/book_repository.dart';

class ImportBookUseCase {
  final BookRepository _bookRepository;

  ImportBookUseCase(this._bookRepository);

  Future<BookMetadata> call(String sourceUri) =>
      _bookRepository.importBook(sourceUri);
}
