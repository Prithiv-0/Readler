import '../../core/model/book_metadata.dart';

class LibraryUiState {
  final List<BookMetadata> books;
  final bool isLoading;
  final String? errorMessage;

  const LibraryUiState({
    this.books = const [],
    this.isLoading = true,
    this.errorMessage,
  });

  LibraryUiState copyWith({
    List<BookMetadata>? books,
    bool? isLoading,
    String? errorMessage,
  }) {
    return LibraryUiState(
      books: books ?? this.books,
      isLoading: isLoading ?? this.isLoading,
      errorMessage: errorMessage,
    );
  }
}
