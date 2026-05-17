Apply from repo root:

```bash
unzip -o vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5.zip
chmod +x vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5/scripts/*.sh
./vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5/scripts/apply_vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5.sh .
./vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5/scripts/validate_vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5.sh .
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

This is a one-line compile-only fix.
It changes the visual payload probe export call in `NoteInspectorScreen.kt` from `bytes = noteBytes` back to the in-scope local `bytes`.
