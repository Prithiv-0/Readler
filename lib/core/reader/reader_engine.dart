import '../model/book_format.dart';
import 'reader_document.dart';

abstract class ReaderEngine {
  BookFormat get format;
  Future<ReaderDocument> open({
    required String filePath,
    String? initialLocator,
  });
}
