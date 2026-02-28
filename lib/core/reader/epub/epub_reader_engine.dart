import '../../model/book_format.dart';
import '../reader_document.dart';
import '../reader_engine.dart';
import 'epub_archive_parser.dart';

class EpubReaderEngine implements ReaderEngine {
  final _parser = EpubArchiveParser();

  @override
  BookFormat get format => BookFormat.epub;

  @override
  Future<ReaderDocument> open({
    required String filePath,
    String? initialLocator,
  }) async {
    final parsed = _parser.parseCombinedHtml(filePath);
    return EpubDocument(
      filePath: filePath,
      initialLocator: initialLocator,
      htmlContent: parsed.htmlContent,
      chapterCount: parsed.chapterCount,
    );
  }
}
