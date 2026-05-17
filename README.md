# VellumSync

VellumSync is a local-first Android e-ink note viewer and annotation app. It is initially focused on BOOX tablets with stylus support, tested on BOOX Note Air 2 Plus, and built around safe read-only handling of Supernote `.note` files.

VellumSync is independently developed and is not affiliated with, endorsed by, or sponsored by Ratta Supernote, BOOX, or Onyx.

## Current status

The current app is a fast read-only Supernote viewer with local Android overlay support.

Implemented and accepted:

```text
- App opens directly to the recent/local notes surface.
- Supernote .note files can be imported through Android document picker.
- Imported .note files are copied into the app's read-only cache.
- Viewer opens cached notes through a fast viewer inspection path.
- Full diagnostics are not run during normal note opening.
- Full diagnostics JSON is available only from explicit diagnostics/export actions.
- Supernote A5X note pages render through visual-layer and vector fallback paths.
- Pen style, grayscale, and eraser metadata are decoded for diagnostics and vector rendering.
- Local Android overlay strokes can be drawn with stylus input without modifying the original .note file.
- Tool rail supports view, pen, eraser, style, layer, page, zoom, export, sync placeholder, settings, and more panels.
- Tool rail position can be configured for left-handed and right-handed workflows.
- Device check and stylus probe remain available but are not part of the normal launch path.
```

Still intentionally limited:

```text
- No automatic cloud sync.
- No Supernote account login.
- No private BOOX e-ink API dependency.
- No destructive write-back to original Supernote .note files.
- No production Supernote-compatible writer yet.
- Sync and write-back screens are placeholders/research surfaces until validation is complete.
```

## Target devices

Primary Android target:

```text
BOOX Note Air 2 Plus
```

Expected compatible Android targets:

```text
- BOOX stylus-capable Android e-ink tablets
- Other Android tablets that expose stylus MotionEvent input
```

Reference Supernote profile used for the current compatibility work:

```text
Device family: Supernote A5X
Observed note marker: SN_FILE_VER_20230015
Observed page style: style_8mm_ruled_line_a5x
Observed layer protocol: RATTA_RLE
Observed vector section: TOTALPATH
```

## Repository

```text
GitHub: https://github.com/piyushdaiya/vellumsync
Android application ID: io.github.piyushdaiya.vellumsync
Minimum SDK: 28
Target SDK: 36
```

## Project layout

```text
app/src/main/java/io/github/piyushdaiya/vellumsync/
├── MainActivity.kt
├── device/                 # Android device/stylus detection
├── note/                   # Supernote parser, renderer, cache, overlay, export models
├── ui/                     # Compose app shell plus Android view note surface
└── util/                   # small shared helpers
```

Important note-layer areas:

```text
ImportedNoteCache.kt                       read-only cached copies of imported notes
SupernoteNoteInspector.kt                  full and fast inspection entry points
SupernoteContainerParser.kt                Supernote header/page/layer structure
SupernoteVisualDecoder.kt                  visual layer record discovery
SupernoteRattaRleVisualLayerRenderer.kt    experimental RATTA_RLE rendering
SupernoteTotalPathStructuralParser.kt      vector/TOTALPATH stroke parsing
SupernoteStrokeStyleDecoder.kt             pen/style/grayscale/eraser metadata mapping
LocalAnnotationOverlayStore.kt             local sidecar overlay persistence
OverlayRenderExporter.kt                   overlay export helpers
VellumCanonicalNoteModel.kt                app-owned canonical note model
```

Important UI areas:

```text
VellumSyncApp.kt             app navigation and launch flow
RecentNotesScreen.kt         recent/local note entry point
NoteViewerScreen.kt          fast viewer and tool rail binding
VellumNoteSurfaceView.kt     stylus overlay drawing surface
SupernoteVectorPreviewView.kt Supernote page/vector rendering surface
NoteInspectorScreen.kt       explicit diagnostics and export surface
DeviceCheckScreen.kt         Android/stylus capability screen
```

## Build

From the repository root:

```bash
./gradlew test
./gradlew assembleDebug
```

Install on a connected Android device:

```bash
./gradlew installDebug
```

Build a local release APK:

```bash
./gradlew assembleRelease
```

## Local test corpus

Do not commit private `.note`, `.pdf`, or diagnostic JSON files. Keep them in an ignored local folder:

```text
local-corpus/
├── vellnum-sync-test.note
├── vellnum-sync-test.pdf
├── vellumsync-note-diagnostics.json
└── other-private-test-fixtures.note
```

The repository `.gitignore` should keep local/private note files out of source control.

## Normal viewer flow

```text
Open VellumSync
→ Recent Notes
→ Import or select cached note
→ Viewer opens cached read-only copy
→ Fast viewer inspection parses only viewer-critical structure
→ Page renders through the best available renderer
→ Local overlay, if enabled, is drawn separately
```

Normal viewer opening must stay fast. Do not add full diagnostics, marker sweeps, visual payload probes, binary summaries, or full structural export preparation to the viewer open path.

## Diagnostics flow

```text
Open diagnostics screen
→ Select/import .note
→ Run full read-only inspection
→ Export diagnostics JSON or visual payload probe JSON
```

Diagnostics can be slower than the viewer because they are explicit troubleshooting actions. They must not block app launch or normal note opening.

## Safety rules

```text
- Never modify the imported original .note file.
- Never overwrite a Supernote .note file in place.
- Keep unknown Supernote sections and metadata observable in diagnostics.
- Treat all Supernote format behavior as versioned and device-specific.
- Keep Android local annotations in sidecar/app-owned storage until write-back is proven safe.
- Gate any future writer behind parse → render → compare → real-device validation.
```

## Acceptance checks

Core build checks:

```bash
./gradlew test
./gradlew assembleDebug
```

Manual device checks:

```text
- App launches on BOOX Note Air 2 Plus.
- App opens directly to Recent Notes.
- Importing a .note creates a cached read-only copy.
- Opening a cached note is fast and does not run full diagnostics.
- Pages render with the same content as the Supernote PDF export for the current test corpus.
- Local overlay strokes draw with the stylus, not with normal finger scrolling/taps.
- Original .note file remains unchanged.
- Export diagnostics JSON still works when explicitly requested.
```

## Documentation

```text
README.md        project overview and build/run instructions
Architecture.md  system design and module boundaries
UserGuide.md     app usage guide for BOOX/Supernote workflow
Scope.md         in-scope, non-scope, safety boundaries
roadmap.md       planned delivery sequence
Release.md       local and GitHub release notes
```

## References

Useful public research references:

```text
https://github.com/jya-dev/supernote-tool
https://github.com/philips/supernote-typescript
https://walnut356.github.io/posts/inspecting-the-supernote-note-format/
```

These are references only. VellumSync should keep a clean implementation based on observed behavior, local test files, and explicit validation.

## License

VellumSync is licensed under the Apache License, Version 2.0. See `LICENSE` for the full license text and `NOTICE` for copyright and attribution notices.

Unless a file states otherwise, repository source code, documentation, scripts, and project configuration are covered by Apache-2.0.

Private user notes, exported PDFs, screenshots, diagnostics JSON, and local test corpora are not part of the default project license and should not be committed unless they are intentionally added as public test fixtures with explicit permission.

VellumSync is an independent compatibility-focused application. Supernote, Ratta, BOOX, Onyx, Android, and Google names are used only for compatibility, platform, and testing references.

## Related documents

```text
Architecture.md
UserGuide.md
Scope.md
roadmap.md
Release.md
Legal.md
LICENSE
NOTICE
```
