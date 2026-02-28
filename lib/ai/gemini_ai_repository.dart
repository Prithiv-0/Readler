import 'dart:convert';
import 'dart:io';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/repository/ai_capability.dart';
import '../domain/repository/ai_queued_request.dart';
import '../domain/repository/ai_repository.dart';

class GeminiAiRepository implements AiRepository {
  static const _keyEnabled = 'ai_enabled';
  static const _keyApiKey = 'ai_api_key';
  static const _maxConversationEntries = 20;

  final String _defaultApiKey;
  late final Directory _storageDir;
  bool _initialized = false;

  GeminiAiRepository({String defaultApiKey = ''})
      : _defaultApiKey = defaultApiKey;

  Future<void> _ensureInit() async {
    if (_initialized) return;
    final appDir = await getApplicationDocumentsDirectory();
    _storageDir = Directory(p.join(appDir.path, 'ai'));
    if (!_storageDir.existsSync()) {
      _storageDir.createSync(recursive: true);
    }
    _initialized = true;
  }

  File get _cacheFile => File(p.join(_storageDir.path, 'cache.jsonl'));
  File get _queueFile => File(p.join(_storageDir.path, 'queue.jsonl'));

  @override
  Future<bool> isEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyEnabled) ?? false;
  }

  @override
  Future<void> setEnabled(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyEnabled, enabled);
  }

  @override
  Future<void> setApiKey(String apiKey) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyApiKey, apiKey.trim());
  }

  @override
  Future<String> getApiKey() async => _currentApiKey();

  @override
  Future<AiCapability> getCapability() async {
    final prefs = await SharedPreferences.getInstance();
    return AiCapability(
      enabled: prefs.getBool(_keyEnabled) ?? false,
      hasApiKey: (await _currentApiKey()).isNotEmpty,
      hasNetwork: await _isNetworkAvailable(),
    );
  }

  @override
  Future<String> askQuestion(
      String bookId, String bookTitle, String? author, String question) {
    final normalized = question.trim();
    return _generateWithCaching(
      request: AiQueuedRequest(
        bookId: bookId,
        bookTitle: bookTitle,
        author: author,
        type: AiRequestType.question,
        prompt: normalized,
      ),
      promptText: _buildQuestionPrompt(bookId, bookTitle, author, normalized),
    );
  }

  @override
  Future<String> explainSelection(
      String bookId, String bookTitle, String selectedText) {
    final normalized = selectedText.trim();
    return _generateWithCaching(
      request: AiQueuedRequest(
        bookId: bookId,
        bookTitle: bookTitle,
        type: AiRequestType.explainSelection,
        prompt: 'Explain this selected text.',
        selectedText: normalized,
      ),
      promptText: _buildExplainPrompt(bookTitle, normalized),
    );
  }

  @override
  Future<String> translateSelection(String bookId, String bookTitle,
      String selectedText, String targetLanguage) {
    final normalized = selectedText.trim();
    final target = targetLanguage.trim();
    return _generateWithCaching(
      request: AiQueuedRequest(
        bookId: bookId,
        bookTitle: bookTitle,
        type: AiRequestType.translateSelection,
        prompt: 'Translate selected text.',
        selectedText: normalized,
        targetLanguage: target,
      ),
      promptText: _buildTranslatePrompt(bookTitle, normalized, target),
    );
  }

  @override
  Future<String> suggestSimilarBooks(
      String bookId, String bookTitle, String? author) {
    return _generateWithCaching(
      request: AiQueuedRequest(
        bookId: bookId,
        bookTitle: bookTitle,
        author: author,
        type: AiRequestType.similarBooks,
        prompt: 'Suggest similar books',
      ),
      promptText: _buildSimilarBooksPrompt(bookTitle, author),
    );
  }

  @override
  Future<String> summarizeSection(
      String bookId, String bookTitle, String sectionText) {
    final normalized = sectionText.trim();
    return _generateWithCaching(
      request: AiQueuedRequest(
        bookId: bookId,
        bookTitle: bookTitle,
        type: AiRequestType.sectionSummary,
        prompt: 'Summarize current section.',
        sectionContext: normalized,
      ),
      promptText: _buildSectionSummaryPrompt(bookTitle, normalized),
    );
  }

  @override
  Future<void> enqueueRequest(AiQueuedRequest request) async {
    await _ensureInit();
    _appendJsonLine(_queueFile, _requestToJson(request));
  }

  @override
  Future<int> flushQueuedRequests() async {
    await _ensureInit();
    final capability = await getCapability();
    if (!capability.canRun || !_queueFile.existsSync()) return 0;

    final lines = _queueFile.readAsLinesSync().where((l) => l.isNotEmpty);
    final queued = <AiQueuedRequest>[];
    for (final line in lines) {
      try {
        queued.add(_jsonToRequest(jsonDecode(line) as Map<String, dynamic>));
      } catch (_) {}
    }

    if (queued.isEmpty) {
      _queueFile.writeAsStringSync('');
      return 0;
    }

    var processed = 0;
    final remaining = <AiQueuedRequest>[];
    for (final request in queued) {
      try {
        final prompt = _buildPromptForRequest(request);
        await _generateRaw(prompt, request.bookId);
        processed++;
      } catch (_) {
        remaining.add(request);
      }
    }

    _queueFile.writeAsStringSync('');
    for (final r in remaining) {
      _appendJsonLine(_queueFile, _requestToJson(r));
    }
    return processed;
  }

  // -- Private helpers --

  Future<String> _generateWithCaching({
    required AiQueuedRequest request,
    required String promptText,
  }) async {
    await _ensureInit();
    final capability = await getCapability();
    if (!capability.enabled) throw StateError('AI is disabled');
    if (!capability.hasApiKey) throw StateError('Gemini API key missing');
    if (!capability.hasNetwork) throw StateError('Network unavailable');

    final cacheKey = '${request.bookId}::${promptText.trim()}';
    final cached = _readCached(cacheKey);
    if (cached != null) return cached;

    final response = await _generateRaw(promptText, request.bookId);
    _writeCache(cacheKey, request.bookId, response);
    return response;
  }

  Future<String> _generateRaw(String promptText, String bookId) async {
    final apiKey = await _currentApiKey();
    if (apiKey.isEmpty) throw StateError('Gemini API key missing');

    final uri = Uri.parse(
        'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey');

    final requestBody = jsonEncode({
      'contents': [
        {
          'parts': [
            {'text': promptText}
          ]
        }
      ]
    });

    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: requestBody,
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw StateError(
          'Gemini API error: ${response.statusCode}');
    }

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final candidates = json['candidates'] as List<dynamic>?;

    String? answer;
    if (candidates != null && candidates.isNotEmpty) {
      final content =
          (candidates[0] as Map<String, dynamic>)['content'] as Map<String, dynamic>?;
      final parts = content?['parts'];
      if (parts is List && parts.isNotEmpty) {
        answer = parts.first['text']?.toString().trim();
      }
    }

    if (answer == null || answer.isEmpty) {
      throw StateError('Gemini returned an empty response');
    }

    _appendConversation(bookId, promptText, answer);
    return answer;
  }

  void _appendConversation(String bookId, String prompt, String response) {
    final file = File(p.join(_storageDir.path, 'conversation_$bookId.jsonl'));
    final line = jsonEncode({
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'prompt': prompt,
      'response': response,
    });
    _appendJsonLine(file, line);

    final lines = file.readAsLinesSync();
    if (lines.length > _maxConversationEntries) {
      file.writeAsStringSync(
          '${lines.sublist(lines.length - _maxConversationEntries).join('\n')}\n');
    }
  }

  String _buildPromptForRequest(AiQueuedRequest request) {
    switch (request.type) {
      case AiRequestType.question:
        return _buildQuestionPrompt(
            request.bookId, request.bookTitle, request.author, request.prompt);
      case AiRequestType.explainSelection:
        return _buildExplainPrompt(
            request.bookTitle, request.selectedText ?? '');
      case AiRequestType.translateSelection:
        return _buildTranslatePrompt(request.bookTitle,
            request.selectedText ?? '', request.targetLanguage ?? '');
      case AiRequestType.similarBooks:
        return _buildSimilarBooksPrompt(request.bookTitle, request.author);
      case AiRequestType.sectionSummary:
        return _buildSectionSummaryPrompt(
            request.bookTitle, request.sectionContext ?? '');
    }
  }

  String _buildQuestionPrompt(
      String bookId, String bookTitle, String? author, String question) {
    final context = _readConversationContext(bookId);
    return '''You are assisting a reader.
Book title: $bookTitle
Author: ${author ?? 'Unknown'}
Keep answers concise and grounded in the book context.
Previous conversation context:
$context

User question:
$question''';
  }

  String _buildExplainPrompt(String bookTitle, String selectedText) {
    return '''Explain the selected passage from "$bookTitle" in clear, simple language.
Include short clarification of hard words.

Selected text:
$selectedText''';
  }

  String _buildTranslatePrompt(
      String bookTitle, String selectedText, String targetLanguage) {
    return '''Translate the selected passage from "$bookTitle" into $targetLanguage.
Keep the original tone and meaning.

Selected text:
$selectedText''';
  }

  String _buildSimilarBooksPrompt(String bookTitle, String? author) {
    return '''Recommend 5 books similar to "$bookTitle" by ${author ?? 'the same genre'}.
Provide title, author, and one-line reason each.''';
  }

  String _buildSectionSummaryPrompt(String bookTitle, String sectionText) {
    return '''Summarize the following section from "$bookTitle".
Keep it under 8 bullet points and include key events/concepts.

Section text:
$sectionText''';
  }

  String _readConversationContext(String bookId) {
    final file = File(p.join(_storageDir.path, 'conversation_$bookId.jsonl'));
    if (!file.existsSync()) return '(none)';
    final lines = file.readAsLinesSync();
    final recent = lines.length > 5 ? lines.sublist(lines.length - 5) : lines;
    final result = recent.join('\n').trim();
    return result.isEmpty ? '(none)' : result;
  }

  String _requestToJson(AiQueuedRequest request) {
    return jsonEncode({
      'requestId': request.requestId,
      'bookId': request.bookId,
      'bookTitle': request.bookTitle,
      'author': request.author,
      'type': request.type.name,
      'prompt': request.prompt,
      'selectedText': request.selectedText,
      'targetLanguage': request.targetLanguage,
      'sectionContext': request.sectionContext,
      'createdAtEpochMs': request.createdAtEpochMs,
    });
  }

  AiQueuedRequest _jsonToRequest(Map<String, dynamic> json) {
    return AiQueuedRequest(
      requestId: json['requestId'] as String? ?? '',
      bookId: json['bookId'] as String? ?? '',
      bookTitle: json['bookTitle'] as String? ?? '',
      author: (json['author'] as String?)?.isNotEmpty == true
          ? json['author'] as String
          : null,
      type: AiRequestType.values.firstWhere(
        (e) => e.name == json['type'],
        orElse: () => AiRequestType.question,
      ),
      prompt: json['prompt'] as String? ?? '',
      selectedText: (json['selectedText'] as String?)?.isNotEmpty == true
          ? json['selectedText'] as String
          : null,
      targetLanguage: (json['targetLanguage'] as String?)?.isNotEmpty == true
          ? json['targetLanguage'] as String
          : null,
      sectionContext: (json['sectionContext'] as String?)?.isNotEmpty == true
          ? json['sectionContext'] as String
          : null,
      createdAtEpochMs: json['createdAtEpochMs'] as int? ??
          DateTime.now().millisecondsSinceEpoch,
    );
  }

  String? _readCached(String cacheKey) {
    if (!_cacheFile.existsSync()) return null;
    final lines = _cacheFile.readAsLinesSync().reversed;
    for (final line in lines) {
      try {
        final json = jsonDecode(line) as Map<String, dynamic>;
        if (json['cacheKey'] == cacheKey) {
          final response = json['response'] as String?;
          if (response != null && response.isNotEmpty) return response;
        }
      } catch (_) {}
    }
    return null;
  }

  void _writeCache(String cacheKey, String bookId, String response) {
    final line = jsonEncode({
      'cacheKey': cacheKey,
      'bookId': bookId,
      'response': response,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
    });
    _appendJsonLine(_cacheFile, line);
  }

  void _appendJsonLine(File file, String line) {
    file.writeAsStringSync('$line\n', mode: FileMode.append);
  }

  Future<bool> _isNetworkAvailable() async {
    final result = await Connectivity().checkConnectivity();
    return result.any((r) => r != ConnectivityResult.none);
  }

  Future<String> _currentApiKey() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_keyApiKey)?.trim() ?? '';
    if (saved.isNotEmpty) return saved;
    return _defaultApiKey.trim();
  }
}
