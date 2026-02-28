class ReadingProgress {
  final String bookId;
  final String locator;

  /// Reading progress as a fraction from 0.0 to 1.0.
  final double percent;
  final int updatedAtEpochMs;

  const ReadingProgress({
    required this.bookId,
    required this.locator,
    required this.percent,
    required this.updatedAtEpochMs,
  });
}
