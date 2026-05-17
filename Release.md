# VellumSync Release Notes and Process

## Local release checks

Run these from the repository root before creating a release:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```

Manual BOOX smoke test:

```text
- Install debug APK.
- Launch app.
- Confirm Recent Notes is the first screen.
- Import a .note file.
- Open the note in viewer.
- Confirm note opens through the fast path.
- Confirm pages visually match the Supernote PDF export for the test corpus.
- Draw a local overlay stroke with the stylus.
- Confirm original .note file remains unchanged.
- Export diagnostics JSON only from the explicit diagnostics/export action.
```

## GitHub release tags

Use semantic `v*` tags:

```text
v0.1.0
v0.2.0
v1.0.0
```

Example:

```bash
git tag -a v0.1.0 -m "VellumSync v0.1.0"
git push origin v0.1.0
```

## APK signing

The current local release build may produce unsigned or locally signed artifacts depending on Android Gradle configuration. Before public distribution, define:

```text
- release keystore location
- GitHub secret names
- signing config
- artifact naming
- upgrade/versionCode policy
```

## Release artifact expectations

A release should include:

```text
- debug APK for internal testing, if desired
- release APK for installation testing
- changelog summary
- known limitations
- supported test device list
```

## Current known limitations

```text
- Read-only Supernote base note handling.
- Local overlay annotations are app-owned sidecar data.
- Supernote-compatible write-back is research-only.
- Cloud sync is not implemented.
- Some visual layer payloads may fall back to vector rendering.
```

## License

VellumSync is licensed under the Apache License, Version 2.0. See `LICENSE` for the full license text and `NOTICE` for copyright and attribution notices.

Unless a file states otherwise, repository source code, documentation, scripts, and project configuration are covered by Apache-2.0.

Private user notes, exported PDFs, screenshots, diagnostics JSON, and local test corpora are not part of the default project license and should not be committed unless they are intentionally added as public test fixtures with explicit permission.

VellumSync is an independent compatibility-focused application. Supernote, Ratta, BOOX, Onyx, Android, and Google names are used only for compatibility, platform, and testing references.
