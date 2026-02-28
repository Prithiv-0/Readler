import 'book_format.dart';

class BookMetadata {
  final String id;
  final String title;
  final String? author;
  final BookFormat format;
  final String filePath;
  final String? coverImagePath;
  final int? lastOpenedAtEpochMs;

  const BookMetadata({
    required this.id,
    required this.title,
    this.author,
    required this.format,
    required this.filePath,
    this.coverImagePath,
    this.lastOpenedAtEpochMs,
  });

  BookMetadata copyWith({
    String? id,
    String? title,
    String? author,
    BookFormat? format,
    String? filePath,
    String? coverImagePath,
    int? lastOpenedAtEpochMs,
  }) {
    return BookMetadata(
      id: id ?? this.id,
      title: title ?? this.title,
      author: author ?? this.author,
      format: format ?? this.format,
      filePath: filePath ?? this.filePath,
      coverImagePath: coverImagePath ?? this.coverImagePath,
      lastOpenedAtEpochMs: lastOpenedAtEpochMs ?? this.lastOpenedAtEpochMs,
    );
  }
}
