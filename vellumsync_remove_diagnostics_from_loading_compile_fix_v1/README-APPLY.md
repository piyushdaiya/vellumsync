Apply from repo root:

```bash
unzip -o vellumsync_remove_diagnostics_from_loading_compile_fix_v1.zip
chmod +x vellumsync_remove_diagnostics_from_loading_compile_fix_v1/scripts/*.sh
./vellumsync_remove_diagnostics_from_loading_compile_fix_v1/scripts/apply_vellumsync_remove_diagnostics_from_loading_compile_fix_v1.sh .
./vellumsync_remove_diagnostics_from_loading_compile_fix_v1/scripts/validate_vellumsync_remove_diagnostics_from_loading_compile_fix_v1.sh .
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

This is a one-file compile fix for the async-import/no-diagnostics-loading patch.
It restores `NoteInspectorScreen.kt` from the latest uploaded `vellumsync(3).zip`, removing the stale `ImportedNoteCache.validateImportSource(...)` call that no longer exists in the current `ImportedNoteCache.kt`.
