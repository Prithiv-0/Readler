# Readler QA Checklist

## Device & Lifecycle Matrix

- [ ] Cold launch
- [ ] Warm launch
- [ ] Rotate portrait/landscape while reading
- [ ] Background app and return
- [ ] Process death + restore

## Reader Scenarios

### EPUB
- [ ] Open EPUB with images
- [ ] Open EPUB with long chapters
- [ ] Change font scale while reading
- [ ] Change theme while reading
- [ ] Change scroll mode while reading

### PDF
- [ ] Open multi-page PDF
- [ ] Navigate prev/next pages
- [ ] Resume from last page after restart

## Library Scenarios

- [ ] Import multiple books in sequence
- [ ] Library ordering by last opened is correct
- [ ] Quick resume opens latest book
- [ ] Import cancellation is handled gracefully

## Error Handling

- [ ] Corrupt EPUB shows graceful message
- [ ] Missing file path shows graceful message
- [ ] Unsupported file type is rejected safely

## Search

- [ ] EPUB search with expected match
- [ ] EPUB search with no match
- [ ] PDF page search with numeric query
- [ ] PDF non-page query behavior is clear

## Telemetry Verification (Offline-safe)

- [ ] Telemetry file created at `filesDir/telemetry/events.log`
- [ ] Startup metric entry exists
- [ ] Book open metric entry exists
- [ ] Error event entry exists after induced non-fatal error

## Exit

- [ ] No critical blocker in matrix above
- [ ] Medium/low issues logged with reproduction steps
