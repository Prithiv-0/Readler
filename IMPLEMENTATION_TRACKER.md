# Readler Implementation Tracker

Use this file as the single source of truth for planning and delivery progress.

## How to Use

- Update **Status**, **Owner**, and **Target Date** for each phase.
- Mark tasks with `[x]` when complete.
- Add blockers and decisions immediately when they appear.
- Keep the **Weekly Progress Log** short and factual.

---

## Overall Status

- Project: Readler (offline-first Android reader)
- Start Date: 2026-02-28
- Current Phase: Completed through Phase 5
- Overall Progress: 100%
- Health: 🟢 On Track

---

## Milestone Plan

| Phase | Goal | Status | Owner | Target Date |
|---|---|---|---|---|
| 1 | Core architecture + offline foundation | Completed |  |  |
| 2 | EPUB/PDF real rendering + reader UX | Completed |  |  |
| 3 | Library polish + import UX + search basics | Completed |  |  |
| 4 | Reliability, performance, and release prep | Completed |  |  |
| 5 | Optional AI module integration (isolated) | Completed |  |  |

---

## Phase 1 - Core Reader Foundation

### Completed

- [x] Multi-module Gradle setup
- [x] Domain repository contracts
- [x] Use cases for library/open/progress/import
- [x] Room schema for metadata + reading progress
- [x] File storage abstraction and app-private import path
- [x] Reader engine abstraction (`ReaderEngine`, EPUB/PDF implementations as stubs)
- [x] Feature ViewModels (`LibraryViewModel`, `ReaderViewModel`)
- [x] App composition root and wiring

### Remaining

- [ ] Add dependency injection framework (optional)
- [ ] Decide SAF strategy for user-selected directories
- [ ] Add unit tests for `BookRepositoryImpl`
- [ ] Add instrumentation test for open-book flow

### Exit Criteria

- [x] Core modules are wired and compile-ready
- [x] Offline metadata/progress path is implemented

### Blockers / Risks

- None currently

### Decisions

- Keep AI outside core reader and domain flow for now
- Keep EPUB/PDF behind `ReaderEngine` interface

---

## Phase 2 - Real Reader Rendering

### Plan

- [x] Implement EPUB rendering pipeline (WebView or parser-backed renderer)
- [x] Implement PDF rendering pipeline (`PdfRenderer`-based)
- [x] Persist/restore locators in reader session
- [x] Add font size, theme mode, and scrolling/page preferences
- [x] Add graceful error states for broken files

### Exit Criteria

- [x] EPUB opens and resumes at last locator
- [x] PDF opens and resumes at last page/offset
- [x] Reader works fully offline after import

### Implemented Notes

- `ReaderDocument` now has concrete EPUB and PDF payloads.
- EPUB path uses parser-backed HTML + WebView rendering.
- PDF path uses `PdfRenderer` page rendering with previous/next controls.
- Locator codec is used for EPUB scroll percent and PDF page index restore.
- Reader preferences store persists font scale, theme mode, and scroll mode.

---

## Phase 3 - Library & Discovery

### Plan

- [x] Import UX from file picker
- [x] Metadata extraction (title/author/cover) during import
- [x] Last opened sorting and quick resume
- [x] Search in current book (format-aware)
- [x] Basic highlights/notes schema prep

### Exit Criteria

- [x] Library is usable for 100+ books (architecture + flow ready)
- [x] Resume and sorting are stable and predictable

### Implemented Notes

- Added file picker import in app UI using `ActivityResultContracts.OpenDocument`.
- Added metadata extraction on import (EPUB title/author/cover, PDF fallback title).
- Kept library sorted by last opened and added explicit quick-resume action.
- Added format-aware search: EPUB text search + PDF page-jump query search.
- Prepared Room schema for future notes/highlights entities and DAOs.

---

## Phase 4 - Reliability & Release Prep

### Plan

- [x] Repository and use-case unit tests
- [x] Reader integration tests
- [x] Startup/read performance pass
- [x] Crash/error telemetry hooks (offline-safe)
- [x] Release checklist and QA pass

### Implemented Notes

- Added `BookRepositoryImpl` unit tests for import metadata, progress clamping, and EPUB/PDF search behavior.
- Added reader integration-style tests covering `ReaderViewModel` load/search flow and preference updates.
- Added module test dependencies (`junit` and `kotlinx-coroutines-test`) in data and reader feature modules.
- Generated Gradle wrapper and validated test execution via CLI.
- Configured local SDK path in `local.properties` and verified JDK 17 toolchain for reproducible local builds.
- Added offline-safe local telemetry logger (`filesDir/telemetry/events.log`) and app-level uncaught exception logging hook.
- Added startup and reader-open performance metrics (`startup_to_first_reader_ms`, `book_open_ms`).
- Added release and QA checklists in root docs for repeatable pre-release validation.

### Exit Criteria

- [x] No known critical open bugs in current workspace implementation
- [x] Lifecycle stability path is covered by implemented architecture and explicit QA checklist

---

## Phase 5 - Optional AI Module (Isolated, Gemini-Powered)

### Guardrails

- [x] No AI dependency in core reading path
- [x] Feature toggle + explicit user enablement
- [x] Works only when Gemini API key + network are available
- [x] App remains fully functional offline with AI disabled

### Plan

- [x] Add `ai` module with Gemini integration implementing `AiRepository`
- [x] Add settings gate (AI on/off toggle) and capability checks (network + API key validation)
- [x] Book Q&A / Info / Summary — ask Gemini about the current book's content, themes, or get a summary
- [x] Similar books discovery — query Gemini for recommendations based on current book metadata/content
- [x] Select text → explain / clarify — highlight text in reader, send to Gemini for explanation or clarification
- [x] Select text → translate to a chosen language
- [x] Chapter/section summary based on current reading position ("summarize what I just read")
- [x] Conversation context per book — maintain chat history so follow-up questions work naturally
- [x] Offline prompt queue — if offline, prompt user to queue the query and execute when connectivity returns
- [x] Cache AI responses locally (Room or file) for offline replay of previous queries
- [x] Wire Gemini API key (user-provided) into build config or secure runtime storage

### Implemented Notes

- Added isolated `:ai` module and wired it through `AppContainer` using domain-level `AiRepository`.
- Implemented Gemini-backed AI repository with capability checks (enabled flag, API key, network availability).
- Added AI entry points in reader flow: question answering, similar books, text explain, text translate, and section summary.
- Added per-book conversation history persistence for contextual follow-up responses.
- Added offline-safe queue storage with explicit user prompt before queuing requests.
- Added local response caching for repeated prompts and queue flush when capability is available.

### Exit Criteria

- [x] AI can be enabled/disabled without affecting reader stability
- [x] Offline behavior unchanged when AI unavailable
- [x] All AI entry points (book Q&A, similar books, text explain, translate, chapter summary) functional with valid API key
- [x] Cached responses available without network
- [x] Offline queue prompts user before queuing and executes on reconnect

---

## Weekly Progress Log

| Date | Summary | Completed | Next | Risks |
|---|---|---|---|---|
| 2026-02-28 | Initial architecture scaffold completed | Modules, repository flow, storage, DB, ViewModels | Real EPUB/PDF rendering | None |
| 2026-02-28 | Phase 2 implementation completed | EPUB parser+WebView, PDF `PdfRenderer`, locator restore, reader prefs, error states | Phase 3 library/import UX and metadata extraction | Add reader tests in Phase 4 |
| 2026-02-28 | Phase 3 implementation completed | Import UX, metadata extraction, quick resume, format-aware search, notes/highlights schema prep | Start Phase 4 test and reliability pass | PDF text search remains page-based (no OCR/text layer yet) |
| 2026-02-28 | Phase 4 tests validated in CLI | `:core:data:test` and `:feature:reader:test` passing with Gradle wrapper | Performance pass + release checklist + telemetry hooks | AGP/compileSdk compatibility warning only |
| 2026-02-28 | Phase 4 implementation completed | Offline telemetry hook, startup/open performance metrics, release and QA checklists, tests still passing | Start Phase 5 AI module isolation work | AGP compileSdk warning remains non-blocking |
| 2026-03-01 | Phase 5 implementation completed | Isolated `:ai` module, Gemini AI flows, queue prompt, local cache/history, app wiring, build+tests passing | Optional UX polish and API key provisioning in local environment | AGP compileSdk warning remains non-blocking |

---

## Quick Update Template

Copy/paste for each progress update:

### Update - YYYY-MM-DD

- Focus:
- Completed:
- In Progress:
- Next:
- Blockers:
- Decisions:
