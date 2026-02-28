import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'app/app_container.dart';
import 'core/model/book_metadata.dart';
import 'feature/library/library_screen.dart';
import 'feature/library/library_view_model.dart';
import 'feature/reader/reader_screen.dart';
import 'feature/reader/reader_view_model.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final container = AppContainer();
  await container.initialize();

  runApp(ReadlerApp(container: container));
}

class ReadlerApp extends StatelessWidget {
  final AppContainer container;

  const ReadlerApp({super.key, required this.container});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => LibraryViewModel(
            getLibraryBooks: container.getLibraryBooksUseCase,
            importBook: container.importBookUseCase,
          ),
        ),
        ChangeNotifierProvider(
          create: (_) => ReaderViewModel(
            openBook: container.openBookUseCase,
            saveReadingProgress: container.saveReadingProgressUseCase,
            searchInBook: container.searchInBookUseCase,
            aiRepository: container.aiRepository,
            readerEngineRegistry: container.readerEngineRegistry,
            preferencesStore: container.readerPreferencesStore,
          ),
        ),
      ],
      child: MaterialApp(
        title: 'Readler',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorSchemeSeed: Colors.indigo,
          useMaterial3: true,
          brightness: Brightness.light,
        ),
        darkTheme: ThemeData(
          colorSchemeSeed: Colors.indigo,
          useMaterial3: true,
          brightness: Brightness.dark,
        ),
        home: const _AppNavigator(),
      ),
    );
  }
}

class _AppNavigator extends StatefulWidget {
  const _AppNavigator();

  @override
  State<_AppNavigator> createState() => _AppNavigatorState();
}

class _AppNavigatorState extends State<_AppNavigator> {
  BookMetadata? _selectedBook;

  @override
  Widget build(BuildContext context) {
    if (_selectedBook != null) {
      return ReaderScreen(
        onBack: () {
          setState(() => _selectedBook = null);
          // Refresh library when returning
          context.read<LibraryViewModel>().refreshLibrary();
        },
      );
    }

    return LibraryScreen(
      onBookSelected: (book) {
        setState(() => _selectedBook = book);
        context.read<ReaderViewModel>().loadBook(book.id);
      },
    );
  }
}
