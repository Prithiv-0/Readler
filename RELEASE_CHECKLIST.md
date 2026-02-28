# Readler Release Checklist

## Build & Environment

- [ ] `./gradlew.bat clean :app:assembleDebug` passes
- [ ] `./gradlew.bat :core:data:test :feature:reader:test` passes
- [ ] JDK 17 is configured for Gradle builds
- [ ] `local.properties` points to valid Android SDK

## Functional Validation

- [ ] Import EPUB succeeds and appears in library
- [ ] Import PDF succeeds and appears in library
- [ ] Resume opens the most recently opened book
- [ ] EPUB locator restore works after app restart
- [ ] PDF page restore works after app restart
- [ ] Search in EPUB jumps to result
- [ ] Search in PDF page query jumps to page

## Offline Reliability

- [ ] Airplane mode reading works for imported books
- [ ] Progress persists while offline
- [ ] Preferences persist while offline

## Telemetry & Diagnostics

- [ ] `filesDir/telemetry/events.log` is written during normal usage
- [ ] Non-fatal UI errors are logged locally
- [ ] Uncaught exception hook writes crash event locally
- [ ] No network dependency for telemetry path

## Performance Gate

- [ ] `startup_to_first_reader_ms` appears in telemetry log
- [ ] `book_open_ms` appears in telemetry log
- [ ] Large EPUB/PDF open times are within acceptable target for device class

## Packaging

- [ ] App version code/name updated
- [ ] Release notes drafted
- [ ] Known limitations documented
