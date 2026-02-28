import 'dart:io';

import 'package:archive/archive.dart';

class ParsedEpub {
  final String htmlContent;
  final int chapterCount;

  const ParsedEpub({required this.htmlContent, required this.chapterCount});
}

class EpubArchiveParser {
  ParsedEpub parseCombinedHtml(String epubPath) {
    final file = File(epubPath);
    if (!file.existsSync()) {
      throw FileSystemException('EPUB file missing', epubPath);
    }

    final bytes = file.readAsBytesSync();
    final archive = ZipDecoder().decodeBytes(bytes);

    final rootOpfPath = _findRootPackagePath(archive);
    final opfContent = _readText(archive, rootOpfPath);
    final baseDir = rootOpfPath.contains('/')
        ? rootOpfPath.substring(0, rootOpfPath.lastIndexOf('/'))
        : '';

    final manifest = _parseManifest(opfContent);
    final spineItemIds = _parseSpine(opfContent);

    final chapters = <String>[];
    for (final itemId in spineItemIds) {
      final href = manifest[itemId];
      if (href == null) continue;
      final entryPath = _resolveRelativePath(baseDir, href);
      final text = _readTextOrNull(archive, entryPath);
      if (text != null) chapters.add(text);
    }

    if (chapters.isEmpty) {
      throw const FormatException('No readable EPUB chapters found');
    }

    final buffer = StringBuffer()
      ..write('<html><head><meta charset="utf-8"/></head><body>');
    for (var i = 0; i < chapters.length; i++) {
      buffer
        ..write('<section data-chapter="')
        ..write(i)
        ..write('">')
        ..write(_stripHtmlEnvelope(chapters[i]))
        ..write('</section>');
    }
    buffer.write('</body></html>');

    return ParsedEpub(
      htmlContent: buffer.toString(),
      chapterCount: chapters.length,
    );
  }

  String _findRootPackagePath(Archive archive) {
    final containerFile = archive.findFile('META-INF/container.xml');
    if (containerFile == null) {
      throw const FormatException('Invalid EPUB: missing META-INF/container.xml');
    }
    final containerXml = String.fromCharCodes(containerFile.content as List<int>);

    final match =
        RegExp(r'full-path\s*=\s*"([^"]+)"').firstMatch(containerXml);
    if (match == null) {
      throw const FormatException('Invalid EPUB: OPF package path not found');
    }
    return match.group(1)!;
  }

  Map<String, String> _parseManifest(String opfContent) {
    final regex =
        RegExp(r'<item[^>]*id\s*=\s*"([^"]+)"[^>]*href\s*=\s*"([^"]+)"[^>]*/?>');
    final map = <String, String>{};
    for (final match in regex.allMatches(opfContent)) {
      map[match.group(1)!] = match.group(2)!;
    }
    return map;
  }

  List<String> _parseSpine(String opfContent) {
    final regex = RegExp(r'<itemref[^>]*idref\s*=\s*"([^"]+)"[^>]*/?>');
    return regex.allMatches(opfContent).map((m) => m.group(1)!).toList();
  }

  String _resolveRelativePath(String baseDir, String href) {
    if (href.startsWith('/')) return href.substring(1);
    final cleanHref = href.contains('#') ? href.substring(0, href.indexOf('#')) : href;
    return baseDir.isEmpty ? cleanHref : '$baseDir/$cleanHref';
  }

  String _stripHtmlEnvelope(String rawChapter) {
    final match =
        RegExp(r'<body[^>]*>([\s\S]*?)</body>', caseSensitive: false)
            .firstMatch(rawChapter);
    return match?.group(1) ?? rawChapter;
  }

  String _readText(Archive archive, String path) {
    final file = archive.findFile(path);
    if (file == null) throw FormatException('Missing EPUB entry: $path');
    return String.fromCharCodes(file.content as List<int>);
  }

  String? _readTextOrNull(Archive archive, String path) {
    final file = archive.findFile(path);
    if (file == null) return null;
    return String.fromCharCodes(file.content as List<int>);
  }
}
