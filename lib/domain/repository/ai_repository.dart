import 'ai_capability.dart';
import 'ai_queued_request.dart';

abstract class AiRepository {
  Future<bool> isEnabled();
  Future<void> setEnabled(bool enabled);
  Future<void> setApiKey(String apiKey);
  Future<String> getApiKey();
  Future<AiCapability> getCapability();
  Future<String> askQuestion(
      String bookId, String bookTitle, String? author, String question);
  Future<String> explainSelection(
      String bookId, String bookTitle, String selectedText);
  Future<String> translateSelection(String bookId, String bookTitle,
      String selectedText, String targetLanguage);
  Future<String> suggestSimilarBooks(
      String bookId, String bookTitle, String? author);
  Future<String> summarizeSection(
      String bookId, String bookTitle, String sectionText);
  Future<void> enqueueRequest(AiQueuedRequest request);
  Future<int> flushQueuedRequests();
}
