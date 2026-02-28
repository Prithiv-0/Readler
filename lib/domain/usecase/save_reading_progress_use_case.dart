import '../../core/model/reading_progress.dart';
import '../repository/book_repository.dart';

class SaveReadingProgressUseCase {
  final BookRepository _bookRepository;

  SaveReadingProgressUseCase(this._bookRepository);

  Future<void> call(ReadingProgress progress) =>
      _bookRepository.saveReadingProgress(progress);
}
