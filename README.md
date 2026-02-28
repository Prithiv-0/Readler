# Readler (Offline-First Reader Architecture Scaffold)

This workspace now contains a modular Android scaffold designed for:

- EPUB + PDF reading
- Local-first metadata and progress storage
- Full offline reading flow
- Future AI integration without coupling to core reading

## Module Layout

- `app` → app entry point + composition root (`AppContainer`)
- `core:model` → shared pure models (`BookMetadata`, `ReadingProgress`)
- `core:database` → Room entities/DAO/database (`books` table)
- `core:storage` → book file import/open abstraction (`BookFileStorage`)
- `core:reader` → format-agnostic reader engine abstraction (`ReaderEngine`)
- `core:data` → repository implementations (`BookRepositoryImpl`)
- `domain` → repository contracts + use cases
- `feature:library` → `LibraryViewModel` and UI state
- `feature:reader` → `ReaderViewModel` and UI state

## Data Flow (Open Book)

1. UI triggers `ReaderViewModel.loadBook(bookId)`
2. ViewModel calls `OpenBookUseCase`
3. `BookRepository` fetches metadata/progress from Room
4. ViewModel selects engine via `ReaderEngineRegistry`
5. Engine opens file stream via `BookFileStorage`
6. Position updates are persisted through `SaveReadingProgressUseCase`

## Why this matches your requirements

- Offline-first: all required reading state is local (Room + app-private files)
- Lifecycle-safe: state in `ViewModel` / async work in coroutines
- Modular: clear separation between storage, database, domain, reader engines, features
- AI optional: `AiRepository` exists only as a contract and is not wired into reader flow

## Next Implementation Step

Replace `EpubReaderEngine` and `PdfReaderEngine` stubs with concrete renderers (WebView/EPUB parser + PdfRenderer) while keeping the same `ReaderEngine` API.
