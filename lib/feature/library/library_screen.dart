import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/model/book_metadata.dart';
import 'library_view_model.dart';

class LibraryScreen extends StatelessWidget {
  final void Function(BookMetadata book) onBookSelected;

  const LibraryScreen({super.key, required this.onBookSelected});

  @override
  Widget build(BuildContext context) {
    return Consumer<LibraryViewModel>(
      builder: (context, vm, _) {
        final state = vm.state;
        return Scaffold(
          appBar: AppBar(
            title: const Text('Readler'),
            actions: [
              IconButton(
                icon: const Icon(Icons.refresh),
                onPressed: vm.refreshLibrary,
                tooltip: 'Refresh',
              ),
            ],
          ),
          body: state.isLoading
              ? const Center(child: CircularProgressIndicator())
              : state.books.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.book, size: 64,
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurface
                                  .withValues(alpha: 0.4)),
                          const SizedBox(height: 16),
                          Text(
                            'No books yet',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 8),
                          const Text('Tap + to import an EPUB or PDF'),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.all(8),
                      itemCount: state.books.length,
                      itemBuilder: (context, index) {
                        final book = state.books[index];
                        return _BookListItem(
                          book: book,
                          onTap: () => onBookSelected(book),
                        );
                      },
                    ),
          floatingActionButton: FloatingActionButton(
            onPressed: () => _importBook(context, vm),
            tooltip: 'Import Book',
            child: const Icon(Icons.add),
          ),
        );
      },
    );
  }

  Future<void> _importBook(BuildContext context, LibraryViewModel vm) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['epub', 'pdf'],
    );

    if (result != null && result.files.single.path != null) {
      await vm.importBookFromPath(result.files.single.path!);
    }
  }
}

class _BookListItem extends StatelessWidget {
  final BookMetadata book;
  final VoidCallback onTap;

  const _BookListItem({required this.book, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: book.coverImagePath != null &&
                File(book.coverImagePath!).existsSync()
            ? ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: Image.file(
                  File(book.coverImagePath!),
                  width: 40,
                  height: 56,
                  fit: BoxFit.cover,
                ),
              )
            : Container(
                width: 40,
                height: 56,
                decoration: BoxDecoration(
                  color: Theme.of(context)
                      .colorScheme
                      .primaryContainer,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Icon(
                  book.format.name == 'epub' ? Icons.menu_book : Icons.picture_as_pdf,
                  color: Theme.of(context).colorScheme.onPrimaryContainer,
                ),
              ),
        title: Text(
          book.title,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: book.author != null
            ? Text(
                book.author!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              )
            : null,
        trailing: Text(
          book.format.name.toUpperCase(),
          style: Theme.of(context).textTheme.labelSmall,
        ),
        onTap: onTap,
      ),
    );
  }
}
