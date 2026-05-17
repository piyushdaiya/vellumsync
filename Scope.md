# VellumSync Scope

## Current in-scope work

```text
- Android app for stylus-capable e-ink tablets.
- BOOX Note Air 2 Plus as the first test device.
- Read-only Supernote .note import and cache.
- Fast note viewer that avoids full diagnostics during opening.
- Supernote A5X parser research and compatibility reporting.
- Visual-layer rendering where supported.
- TOTALPATH/vector fallback rendering where visual decoding is unavailable.
- Stroke style, grayscale, and eraser metadata decoding for rendering/diagnostics.
- Local Android overlay strokes stored outside the original .note file.
- Explicit diagnostics JSON export for development and debugging.
- GitHub CI/release workflows for tests and APK artifacts.
```

## Explicit non-scope for the current app

```text
- Modifying original Supernote .note files.
- Automatic write-back to Supernote files.
- Supernote cloud login or private cloud APIs.
- BOOX private e-ink APIs as a required dependency.
- Production two-way sync.
- Recognition/OCR editing.
- PDF annotation as the primary data model.
- Destructive merge or conflict resolution.
```

## Research-only areas

These may exist in code as harnesses or experimental helpers, but they are not production features yet:

```text
- Supernote-compatible writer candidates.
- BOOX-created Supernote note generation.
- Offset reference adjustment research.
- Canonical Vellum package export.
- Full Supernote write-back.
- Round-trip sync.
```

## Safety boundaries

```text
- Original Supernote files must be treated as immutable input.
- Imported files must be copied to app-owned cache before viewer workflows.
- Local overlay data must be stored separately from Supernote source bytes.
- Unsupported or unknown Supernote versions must open read-only or fail safely.
- Full diagnostics must be user-triggered and must not slow normal note opening.
```

## Performance boundaries

Allowed during normal viewer open:

```text
- read cached note bytes
- fast viewer inspection
- page table/layer parsing needed for rendering
- current page render preparation
```

Not allowed during normal viewer open:

```text
- full diagnostics JSON generation
- all-marker binary sweeps
- visual payload probe export
- deep binary numeric summaries
- write-back research harness execution
- device capability detection
```

## License

VellumSync is licensed under the Apache License, Version 2.0. See `LICENSE` for the full license text and `NOTICE` for copyright and attribution notices.

Unless a file states otherwise, repository source code, documentation, scripts, and project configuration are covered by Apache-2.0.

Private user notes, exported PDFs, screenshots, diagnostics JSON, and local test corpora are not part of the default project license and should not be committed unless they are intentionally added as public test fixtures with explicit permission.

VellumSync is an independent compatibility-focused application. Supernote, Ratta, BOOX, Onyx, Android, and Google names are used only for compatibility, platform, and testing references.
