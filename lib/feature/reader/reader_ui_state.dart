import '../../core/model/book_metadata.dart';
import '../../core/model/book_search_result.dart';
import '../../core/reader/reader_document.dart';
import '../../domain/repository/ai_capability.dart';
import '../../domain/repository/ai_queued_request.dart';
import 'prefs/reader_preferences.dart';

class ReaderUiState {
  final BookMetadata? currentBook;
  final ReaderDocument? currentDocument;
  final String? currentLocator;
  final String searchQuery;
  final List<BookSearchResult> searchResults;
  final bool aiEnabled;
  final AiCapability aiCapability;
  final String? aiResponse;
  final bool isAiLoading;
  final AiQueuedRequest? pendingQueueRequest;
  final ReaderPreferences preferences;
  final bool isLoading;
  final String? errorMessage;

  const ReaderUiState({
    this.currentBook,
    this.currentDocument,
    this.currentLocator,
    this.searchQuery = '',
    this.searchResults = const [],
    this.aiEnabled = false,
    this.aiCapability = const AiCapability(),
    this.aiResponse,
    this.isAiLoading = false,
    this.pendingQueueRequest,
    this.preferences = const ReaderPreferences(),
    this.isLoading = false,
    this.errorMessage,
  });

  ReaderUiState copyWith({
    BookMetadata? currentBook,
    ReaderDocument? currentDocument,
    String? currentLocator,
    String? searchQuery,
    List<BookSearchResult>? searchResults,
    bool? aiEnabled,
    AiCapability? aiCapability,
    String? aiResponse,
    bool clearAiResponse = false,
    bool? isAiLoading,
    AiQueuedRequest? pendingQueueRequest,
    bool clearPendingQueue = false,
    ReaderPreferences? preferences,
    bool? isLoading,
    String? errorMessage,
    bool clearError = false,
  }) {
    return ReaderUiState(
      currentBook: currentBook ?? this.currentBook,
      currentDocument: currentDocument ?? this.currentDocument,
      currentLocator: currentLocator ?? this.currentLocator,
      searchQuery: searchQuery ?? this.searchQuery,
      searchResults: searchResults ?? this.searchResults,
      aiEnabled: aiEnabled ?? this.aiEnabled,
      aiCapability: aiCapability ?? this.aiCapability,
      aiResponse: clearAiResponse ? null : (aiResponse ?? this.aiResponse),
      isAiLoading: isAiLoading ?? this.isAiLoading,
      pendingQueueRequest: clearPendingQueue
          ? null
          : (pendingQueueRequest ?? this.pendingQueueRequest),
      preferences: preferences ?? this.preferences,
      isLoading: isLoading ?? this.isLoading,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
    );
  }
}
