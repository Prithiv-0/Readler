import '../ai/gemini_ai_repository.dart';
import '../core/data/book_repository_impl.dart';
import '../core/data/importer/book_import_metadata_extractor.dart';
import '../core/data/importer/book_metadata_extractor.dart';
import '../core/database/app_database.dart';
import '../core/database/book_dao.dart';
import '../core/reader/epub/epub_reader_engine.dart';
import '../core/reader/pdf_reader_engine.dart';
import '../core/reader/reader_engine_registry.dart';
import '../core/storage/app_private_book_file_storage.dart';
import '../core/storage/book_file_storage.dart';
import '../domain/repository/ai_repository.dart';
import '../domain/repository/book_repository.dart';
import '../domain/usecase/get_library_books_use_case.dart';
import '../domain/usecase/import_book_use_case.dart';
import '../domain/usecase/open_book_use_case.dart';
import '../domain/usecase/save_reading_progress_use_case.dart';
import '../domain/usecase/search_in_book_use_case.dart';
import '../feature/reader/prefs/reader_preferences_store.dart';

class AppContainer {
  late final BookDao _bookDao;
  late final BookFileStorage _fileStorage;
  late final BookMetadataExtractor _metadataExtractor;
  late final BookRepository bookRepository;
  late final AiRepository aiRepository;
  late final ReaderEngineRegistry readerEngineRegistry;
  late final ReaderPreferencesStore readerPreferencesStore;

  late final GetLibraryBooksUseCase getLibraryBooksUseCase;
  late final ImportBookUseCase importBookUseCase;
  late final OpenBookUseCase openBookUseCase;
  late final SaveReadingProgressUseCase saveReadingProgressUseCase;
  late final SearchInBookUseCase searchInBookUseCase;

  Future<void> initialize() async {
    final db = await AppDatabase.getInstance();
    _bookDao = BookDao(db);
    _fileStorage = AppPrivateBookFileStorage();
    _metadataExtractor = BookImportMetadataExtractor();

    bookRepository = BookRepositoryImpl(
      bookDao: _bookDao,
      fileStorage: _fileStorage,
      metadataExtractor: _metadataExtractor,
    );

    aiRepository = GeminiAiRepository();

    readerEngineRegistry = ReaderEngineRegistry([
      EpubReaderEngine(),
      PdfReaderEngine(),
    ]);

    readerPreferencesStore = ReaderPreferencesStore();
    await readerPreferencesStore.load();

    getLibraryBooksUseCase = GetLibraryBooksUseCase(bookRepository);
    importBookUseCase = ImportBookUseCase(bookRepository);
    openBookUseCase = OpenBookUseCase(bookRepository);
    saveReadingProgressUseCase = SaveReadingProgressUseCase(bookRepository);
    searchInBookUseCase = SearchInBookUseCase(bookRepository);
  }

  BookFileStorage get bookFileStorage => _fileStorage;
}
