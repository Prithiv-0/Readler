import 'dart:io';

import 'package:archive/archive.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../../model/book_format.dart';
import '../../storage/imported_book_file.dart';
import 'book_metadata_extractor.dart';

class BookImportMetadataExtractor implements BookMetadataExtractor {
  @override
  Future<BookImportMetadata> extract(ImportedBookFile importedFile) async {
    switch (importedFile.format) {
      case BookFormat.epub:
        return _extractEpub(importedFile.filePath);
      case BookFormat.pdf:
        return BookImportMetadata(title: importedFile.displayName);
    }
  }

  Future<BookImportMetadata> _extractEpub(String filePath) async {
    try {
      final file = File(filePath);
      final bytes = await file.readAsBytes();
      final archive = ZipDecoder().decodeBytes(bytes);

      // Find OPF file
      final containerFile = archive.findFile('META-INF/container.xml');
      if (containerFile == null) {
        return const BookImportMetadata();
      }

      final containerXml =
          String.fromCharCodes(containerFile.content as List<int>);
      final opfMatch =
          RegExp(r'full-path\s*=\s*"([^"]+)"').firstMatch(containerXml);
      if (opfMatch == null) return const BookImportMetadata();

      final opfPath = opfMatch.group(1)!;
      final opfFile = archive.findFile(opfPath);
      if (opfFile == null) return const BookImportMetadata();

      final opfContent = String.fromCharCodes(opfFile.content as List<int>);
      final baseDir = opfPath.contains('/')
          ? opfPath.substring(0, opfPath.lastIndexOf('/'))
          : '';

      // Extract title
      final titleMatch =
          RegExp(r'<dc:title[^>]*>([^<]+)</dc:title>').firstMatch(opfContent);
      final title = titleMatch?.group(1)?.trim();

      // Extract author
      final authorMatch = RegExp(r'<dc:creator[^>]*>([^<]+)</dc:creator>')
          .firstMatch(opfContent);
      final author = authorMatch?.group(1)?.trim();

      // Extract cover image
      String? coverPath;
      final coverMeta = RegExp(r'<meta[^>]*name\s*=\s*"cover"[^>]*content\s*=\s*"([^"]+)"')
          .firstMatch(opfContent);
      if (coverMeta != null) {
        final coverId = coverMeta.group(1)!;
        final itemMatch = RegExp(
                '<item[^>]*id\\s*=\\s*"${RegExp.escape(coverId)}"[^>]*href\\s*=\\s*"([^"]+)"[^>]*/?>',
            )
            .firstMatch(opfContent);
        if (itemMatch != null) {
          final coverHref = itemMatch.group(1)!;
          final entryPath = baseDir.isEmpty
              ? coverHref
              : '$baseDir/$coverHref';
          final coverEntry = archive.findFile(entryPath);
          if (coverEntry != null) {
            final appDir = await getApplicationDocumentsDirectory();
            final coversDir = Directory(p.join(appDir.path, 'covers'));
            if (!coversDir.existsSync()) coversDir.createSync(recursive: true);
            final ext = p.extension(coverHref).isNotEmpty
                ? p.extension(coverHref)
                : '.jpg';
            final coverFile =
                File(p.join(coversDir.path, '${p.basenameWithoutExtension(filePath)}$ext'));
            await coverFile.writeAsBytes(coverEntry.content as List<int>);
            coverPath = coverFile.path;
          }
        }
      }

      return BookImportMetadata(
        title: title,
        author: author,
        coverImagePath: coverPath,
      );
    } catch (_) {
      return const BookImportMetadata();
    }
  }
}
