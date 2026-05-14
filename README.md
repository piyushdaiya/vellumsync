# VellumSync

VellumSync is an Android e-ink stylus app for opening, inspecting, and eventually syncing compatible `.note` files across stylus-capable Android tablets.

Initial target devices:

```text
Primary Android target:
- BOOX Note Air 2 Plus

Similar expected targets:
- BOOX stylus-capable Android e-ink tablets
- Other Android e-ink tablets with pen/stylus support

Reference Supernote target:
- Supernote A5X
- Chauvet 2.24.37
- `.note` header family observed in test corpus: SN_FILE_VER_20230015
```

VellumSync is independently developed and is not affiliated with, endorsed by, or sponsored by Ratta Supernote, BOOX, or Onyx.

## Project goals

VellumSync will be built in stages:

1. Detect whether the Android device supports stylus/pen input.
2. Import and inspect Supernote `.note` files safely.
3. Render Supernote `.note` files in read-only mode.
4. Decode stroke-level `.note` data.
5. Support sidecar overlays for Android annotations.
6. Support controlled Supernote-compatible write-back.
7. Support two-way sync with Supernote-compatible workflows.

The first version is intentionally read-only for `.note` files. It should never overwrite or modify a Supernote note file until the writer and validator are proven safe.

## Current foundation scope

The current app foundation includes:

```text
- Android app shell
- Stylus/device capability check
- Manual stylus probe
- Supernote .note file picker
- Read-only binary marker inspection
- Initial compatibility report
- Local-only workflow
```

Current non-scope:

```text
- Native .note write-back
- Cloud sync login
- Supernote Cloud API
- BOOX private e-ink APIs
- Full note rendering
- Stroke editing
- Recognition notebook parsing
- PDF annotation
```

## Repository

```text
GitHub:
https://github.com/piyushdaiya/vellumsync

Android application ID:
io.github.piyushdaiya.vellumsync
```

## Local corpus

Do not commit private `.note` files to the public repository.

Use a local ignored directory:

```text
local-corpus/
├── SN_ONE_STROKE_BLACK_PEN.note
├── SN_ONE_STROKE_BLACK_PEN.pdf
├── SYNTHETIC_TEST_NOTE.note
└── SYNTHETIC_TEST_NOTE.pdf
```

Add this to `.gitignore`:

```gitignore
local-corpus/
```

## Build

```bash
./gradlew test
./gradlew assembleDebug
```

## Run on Android device

Enable developer mode and USB debugging on the Android tablet, then run:

```bash
./gradlew installDebug
```

Or deploy through Android Studio.

## First acceptance checks

The foundation is working when:

```text
- App builds successfully.
- App launches on emulator.
- App launches on BOOX device.
- Device check screen shows stylus capability or asks for a pen probe.
- User can select a .note file.
- App detects SN_FILE_VER_20230015 when present.
- App detects NOTE marker when present.
- App detects A5X marker when present.
- App detects PAGE markers.
- App detects MAINLAYER, BGLAYER, TOTALPATH, PAGESTYLE, and LAYERINFO markers when present.
- App reports read-only compatibility.
```

## Compatibility disclaimer

Supernote `.note` is not a public stable interchange format. VellumSync should treat every parsed file as versioned and potentially device-specific.

The first supported profile is based on local test files created on:

```text
Device: Supernote A5X
Software: Chauvet 2.24.37
```

Any write-back support must be gated by validation:

```text
write temporary file
→ re-parse file
→ render file
→ compare visual output
→ test on real Supernote device
→ sync back
→ verify again
```

## References

Useful public research references:

```text
https://github.com/jya-dev/supernote-tool
https://github.com/philips/supernote-typescript
https://walnut356.github.io/posts/inspecting-the-supernote-note-format/
```

These are references only. VellumSync should maintain its own parser/validator and avoid copying incompatible or proprietary code.

## License

Choose a license before publishing reusable code.

Recommended options:

```text
Apache-2.0
MIT
```

If code is copied from GPL projects, the project license may need to be GPL-compatible. Prefer clean-room implementation using public behavior and test files as references.