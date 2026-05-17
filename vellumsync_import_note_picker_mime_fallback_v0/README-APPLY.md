Apply from repo root:

```bash
unzip -o vellumsync_import_note_picker_mime_fallback_v0.zip
chmod +x vellumsync_import_note_picker_mime_fallback_v0/scripts/*.sh
./vellumsync_import_note_picker_mime_fallback_v0/scripts/apply_vellumsync_import_note_picker_mime_fallback_v0.sh .
./vellumsync_import_note_picker_mime_fallback_v0/scripts/validate_vellumsync_import_note_picker_mime_fallback_v0.sh .
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

What this patch does:
- adds `*/*` to the main **Import .note** picker MIME filter in `RecentNotesScreen.kt`
- keeps existing import validation, so non-`.note` files are still rejected after selection
- fixes Android document providers that do not expose `.note` under `application/octet-stream` or `application/x-note`
