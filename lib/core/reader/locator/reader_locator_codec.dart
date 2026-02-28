class ReaderLocatorCodec {
  static const _epubScrollPrefix = 'epub-scroll:';
  static const _pdfPagePrefix = 'pdf-page:';

  static String encodeEpubScrollPercent(double percent) {
    return '$_epubScrollPrefix${percent.clamp(0.0, 1.0)}';
  }

  static double? decodeEpubScrollPercent(String? locator) {
    if (locator == null || !locator.startsWith(_epubScrollPrefix)) return null;
    final value = double.tryParse(locator.substring(_epubScrollPrefix.length));
    return value?.clamp(0.0, 1.0);
  }

  static String encodePdfPageIndex(int pageIndex) {
    return '$_pdfPagePrefix${pageIndex < 0 ? 0 : pageIndex}';
  }

  static int? decodePdfPageIndex(String? locator) {
    if (locator == null || !locator.startsWith(_pdfPagePrefix)) return null;
    final value = int.tryParse(locator.substring(_pdfPagePrefix.length));
    if (value == null) return null;
    return value < 0 ? 0 : value;
  }
}
