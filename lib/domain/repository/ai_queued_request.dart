import 'package:uuid/uuid.dart';

enum AiRequestType {
  question,
  explainSelection,
  translateSelection,
  similarBooks,
  sectionSummary,
}

class AiQueuedRequest {
  final String requestId;
  final String bookId;
  final String bookTitle;
  final String? author;
  final AiRequestType type;
  final String prompt;
  final String? selectedText;
  final String? targetLanguage;
  final String? sectionContext;
  final int createdAtEpochMs;

  AiQueuedRequest({
    String? requestId,
    required this.bookId,
    required this.bookTitle,
    this.author,
    required this.type,
    required this.prompt,
    this.selectedText,
    this.targetLanguage,
    this.sectionContext,
    int? createdAtEpochMs,
  })  : requestId = requestId ?? const Uuid().v4(),
        createdAtEpochMs =
            createdAtEpochMs ?? DateTime.now().millisecondsSinceEpoch;
}
