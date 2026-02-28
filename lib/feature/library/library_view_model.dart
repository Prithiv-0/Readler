import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../domain/usecase/get_library_books_use_case.dart';
import '../../domain/usecase/import_book_use_case.dart';
import 'library_ui_state.dart';

class LibraryViewModel extends ChangeNotifier {
  final GetLibraryBooksUseCase _getLibraryBooks;
  final ImportBookUseCase _importBook;

  LibraryUiState _state = const LibraryUiState();
  LibraryUiState get state => _state;

  StreamSubscription<dynamic>? _subscription;

  LibraryViewModel({
    required GetLibraryBooksUseCase getLibraryBooks,
    required ImportBookUseCase importBook,
  })  : _getLibraryBooks = getLibraryBooks,
        _importBook = importBook {
    _observeLibrary();
  }

  void _observeLibrary() {
    _subscription = _getLibraryBooks.call().listen(
      (books) {
        _state = _state.copyWith(books: books, isLoading: false);
        notifyListeners();
      },
      onError: (Object error) {
        _state = _state.copyWith(
          isLoading: false,
          errorMessage: error.toString(),
        );
        notifyListeners();
      },
    );
  }

  Future<void> refreshLibrary() async {
    _subscription?.cancel();
    _state = _state.copyWith(isLoading: true);
    notifyListeners();
    _observeLibrary();
  }

  Future<void> importBookFromPath(String path) async {
    try {
      _state = _state.copyWith(isLoading: true);
      notifyListeners();
      await _importBook.call(path);
      await refreshLibrary();
    } catch (e) {
      _state = _state.copyWith(
        isLoading: false,
        errorMessage: e.toString(),
      );
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }
}
