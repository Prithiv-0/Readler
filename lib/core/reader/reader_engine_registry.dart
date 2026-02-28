import '../model/book_format.dart';
import 'reader_engine.dart';

class ReaderEngineRegistry {
  final Map<BookFormat, ReaderEngine> _engines;

  ReaderEngineRegistry(List<ReaderEngine> engines)
      : _engines = {for (final e in engines) e.format: e};

  ReaderEngine get(BookFormat format) {
    final engine = _engines[format];
    if (engine == null) {
      throw StateError('No reader engine registered for $format');
    }
    return engine;
  }
}
