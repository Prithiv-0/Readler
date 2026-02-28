import 'package:flutter/foundation.dart';

import '../../core/model/reading_progress.dart';
import '../../core/reader/reader_document.dart';
import '../../core/reader/reader_engine_registry.dart';
import '../../core/storage/book_file_storage.dart';
import '../../domain/repository/ai_queued_request.dart';
import '../../domain/repository/ai_repository.dart';
import '../../domain/usecase/open_book_use_case.dart';
import '../../domain/usecase/save_reading_progress_use_case.dart';
import '../../domain/usecase/search_in_book_use_case.dart';
import 'prefs/reader_preferences.dart';
import 'prefs/reader_preferences_store.dart';
import 'prefs/reader_scroll_mode.dart';
import 'prefs/reader_theme_mode.dart';
import 'reader_ui_state.dart';

class ReaderViewModel extends ChangeNotifier {
  final OpenBookUseCase _openBook;
  final SaveReadingProgressUseCase _saveReadingProgress;
  final SearchInBookUseCase _searchInBook;
  final AiRepository _aiRepository;
  final BookFileStorage _fileStorage;
  final ReaderEngineRegistry _readerEngineRegistry;
  final ReaderPreferencesStore _preferencesStore;

  ReaderUiState _state = const ReaderUiState();
  ReaderUiState get state => _state;

  ReaderViewModel({
    required OpenBookUseCase openBook,
    required SaveReadingProgressUseCase saveReadingProgress,
    required SearchInBookUseCase searchInBook,
    required AiRepository aiRepository,
    required BookFileStorage fileStorage,
    required ReaderEngineRegistry readerEngineRegistry,
    required ReaderPreferencesStore preferencesStore,
  })  : _openBook = openBook,
        _saveReadingProgress = saveReadingProgress,
        _searchInBook = searchInBook,
        _aiRepository = aiRepository,
        _fileStorage = fileStorage,
        _readerEngineRegistry = readerEngineRegistry,
        _preferencesStore = preferencesStore {
    _loadPreferences();
    _refreshAiCapability();
  }

  Future<void> _loadPreferences() async {
    await _preferencesStore.load();
    _state = _state.copyWith(preferences: _preferencesStore.current);
    notifyListeners();
  }

  Future<void> loadBook(String bookId) async {
    _state = _state.copyWith(isLoading: true, clearError: true);
    notifyListeners();

    try {
      final openedBook = await _openBook.call(bookId);
      final engine = _readerEngineRegistry.get(openedBook.metadata.format);
      final document = await engine.open(
        filePath: openedBook.metadata.filePath,
        initialLocator: openedBook.startLocator,
      );
      _state = _state.copyWith(
        currentBook: openedBook.metadata,
        currentDocument: document,
        currentLocator: openedBook.startLocator,
        isLoading: false,
        clearError: true,
      );
    } catch (e) {
      _state = _state.copyWith(
        isLoading: false,
        errorMessage: e.toString(),
      );
    }
    notifyListeners();
  }

  Future<void> onReadingPositionChanged(String locator, double percent) async {
    final currentBook = _state.currentBook;
    if (currentBook == null) return;

    final progress = ReadingProgress(
      bookId: currentBook.id,
      locator: locator,
      percent: percent,
      updatedAtEpochMs: DateTime.now().millisecondsSinceEpoch,
    );

    await _saveReadingProgress.call(progress);
    _state = _state.copyWith(currentLocator: locator);
    notifyListeners();
  }

  Future<void> onFontScaleChanged(double fontScale) async {
    await _preferencesStore.updateFontScale(fontScale);
    _state = _state.copyWith(preferences: _preferencesStore.current);
    notifyListeners();
  }

  Future<void> onThemeModeChanged(ReaderThemeMode themeMode) async {
    await _preferencesStore.updateThemeMode(themeMode);
    _state = _state.copyWith(preferences: _preferencesStore.current);
    notifyListeners();
  }

  Future<void> onScrollModeChanged(ReaderScrollMode scrollMode) async {
    await _preferencesStore.updateScrollMode(scrollMode);
    _state = _state.copyWith(preferences: _preferencesStore.current);
    notifyListeners();
  }

  Future<void> onFontFamilyChanged(ReaderFontFamily fontFamily) async {
    await _preferencesStore.updateFontFamily(fontFamily);
    _state = _state.copyWith(preferences: _preferencesStore.current);
    notifyListeners();
  }

  Future<void> onLineSpacingChanged(ReaderLineSpacing lineSpacing) async {
    await _preferencesStore.updateLineSpacing(lineSpacing);
    _state = _state.copyWith(preferences: _preferencesStore.current);
    notifyListeners();
  }

  void clearError() {
    _state = _state.copyWith(clearError: true);
    notifyListeners();
  }

  Future<void> onAiEnabledChanged(bool enabled) async {
    try {
      await _aiRepository.setEnabled(enabled);
      await _refreshAiCapability();
    } catch (e) {
      _state = _state.copyWith(errorMessage: e.toString());
      notifyListeners();
    }
  }

  Future<void> saveAiApiKey(String apiKey) async {
    final normalized = apiKey.trim();
    try {
      await _aiRepository.setApiKey(normalized);
      await _refreshAiCapability();
      _state = _state.copyWith(
        aiResponse: normalized.isEmpty
            ? 'Gemini API key cleared.'
            : 'Gemini API key saved.',
      );
      notifyListeners();
    } catch (e) {
      _state = _state.copyWith(errorMessage: e.toString());
      notifyListeners();
    }
  }

  Future<void> _refreshAiCapability() async {
    try {
      final capability = await _aiRepository.getCapability();
      String? aiResponse = _state.aiResponse;
      if (capability.canRun) {
        try {
          final flushed = await _aiRepository.flushQueuedRequests();
          if (flushed > 0) {
            aiResponse = 'Processed $flushed queued AI request(s).';
          }
        } catch (_) {}
      }
      _state = _state.copyWith(
        aiEnabled: capability.enabled,
        aiCapability: capability,
        aiResponse: aiResponse,
      );
      notifyListeners();
    } catch (e) {
      _state = _state.copyWith(errorMessage: e.toString());
      notifyListeners();
    }
  }

  Future<void> refreshAiCapability() => _refreshAiCapability();

  Future<void> askBookQuestion(String question) async {
    await _performAiAction(
      type: AiRequestType.question,
      prompt: question,
      action: (book) =>
          _aiRepository.askQuestion(book.id, book.title, book.author, question),
    );
  }

  Future<void> askSimilarBooks() async {
    await _performAiAction(
      type: AiRequestType.similarBooks,
      prompt: 'Suggest books similar to current book',
      action: (book) =>
          _aiRepository.suggestSimilarBooks(book.id, book.title, book.author),
    );
  }

  Future<void> explainSelectedText(String selectedText) async {
    await _performAiAction(
      type: AiRequestType.explainSelection,
      prompt: 'Explain this selected text',
      selectedText: selectedText,
      action: (book) =>
          _aiRepository.explainSelection(book.id, book.title, selectedText),
    );
  }

  Future<void> translateSelectedText(
      String selectedText, String targetLanguage) async {
    await _performAiAction(
      type: AiRequestType.translateSelection,
      prompt: 'Translate selected text',
      selectedText: selectedText,
      targetLanguage: targetLanguage,
      action: (book) => _aiRepository.translateSelection(
          book.id, book.title, selectedText, targetLanguage),
    );
  }

  Future<void> summarizeCurrentSection(String sectionText) async {
    await _performAiAction(
      type: AiRequestType.sectionSummary,
      prompt: 'Summarize current section',
      sectionContext: sectionText,
      action: (book) =>
          _aiRepository.summarizeSection(book.id, book.title, sectionText),
    );
  }

  Future<void> confirmQueuePending(bool shouldQueue) async {
    final pending = _state.pendingQueueRequest;
    if (pending == null) return;

    if (shouldQueue) {
      try {
        await _aiRepository.enqueueRequest(pending);
      } catch (e) {
        _state = _state.copyWith(
          clearPendingQueue: true,
          errorMessage: e.toString(),
          isAiLoading: false,
        );
        notifyListeners();
        return;
      }
    }

    _state = _state.copyWith(
      clearPendingQueue: true,
      isAiLoading: false,
      clearError: true,
      aiResponse: shouldQueue
          ? 'Request queued. It will run when network is available.'
          : _state.aiResponse,
    );
    notifyListeners();
  }

  void clearAiResponse() {
    _state = _state.copyWith(clearAiResponse: true);
    notifyListeners();
  }

  Future<void> searchInCurrentBook(String query) async {
    final currentBook = _state.currentBook;
    if (currentBook == null) return;

    final trimmed = query.trim();
    if (trimmed.isEmpty) {
      _state = _state.copyWith(searchQuery: '', searchResults: []);
      notifyListeners();
      return;
    }

    try {
      final results = await _searchInBook.call(currentBook.id, trimmed);
      _state = _state.copyWith(
        searchQuery: trimmed,
        searchResults: results,
        errorMessage:
            results.isEmpty ? "No matches for '$trimmed'" : null,
      );
      if (results.isNotEmpty) {
        _jumpToLocator(results.first.locator);
      }
    } catch (e) {
      _state = _state.copyWith(errorMessage: e.toString());
    }
    notifyListeners();
  }

  void _jumpToLocator(String locator) {
    final currentDocument = _state.currentDocument;
    if (currentDocument == null) return;

    ReaderDocument relocated;
    if (currentDocument is EpubDocument) {
      relocated = currentDocument.copyWith(initialLocator: locator);
    } else if (currentDocument is PdfDocument) {
      relocated = currentDocument.copyWith(initialLocator: locator);
    } else {
      return;
    }

    _state = _state.copyWith(
      currentDocument: relocated,
      currentLocator: locator,
    );
  }

  Future<void> _performAiAction({
    required AiRequestType type,
    required String prompt,
    String? selectedText,
    String? targetLanguage,
    String? sectionContext,
    required Future<String> Function(
            _BookRef book)
        action,
  }) async {
    final currentBook = _state.currentBook;
    if (currentBook == null) {
      _state = _state.copyWith(errorMessage: 'Open a book before using AI');
      notifyListeners();
      return;
    }

    _state = _state.copyWith(isAiLoading: true, clearError: true);
    notifyListeners();

    try {
      final capability = await _aiRepository.getCapability();
      _state = _state.copyWith(
          aiCapability: capability, aiEnabled: capability.enabled);

      if (!capability.enabled) {
        _state = _state.copyWith(
            isAiLoading: false, errorMessage: 'AI is disabled');
        notifyListeners();
        return;
      }
      if (!capability.hasApiKey) {
        _state = _state.copyWith(
            isAiLoading: false, errorMessage: 'Gemini API key missing');
        notifyListeners();
        return;
      }
      if (!capability.hasNetwork) {
        _state = _state.copyWith(
          isAiLoading: false,
          pendingQueueRequest: AiQueuedRequest(
            bookId: currentBook.id,
            bookTitle: currentBook.title,
            author: currentBook.author,
            type: type,
            prompt: prompt,
            selectedText: selectedText,
            targetLanguage: targetLanguage,
            sectionContext: sectionContext,
          ),
          errorMessage: 'No network. Queue this AI request?',
        );
        notifyListeners();
        return;
      }

      final bookRef = _BookRef(
        id: currentBook.id,
        title: currentBook.title,
        author: currentBook.author,
      );
      final response = await action(bookRef);
      _state = _state.copyWith(
        isAiLoading: false,
        clearPendingQueue: true,
        aiResponse: response,
        clearError: true,
      );
    } catch (e) {
      _state = _state.copyWith(
        isAiLoading: false,
        errorMessage: e.toString(),
      );
    }
    notifyListeners();
  }
}

class _BookRef {
  final String id;
  final String title;
  final String? author;
  const _BookRef({required this.id, required this.title, this.author});
}
