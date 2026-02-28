/// A parsed reader document, either EPUB or PDF.
sealed class ReaderDocument {
  String get filePath;
  String? get initialLocator;
}

class EpubDocument extends ReaderDocument {
  @override
  final String filePath;
  @override
  final String? initialLocator;
  final String htmlContent;
  final int chapterCount;

  EpubDocument({
    required this.filePath,
    this.initialLocator,
    required this.htmlContent,
    required this.chapterCount,
  });

  EpubDocument copyWith({String? initialLocator}) {
    return EpubDocument(
      filePath: filePath,
      initialLocator: initialLocator ?? this.initialLocator,
      htmlContent: htmlContent,
      chapterCount: chapterCount,
    );
  }
}

class PdfDocument extends ReaderDocument {
  @override
  final String filePath;
  @override
  final String? initialLocator;
  final int initialPageIndex;

  PdfDocument({
    required this.filePath,
    this.initialLocator,
    required this.initialPageIndex,
  });

  PdfDocument copyWith({String? initialLocator, int? initialPageIndex}) {
    return PdfDocument(
      filePath: filePath,
      initialLocator: initialLocator ?? this.initialLocator,
      initialPageIndex: initialPageIndex ?? this.initialPageIndex,
    );
  }
}
