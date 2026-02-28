import 'dart:io';

import '../model/book_format.dart';
import 'locator/reader_locator_codec.dart';
import 'reader_document.dart';
import 'reader_engine.dart';

class PdfReaderEngine implements ReaderEngine {
  @override
  BookFormat get format => BookFormat.pdf;

  @override
  Future<ReaderDocument> open({
    required String filePath,
    String? initialLocator,
  }) async {
    final file = File(filePath);
    if (!file.existsSync()) {
      throw FileSystemException('PDF file missing', filePath);
    }
    return PdfDocument(
      filePath: filePath,
      initialLocator: initialLocator,
      initialPageIndex:
          ReaderLocatorCodec.decodePdfPageIndex(initialLocator) ?? 0,
    );
  }
}
