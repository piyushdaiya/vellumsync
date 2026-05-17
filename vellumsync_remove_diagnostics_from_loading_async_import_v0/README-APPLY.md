Apply from repo root:

```bash
unzip -o vellumsync_remove_diagnostics_from_loading_async_import_v0.zip
chmod +x vellumsync_remove_diagnostics_from_loading_async_import_v0/scripts/*.sh
./vellumsync_remove_diagnostics_from_loading_async_import_v0/scripts/apply_vellumsync_remove_diagnostics_from_loading_async_import_v0.sh .
./vellumsync_remove_diagnostics_from_loading_async_import_v0/scripts/validate_vellumsync_remove_diagnostics_from_loading_async_import_v0.sh .
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

What this patch does:
- removes diagnostics generation from import-time loading and linked-folder scan-time loading
- keeps diagnostics on the explicit diagnostics/settings surfaces only
- moves Import Note byte reading and cache write work off the UI thread
- opens notes asynchronously in the viewer instead of blocking composition
- shows a small non-blocking “Opening note…” / “Importing…” state
- keeps `VellumSyncOpen` logs for:
  - import button tapped
  - picker result received
  - openInputStream start/end
  - bytes read size + elapsed ms
  - cache write start/end
  - navigation to viewer start/end
  - viewer open start/end

Notes:
- Import/scan now cache only the original `.note` and do not write `diagnostics.json` during loading.
- Page count is left `null` until the note is actually opened/parsed.
- Diagnostics export remains available from the diagnostics/settings surfaces after the note is loaded.
