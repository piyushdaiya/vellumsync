# VellumSync Roadmap

## Accepted foundation

```text
- Android app shell.
- BOOX/stylus device check.
- Supernote .note file picker/import.
- Read-only cached note copies.
- Recent notes entry point.
- Note viewer with tool rail.
- Local overlay drawing scaffold.
```

## Accepted rendering and performance state

```text
- Visual-layer-first rendering where plausible.
- Vector/TOTALPATH fallback when visual layer is unavailable.
- Pen style and width decoding started.
- Grayscale mapping started.
- Eraser knockout behavior started for vector rendering.
- Normal note opening no longer runs full diagnostics.
- App launch no longer runs device diagnostics by default.
```

## Near-term next steps

```text
1. Keep documentation and repo hygiene clean.
2. Stabilize tests and CI on the latest fast-open viewer path.
3. Improve renderer parity using controlled Supernote test notes plus PDF exports.
4. Refine page 3 and page 4 fallback behavior where visual layer is unavailable.
5. Keep diagnostics export available but outside normal viewer open.
6. Improve local overlay UX on BOOX stylus hardware.
```

## Rendering fidelity track

```text
- Build a small controlled corpus:
  - pen type page
  - grayscale page
  - width page
  - eraser page
- Compare each .note against Supernote PDF export.
- Add regression tests for each decoded metadata pattern.
- Keep unknown stroke metadata in diagnostics.
- Avoid tuning only for one handwritten note.
```

## Viewer UX track

```text
- Keep viewer open fast.
- Preserve page position per note.
- Improve transform defaults per A5X profile.
- Keep rail side configurable.
- Make overlay controls clear for stylus users.
- Avoid expensive work on the UI thread.
```

## Overlay track

```text
- Improve overlay persistence and restore behavior.
- Add better pen/eraser style controls.
- Add overlay export formats.
- Add conflict-safe sidecar packaging.
- Keep original .note files unchanged.
```

## Sync/write-back track

```text
- Continue writer work as research only.
- Define canonical Vellum package format.
- Validate generated notes with parser and renderer.
- Test generated notes on real Supernote hardware.
- Add write-back only after round-trip validation is reliable.
```

## Release track

```text
- Keep Android CI running unit tests and debug builds.
- Add or maintain release workflow for tagged APK artifacts.
- Use v* tags for GitHub releases.
- Decide APK signing strategy before public releases.
```

## License

VellumSync is licensed under the Apache License, Version 2.0. See `LICENSE` for the full license text and `NOTICE` for copyright and attribution notices.

Unless a file states otherwise, repository source code, documentation, scripts, and project configuration are covered by Apache-2.0.

Private user notes, exported PDFs, screenshots, diagnostics JSON, and local test corpora are not part of the default project license and should not be committed unless they are intentionally added as public test fixtures with explicit permission.

VellumSync is an independent compatibility-focused application. Supernote, Ratta, BOOX, Onyx, Android, and Google names are used only for compatibility, platform, and testing references.
