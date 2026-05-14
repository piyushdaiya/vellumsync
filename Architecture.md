# VellumSync Architecture

## 1. Product definition

VellumSync is a local-first Android e-ink stylus app for opening, inspecting, rendering, and eventually syncing Supernote-compatible `.note` files on Android tablets.

Initial Android target:

```text
BOOX Note Air 2 Plus and similar stylus-capable BOOX e-ink devices
```

Initial Supernote compatibility profile:

```text
Device: Supernote A5X
Software: Chauvet 2.24.37
Observed note family: SN_FILE_VER_20230015
```

## 2. Design principles

```text
Local-first
Read-only before write-back
Never overwrite a Supernote file directly
Preserve unknown data
Separate Android-native notes from Supernote-compatible notes
Treat .note compatibility as versioned and device-specific
Use real Supernote files as validation corpus
```

## 3. High-level architecture

```text
VellumSync Android App
│
├── App Shell
│   ├── Home
│   ├── Device Check
│   ├── Import
│   ├── Note Inspector
│   └── Settings
│
├── Device Layer
│   ├── Android device profile
│   ├── Stylus capability detection
│   ├── Runtime pen probe
│   └── Future e-ink adapter
│
├── Supernote Compatibility Layer
│   ├── .note file detector
│   ├── container/header parser
│   ├── page/layer marker scanner
│   ├── metadata marker scanner
│   ├── future visual decoder
│   ├── future stroke decoder
│   └── future write-back validator
│
├── Storage Layer
│   ├── imported file references
│   ├── local metadata
│   ├── local cache
│   ├── sidecar overlays
│   └── future sync state
│
├── Rendering Layer
│   ├── future page bitmap renderer
│   ├── future stroke renderer
│   ├── future template renderer
│   └── future PDF comparison renderer
│
└── Sync Layer
    ├── future local folder sync
    ├── future cloud-folder sync
    ├── future Supernote-compatible sync flow
    └── future conflict resolver
```

## 4. Initial module strategy

Start as a single Android app module.

Reason:

```text
- Faster Android Studio setup
- Easier Compose iteration
- Easier device deployment
- Easier debugging of file picker and MotionEvent stylus input
```

Later split into modules:

```text
:app
:core-model
:core-device
:core-note-format
:core-storage
:core-rendering
:feature-device-check
:feature-import
:feature-note-inspector
:feature-editor
:feature-sync
```

## 5. Current package layout

```text
app/src/main/java/io/github/piyushdaiya/vellumsync/
├── MainActivity.kt
├── device/
│   ├── DeviceCapabilityDetector.kt
│   ├── DeviceProfile.kt
│   └── StylusProbeView.kt
├── note/
│   ├── SupernoteInspectionReport.kt
│   └── SupernoteNoteInspector.kt
└── ui/
    ├── VellumSyncApp.kt
    ├── DeviceCheckScreen.kt
    └── NoteInspectorScreen.kt
```

## 6. Device capability detection

VellumSync should not assume every Android device supports pen input.

Detection strategy:

```text
1. Scan Android InputDevice sources for stylus-like input.
2. Check known device manufacturer/model.
3. Allow runtime stylus probe using MotionEvent.TOOL_TYPE_STYLUS.
4. Allow manual override later in settings.
```

Device status values:

```text
Detected
Not detected
Unknown
Probe required
```

Example messages:

```text
Stylus support detected.
VellumSync is ready for handwritten .note workflows on this device.

No active stylus support was detected.
You can browse and inspect compatible .note files, but handwriting/editing features require a stylus-capable Android e-ink device.

Stylus support could not be confirmed.
Open the test canvas and touch the screen with your pen to complete device detection.
```

## 7. Supernote file detection

The first parser is not a full decoder. It is a safe binary marker inspector.

Initial markers:

```text
SN_FILE_VER_
SN_FILE_VER_20230015
NOTE
A5X
A6X
A5X2
A6X2
PAGE
PAGESTYLE
MAINLAYER
BGLAYER
LAYERINFO
LAYERSEQ
TOTALPATH
TITLE
KEYWORD
LINK
STAR
```

Initial report:

```text
fileName
fileSizeBytes
versionMarker
detectedEquipment
estimatedPageCount
hasMainLayer
hasBackgroundLayer
hasLayerInfo
hasLayerSequence
hasTotalPath
hasPageStyle
hasTitleMetadata
hasKeywordMetadata
hasLinkMetadata
hasStarMetadata
compatibilityStatus
warnings
```

## 8. Supernote compatibility modes

VellumSync should support multiple modes over time.

```text
Viewer Mode
- Read-only open and inspect
- Safe first milestone

Overlay Mode
- Original .note remains untouched
- Android annotations stored as sidecar data

Native Sync Mode
- Controlled .note write-back
- Only enabled after file version validation
```

## 9. Future .note parser layers

```text
Parse Level 1: Container
- header
- version marker
- footer/table references
- page offsets

Parse Level 2: Visual
- layer bitmap
- background template
- rendered page cache

Parse Level 3: Stroke
- TOTALPATH
- pen type
- width
- grayscale
- raw points
- pressure
- angle/tilt
- bounds

Parse Level 4: Metadata
- headings
- keywords
- links
- stars
- page navigation

Parse Level 5: Writer
- create compatible .note
- append compatible strokes
- preserve unknown sections
- rebuild offsets
- validate with real device
```

## 10. Sync design

Initial app does not sync.

Future sync flow:

```text
Remote source
→ download .note
→ preserve original file
→ parse and inspect
→ render/read locally
→ user edits compatible data
→ write temp .note
→ re-parse temp .note
→ validate visual output
→ upload atomically
→ preserve recovery copy
```

Conflict policy:

```text
Remote changed only:
- download and refresh

Local changed only:
- write and upload after validation

Both changed different pages:
- attempt page-level merge later

Both changed same page:
- do not auto-merge initially
- create duplicate/conflict copy

Unknown version:
- open read-only
```

## 11. Write-back safety rule

Never directly overwrite a Supernote file.

Required write-back pipeline:

```text
original.note
→ working copy
→ temp output
→ parse temp output
→ render temp output
→ compare expected output
→ atomic replace/upload
→ keep recovery copy
```

## 12. Current deliverable

VellumSync Foundation:

```text
- App launches
- Device check works
- Stylus probe works
- .note picker works
- Binary marker inspection works
- Read-only compatibility report works
```

## 13. Roadmap

```text
Milestone 1: Foundation
- app shell
- stylus detection
- .note marker inspector

Milestone 2: Read-only Supernote parser
- container parser
- page/layer parser
- metadata parser

Milestone 3: Visual rendering
- decode page bitmaps
- compare against Supernote-exported PDFs

Milestone 4: Stroke decoding
- decode TOTALPATH
- render vector strokes
- map pen width/color/pressure

Milestone 5: Overlay mode
- Android sidecar annotations
- original .note untouched

Milestone 6: Minimal writer
- create simple compatible .note
- black pen, one page, one layer

Milestone 7: Round-trip sync
- edit compatible notes
- validate on Supernote
- conflict handling
```