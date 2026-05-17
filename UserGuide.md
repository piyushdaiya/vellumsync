# VellumSync User Guide

## 1. What VellumSync does today

VellumSync opens Supernote `.note` files on Android e-ink tablets and displays them in a read-only viewer. It also supports local Android overlay strokes so you can test annotation behavior without changing the original Supernote file.

The app is currently optimized for BOOX Note Air 2 Plus testing.

## 2. Import a Supernote note

```text
1. Open VellumSync.
2. Stay on the Recent Notes screen.
3. Use the import/open action to select a .note file.
4. Android document picker opens.
5. Choose the Supernote .note file.
6. VellumSync copies it into app-owned read-only cache.
7. Open the cached note in the viewer.
```

The original selected file is not modified.

## 3. Open a cached note

```text
1. Open VellumSync.
2. Select a note from Recent Notes.
3. The viewer opens using the fast viewer path.
4. Page content appears without running full diagnostics.
```

Normal note opening should be fast. Diagnostics are separate and explicit.

## 4. Use the viewer

The viewer is designed around a BOOX-style e-ink layout.

Main controls:

```text
View      view options and render status
Pen       local overlay pen options
Erase     local overlay eraser options
Style     overlay style options
Layer     base/overlay visibility controls
Page      page navigation
Zoom      transform and display controls
Export    diagnostics/overlay/package exports
Sync      placeholder for future sync
Settings  rail position and viewer settings
More      advanced/debug options
```

## 5. Change tool rail side

Use the settings/tool panel to place the rail on the left or right. This supports both right-handed and left-handed stylus workflows.

## 6. Draw local overlay strokes

```text
1. Open a note in the viewer.
2. Select Pen.
3. Use the stylus on the note surface.
4. Overlay strokes appear above the read-only Supernote base page.
```

Overlay strokes are local app data. They do not change the original `.note` file.

## 7. Erase local overlay strokes

```text
1. Select Erase.
2. Use the stylus on overlay strokes.
3. Undo or clear overlay data if needed.
```

Eraser behavior in the original Supernote note is rendered as part of the base note renderer. Local eraser actions affect only the Android overlay.

## 8. Export diagnostics

Use diagnostics only when investigating parser or rendering behavior.

```text
1. Open the diagnostics screen.
2. Select a .note file.
3. Wait for full diagnostics to complete.
4. Export diagnostics JSON, visual payload probe JSON, or TOTALPATH structural JSON.
```

Diagnostics may be slower than normal viewing because they run deeper parser/probe logic.

## 9. Compare against Supernote PDF export

For rendering validation:

```text
1. Export the same note to PDF from Supernote.
2. Open the .note in VellumSync.
3. Compare page content, grayscale, pen width, and eraser behavior.
4. Export diagnostics JSON only if the display differs from the PDF.
```

## 10. Device check

Device Check is available for troubleshooting stylus support.

It reports:

```text
- Android device/manufacturer/model
- detected input devices
- whether stylus-like input is visible
- runtime stylus probe result
```

The app no longer performs this check during normal launch.

## 11. Current limitations

```text
- Supernote .note write-back is not production-ready.
- Original .note files remain read-only.
- Cloud sync is not implemented.
- Private Supernote/BOOX APIs are not used.
- Some rare Supernote visual-layer payloads may use vector fallback.
- Diagnostics are intended for development and troubleshooting, not normal use.
```

## License

VellumSync is licensed under the Apache License, Version 2.0. See `LICENSE` for the full license text and `NOTICE` for copyright and attribution notices.

Unless a file states otherwise, repository source code, documentation, scripts, and project configuration are covered by Apache-2.0.

Private user notes, exported PDFs, screenshots, diagnostics JSON, and local test corpora are not part of the default project license and should not be committed unless they are intentionally added as public test fixtures with explicit permission.

VellumSync is an independent compatibility-focused application. Supernote, Ratta, BOOX, Onyx, Android, and Google names are used only for compatibility, platform, and testing references.
