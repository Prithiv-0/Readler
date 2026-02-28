import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:provider/provider.dart';
import 'package:syncfusion_flutter_pdfviewer/pdfviewer.dart';

import '../../core/reader/reader_document.dart';
import 'prefs/reader_preferences.dart';
import 'prefs/reader_scroll_mode.dart';
import 'prefs/reader_theme_mode.dart';
import 'reader_view_model.dart';

class ReaderScreen extends StatelessWidget {
  final VoidCallback onBack;

  const ReaderScreen({super.key, required this.onBack});

  @override
  Widget build(BuildContext context) {
    return Consumer<ReaderViewModel>(
      builder: (context, vm, _) {
        final state = vm.state;

        if (state.isLoading) {
          return Scaffold(
            appBar: AppBar(
              leading: IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: onBack,
              ),
              title: const Text('Loading…'),
            ),
            body: const Center(child: CircularProgressIndicator()),
          );
        }

        if (state.errorMessage != null && state.currentDocument == null) {
          return Scaffold(
            appBar: AppBar(
              leading: IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: onBack,
              ),
              title: const Text('Error'),
            ),
            body: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  state.errorMessage!,
                  style: const TextStyle(color: Colors.red),
                  textAlign: TextAlign.center,
                ),
              ),
            ),
          );
        }

        final document = state.currentDocument;
        if (document == null) {
          return Scaffold(
            appBar: AppBar(
              leading: IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: onBack,
              ),
              title: const Text('Reader'),
            ),
            body: const Center(child: Text('No book loaded')),
          );
        }

        return _ReaderContent(
          document: document,
          bookTitle: state.currentBook?.title ?? 'Reader',
          preferences: state.preferences,
          aiEnabled: state.aiEnabled,
          aiResponse: state.aiResponse,
          isAiLoading: state.isAiLoading,
          errorMessage: state.errorMessage,
          onBack: onBack,
          vm: vm,
        );
      },
    );
  }
}

class _ReaderContent extends StatefulWidget {
  final ReaderDocument document;
  final String bookTitle;
  final ReaderPreferences preferences;
  final bool aiEnabled;
  final String? aiResponse;
  final bool isAiLoading;
  final String? errorMessage;
  final VoidCallback onBack;
  final ReaderViewModel vm;

  const _ReaderContent({
    required this.document,
    required this.bookTitle,
    required this.preferences,
    required this.aiEnabled,
    required this.aiResponse,
    required this.isAiLoading,
    required this.errorMessage,
    required this.onBack,
    required this.vm,
  });

  @override
  State<_ReaderContent> createState() => _ReaderContentState();
}

class _ReaderContentState extends State<_ReaderContent> {
  bool _showTools = false;
  final _searchController = TextEditingController();
  final _aiQuestionController = TextEditingController();
  final _aiSelectionController = TextEditingController();
  final _aiLanguageController = TextEditingController(text: 'Spanish');
  final _aiSummaryController = TextEditingController();
  final _aiApiKeyController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    _aiQuestionController.dispose();
    _aiSelectionController.dispose();
    _aiLanguageController.dispose();
    _aiSummaryController.dispose();
    _aiApiKeyController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: widget.onBack,
        ),
        title: Text(
          widget.bookTitle,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          IconButton(
            icon: Icon(_showTools ? Icons.close : Icons.settings),
            onPressed: () => setState(() => _showTools = !_showTools),
            tooltip: 'Tools',
          ),
        ],
      ),
      body: Column(
        children: [
          if (widget.errorMessage != null)
            Container(
              color: Colors.red.shade100,
              width: double.infinity,
              padding: const EdgeInsets.all(8),
              child: Text(
                widget.errorMessage!,
                style: const TextStyle(color: Colors.red),
              ),
            ),
          if (_showTools) _buildToolsPanel(),
          Expanded(child: _buildReaderView()),
        ],
      ),
    );
  }

  Widget _buildReaderView() {
    final doc = widget.document;
    if (doc is EpubDocument) {
      return _EpubViewer(
        document: doc,
        preferences: widget.preferences,
      );
    } else if (doc is PdfDocument) {
      return _PdfViewer(document: doc);
    }
    return const Center(child: Text('Unsupported format'));
  }

  Widget _buildToolsPanel() {
    return Container(
      constraints: const BoxConstraints(maxHeight: 350),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Theme controls
            _buildSectionTitle('Theme'),
            Wrap(
              spacing: 8,
              children: ReaderThemeMode.values.map((mode) {
                return ChoiceChip(
                  label: Text(mode.name),
                  selected: widget.preferences.themeMode == mode,
                  onSelected: (_) => widget.vm.onThemeModeChanged(mode),
                );
              }).toList(),
            ),
            const SizedBox(height: 12),

            // Scroll mode
            _buildSectionTitle('Scroll Mode'),
            Wrap(
              spacing: 8,
              children: ReaderScrollMode.values.map((mode) {
                return ChoiceChip(
                  label: Text(mode.name),
                  selected: widget.preferences.scrollMode == mode,
                  onSelected: (_) => widget.vm.onScrollModeChanged(mode),
                );
              }).toList(),
            ),
            const SizedBox(height: 12),

            // Font scale
            _buildSectionTitle(
                'Font Scale: ${widget.preferences.fontScale.toStringAsFixed(2)}'),
            Slider(
              value: widget.preferences.fontScale,
              min: 0.75,
              max: 2.0,
              divisions: 25,
              onChanged: (v) => widget.vm.onFontScaleChanged(v),
            ),

            // Font family
            _buildSectionTitle('Font Family'),
            Wrap(
              spacing: 8,
              children: ReaderFontFamily.values.map((family) {
                return ChoiceChip(
                  label: Text(family.label),
                  selected: widget.preferences.fontFamily == family,
                  onSelected: (_) => widget.vm.onFontFamilyChanged(family),
                );
              }).toList(),
            ),
            const SizedBox(height: 12),

            // Line spacing
            _buildSectionTitle('Line Spacing'),
            Wrap(
              spacing: 8,
              children: ReaderLineSpacing.values.map((spacing) {
                return ChoiceChip(
                  label: Text(spacing.label),
                  selected: widget.preferences.lineSpacing == spacing,
                  onSelected: (_) => widget.vm.onLineSpacingChanged(spacing),
                );
              }).toList(),
            ),
            const SizedBox(height: 12),

            // Search
            _buildSectionTitle('Search'),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _searchController,
                    decoration: const InputDecoration(
                      hintText: 'Search in book…',
                      isDense: true,
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: () =>
                      widget.vm.searchInCurrentBook(_searchController.text),
                  child: const Text('Search'),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // AI section
            _buildSectionTitle('AI Tools'),
            SwitchListTile(
              title: const Text('Enable AI'),
              value: widget.aiEnabled,
              onChanged: widget.vm.onAiEnabledChanged,
              dense: true,
            ),
            if (widget.aiEnabled) ...[
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _aiApiKeyController,
                      decoration: const InputDecoration(
                        hintText: 'Gemini API Key',
                        isDense: true,
                        border: OutlineInputBorder(),
                      ),
                      obscureText: true,
                    ),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    onPressed: () =>
                        widget.vm.saveAiApiKey(_aiApiKeyController.text),
                    child: const Text('Save'),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _aiQuestionController,
                decoration: const InputDecoration(
                  hintText: 'Ask a question about the book…',
                  isDense: true,
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 4),
              Wrap(
                spacing: 8,
                children: [
                  ElevatedButton(
                    onPressed: () =>
                        widget.vm.askBookQuestion(_aiQuestionController.text),
                    child: const Text('Ask'),
                  ),
                  ElevatedButton(
                    onPressed: widget.vm.askSimilarBooks,
                    child: const Text('Similar Books'),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _aiSelectionController,
                decoration: const InputDecoration(
                  hintText: 'Selected text for explain/translate…',
                  isDense: true,
                  border: OutlineInputBorder(),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  ElevatedButton(
                    onPressed: () => widget.vm
                        .explainSelectedText(_aiSelectionController.text),
                    child: const Text('Explain'),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: _aiLanguageController,
                      decoration: const InputDecoration(
                        hintText: 'Language',
                        isDense: true,
                        border: OutlineInputBorder(),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    onPressed: () => widget.vm.translateSelectedText(
                      _aiSelectionController.text,
                      _aiLanguageController.text,
                    ),
                    child: const Text('Translate'),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _aiSummaryController,
                decoration: const InputDecoration(
                  hintText: 'Section text for summary…',
                  isDense: true,
                  border: OutlineInputBorder(),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: 4),
              ElevatedButton(
                onPressed: () => widget.vm
                    .summarizeCurrentSection(_aiSummaryController.text),
                child: const Text('Summarize'),
              ),
              if (widget.isAiLoading) ...[
                const SizedBox(height: 8),
                const LinearProgressIndicator(),
              ],
              if (widget.aiResponse != null) ...[
                const SizedBox(height: 8),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Theme.of(context)
                        .colorScheme
                        .surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(widget.aiResponse!),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
              fontWeight: FontWeight.bold,
            ),
      ),
    );
  }
}

class _EpubViewer extends StatelessWidget {
  final EpubDocument document;
  final ReaderPreferences preferences;

  const _EpubViewer({required this.document, required this.preferences});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Html(
        data: document.htmlContent,
        style: {
          'body': Style(
            fontSize: FontSize(16.0 * preferences.fontScale),
            fontFamily: preferences.fontFamily.css,
            lineHeight: LineHeight(preferences.lineSpacing.value),
          ),
        },
      ),
    );
  }
}

class _PdfViewer extends StatelessWidget {
  final PdfDocument document;

  const _PdfViewer({required this.document});

  @override
  Widget build(BuildContext context) {
    return SfPdfViewer.file(
      File(document.filePath),
      initialPageNumber: document.initialPageIndex + 1,
    );
  }
}
