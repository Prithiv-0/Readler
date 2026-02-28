# Readler (Offline-First Reader — Flutter)

A cross-platform Flutter e-reader app for EPUB and PDF files with optional Gemini AI integration.

## Features

- **EPUB & PDF reading** with format-aware rendering
- **Offline-first** — all metadata, progress, and books stored locally
- **Library management** — import, browse, and resume reading
- **Reader preferences** — font scale, font family, line spacing, theme mode, scroll mode
- **In-book search** — text search in EPUB, page jump in PDF
- **AI integration** (optional) — Gemini-powered Q&A, explain, translate, summarize, similar books
- **Offline AI queue** — requests queued when offline, flushed when connectivity returns

## Architecture

Built with clean architecture and modular design:

```
lib/
├── main.dart                     → App entry point
├── app/
│   └── app_container.dart        → Dependency injection / composition root
├── core/
│   ├── model/                    → Pure domain models (BookMetadata, ReadingProgress, etc.)
│   ├── database/                 → SQLite persistence via sqflite (AppDatabase, BookDao)
│   ├── storage/                  → Book file import/storage abstraction
│   ├── reader/                   → Format-agnostic reader engine (EPUB parser, PDF support)
│   └── data/                     → Repository implementations + metadata extraction
├── domain/
│   ├── repository/               → Repository contracts + AI types
│   └── usecase/                  → Use cases (GetLibraryBooks, ImportBook, OpenBook, etc.)
├── feature/
│   ├── library/                  → Library screen + ViewModel (ChangeNotifier)
│   └── reader/                   → Reader screen + ViewModel + preferences
└── ai/
    └── gemini_ai_repository.dart → Gemini AI implementation with caching & queuing
```

## Tech Stack

- **Flutter** with Material 3
- **Provider** for state management (ChangeNotifier pattern)
- **sqflite** for local database
- **shared_preferences** for reader preferences
- **archive** for EPUB ZIP parsing
- **flutter_html** for EPUB rendering
- **syncfusion_flutter_pdfviewer** for PDF rendering
- **file_picker** for book import
- **connectivity_plus** for network detection
- **http** for Gemini AI API calls

## Data Flow (Open Book)

1. UI triggers `ReaderViewModel.loadBook(bookId)`
2. ViewModel calls `OpenBookUseCase`
3. `BookRepository` fetches metadata/progress from SQLite
4. ViewModel selects engine via `ReaderEngineRegistry`
5. Engine opens and parses the book file
6. Position updates are persisted through `SaveReadingProgressUseCase`

## Getting Started

```bash
flutter pub get
flutter run
```

## Running Tests

```bash
flutter test
```

