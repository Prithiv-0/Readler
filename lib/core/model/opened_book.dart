import 'book_metadata.dart';

class OpenedBook {
  final BookMetadata metadata;
  final String? startLocator;

  const OpenedBook({
    required this.metadata,
    this.startLocator,
  });
}
