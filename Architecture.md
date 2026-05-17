# VellumSync Architecture

## 1. Product definition

VellumSync is a local-first Android e-ink note app for opening, viewing, inspecting, annotating, and eventually syncing Supernote-compatible `.note` files on Android tablets.

The current product priority is:

```text
BOOX Note Air 2 Plus first
Android stylus tablets second
Supernote-compatible read-only viewing before write-back
Fast viewer open before deep diagnostics
```

## 2. Design principles

```text
Local-first
Viewer-first
Read-only before write-back
Never overwrite original Supernote files
Keep local overlay data separate from Supernote source files
Treat unknown .note fields as data to preserve or diagnose, not data to discard
Keep diagnostics explicit and outside the normal open path
Prefer simple Android APIs before BOOX/private APIs
Use real Supernote exports as visual acceptance references
```

## 3. Runtime navigation model

```text
VellumSyncApp
│
├── Recent Notes
│   ├── import/open cached .note
│   ├── open viewer
│   └── open diagnostics screen
│
├── Note Viewer
│   ├── fast viewer inspection
│   ├── page render surface
│   ├── local overlay surface
│   ├── tool rail and panels
│   └── explicit export actions
│
├── Note Diagnostics
│   ├── full parser/marker inspection
│   ├── full diagnostic JSON export
│   ├── visual payload probe export
│   └── TOTALPATH structural export
│
└── Device Check
    ├── Android input-device scan
    ├── stylus capability summary
    └── runtime stylus probe
```

The app no longer performs device diagnostics on startup. Device Check is available when needed, but the launch surface is the note workflow.

## 4. High-level system architecture

```text
VellumSync Android App
│
├── UI Layer
│   ├── Compose app shell
│   ├── Recent notes surface
│   ├── XML-backed note viewer surface
│   ├── e-ink-oriented tool rail
│   └── diagnostics/export screens
│
├── Device Layer
│   ├── Android device profile
│   ├── input-device capability scan
│   ├── MotionEvent stylus probe
│   └── persisted rail/stylus preferences
│
├── Import and Cache Layer
│   ├── Android document picker
│   ├── read-only cached copy
│   ├── import source validation
│   ├── cache metadata
│   └── recent-note library index
│
├── Supernote Compatibility Layer
│   ├── header and marker inspection
│   ├── container/page/layer parser
│   ├── visual layer discovery
│   ├── RATTA_RLE visual renderer
│   ├── TOTALPATH vector parser
│   ├── stroke style/grayscale/eraser decoder
│   └── full diagnostics model
│
├── Rendering Layer
│   ├── visual-layer renderer
│   ├── vector fallback renderer
│   ├── A5X transform calibration
│   ├── PDF-reference comparison support
│   └── local overlay renderer
│
├── Local Overlay Layer
│   ├── stylus-only sidecar strokes
│   ├── page-level overlay storage
│   ├── overlay undo/clear operations
│   └── overlay PNG/PDF/package export
│
└── Future Sync/Write Layer
    ├── canonical note model
    ├── package export model
    ├── write-back research harness
    ├── Supernote-compatible writer candidate
    └── future conflict resolver
```

## 5. Package layout

```text
io.github.piyushdaiya.vellumsync
├── MainActivity.kt
├── device/
│   ├── DeviceCapabilityDetector.kt
│   ├── DeviceProfile.kt
│   ├── StylusProbePersistence.kt
│   └── StylusProbeView.kt
├── note/
│   ├── ImportedNoteCache.kt
│   ├── LocalAnnotationOverlayStore.kt
│   ├── OverlayRenderExporter.kt
│   ├── SupernoteContainerParser.kt
│   ├── SupernoteFeatureCompatibility.kt
│   ├── SupernoteInspectionReport.kt
│   ├── SupernoteNoteInspector.kt
│   ├── SupernoteRattaRleVisualLayerRenderer.kt
│   ├── SupernoteStrokeGeometry.kt
│   ├── SupernoteStrokeStyleDecoder.kt
│   ├── SupernoteStructuralStrokeRenderBridge.kt
│   ├── SupernoteTotalPathStructuralParser.kt
│   ├── SupernoteTotalPathTransformCalibrator.kt
│   ├── SupernoteVisualDecoder.kt
│   ├── SupernoteVisualPayloadProbe.kt
│   ├── VellumCanonicalNoteModel.kt
│   └── VellumSyncPackageExporter.kt
├── ui/
│   ├── VellumSyncApp.kt
│   ├── RecentNotesScreen.kt
│   ├── NoteViewerScreen.kt
│   ├── VellumNoteSurfaceView.kt
│   ├── SupernoteVectorPreviewView.kt
│   ├── NoteInspectorScreen.kt
│   ├── DeviceCheckScreen.kt
│   ├── RailPositionPersistence.kt
│   └── ViewerTransformPersistence.kt
└── util/
    └── JsonText.kt
```

## 6. Open-path performance boundary

Normal note opening must use the viewer path:

```text
ImportedNoteCache.readCachedNote
→ SupernoteNoteInspector.inspectForViewer
→ viewer-critical page/layer/vector data
→ render current page
```

The viewer path must not run:

```text
- full marker sweeps
- full diagnostic JSON generation
- visual payload probe export
- binary numeric-run summaries
- expensive all-page comparison work
- device capability detection
- write-back research harnesses
```

Those tasks belong to explicit diagnostics/export flows.

## 7. Diagnostics boundary

Full diagnostics are allowed only when the user asks for them through the diagnostics screen or an export action.

Diagnostics may include:

```text
- header preview hex/ascii
- version and equipment markers
- page/layer offsets
- visual layer record reports
- RATTA_RLE decode attempts
- TOTALPATH structural reports
- stroke style counts
- stroke color counts
- eraser record counts
- unknown metadata samples
- compatibility warnings
```

Diagnostics are read-only. They must not modify `.note` bytes.

## 8. Import and cache model

VellumSync imports external notes by copying them into app-owned storage:

```text
external document URI
→ read bytes through ContentResolver
→ preliminary read-only inspection
→ cache under imported-notes/<sha256>/
→ open cached copy in viewer
```

The cache layer provides:

```text
- sha256-based stable identity
- source validation
- cached-copy existence checks
- recent-note library entries
- protection against path traversal or opening arbitrary files as imported notes
```

Original external notes remain unchanged.

## 9. Rendering strategy

Current renderer behavior:

```text
Preferred: visual layer when a plausible RATTA_RLE decoder is available
Fallback: TOTALPATH vector rendering when visual layer is unavailable or implausible
Overlay: local Android strokes drawn separately above the read-only base
```

The viewer should display a stable page even when a visual-layer decoder is not available. The status text may report that the fast vector renderer or fallback renderer is active, but this should not block interaction.

## 10. Supernote parser layers

```text
Level 1: Container
- version marker
- module/file type markers
- equipment marker
- page references
- page sections
- layer offsets

Level 2: Visual
- MAINLAYER/BGLAYER records
- RATTA_RLE metadata
- bitmap payload offsets
- shared background payloads
- plausible decoder selection

Level 3: Vector
- TOTALPATH declared payload size
- declared record count
- record chain boundaries
- point-count fields
- raw point arrays
- transform calibration

Level 4: Stroke semantics
- pen/tool metadata
- width metadata
- grayscale metadata
- eraser/path-removal metadata
- unknown style samples

Level 5: App-owned model
- canonical Vellum document
- page/layer/stroke abstractions
- overlay export model
- future sync/write model
```

## 11. Local overlay model

Local overlay strokes are app-owned sidecar data.

```text
read-only base note
+ app-owned overlay strokes
= displayed note surface
```

Overlay rules:

```text
- Stylus creates overlay strokes.
- Finger input should not accidentally create handwriting.
- Undo and clear operate on overlay data only.
- Export can include overlay data.
- Original .note bytes remain unchanged.
```

## 12. Write-back safety model

Write-back is not production scope yet. Any future write-back must follow this pipeline:

```text
original.note
→ working copy
→ generated temp output
→ parse temp output
→ render temp output
→ compare against expected visual output
→ test on real Supernote device
→ atomic replace/upload only after validation
→ preserve recovery copy
```

Unknown sections must be preserved. Unsupported versions must remain read-only.

## 13. Sync model

Sync is future work. The initial sync design should be conservative:

```text
Remote changed only: download and refresh cached read-only copy
Local overlay changed only: sync sidecar overlay package first
Both changed: create conflict copy rather than auto-merging
Unknown Supernote format: read-only open only
```

## 14. Testing strategy

Unit tests should cover:

```text
- import cache safety
- container parser behavior
- visual decoder/probe behavior
- RATTA_RLE render assumptions
- TOTALPATH structural parsing
- stroke geometry decoding
- stroke style/grayscale/eraser decoding
- transform calibration
```

Manual acceptance should use:

```text
- a real Supernote .note file
- a Supernote-exported PDF of the same note
- screenshots from BOOX Note Air 2 Plus
- exported VellumSync diagnostics JSON when investigating rendering gaps
```

## 15. Release architecture

Release artifacts are Android APKs. A release build should run:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```

GitHub release tags should use `v*` names, for example:

```text
v0.1.0
v1.0.0
```

Unsigned release APKs are suitable for internal testing. Signed release delivery requires a separate signing-key plan.

## License

VellumSync is licensed under the Apache License, Version 2.0. See `LICENSE` for the full license text and `NOTICE` for copyright and attribution notices.

Unless a file states otherwise, repository source code, documentation, scripts, and project configuration are covered by Apache-2.0.

Private user notes, exported PDFs, screenshots, diagnostics JSON, and local test corpora are not part of the default project license and should not be committed unless they are intentionally added as public test fixtures with explicit permission.

VellumSync is an independent compatibility-focused application. Supernote, Ratta, BOOX, Onyx, Android, and Google names are used only for compatibility, platform, and testing references.
