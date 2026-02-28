class ReadingProgress {
  final String bookId;
  final String locator;
  final double percent;
  final int updatedAtEpochMs;

  const ReadingProgress({
    required this.bookId,
    required this.locator,
    required this.percent,
    required this.updatedAtEpochMs,
  });
}
