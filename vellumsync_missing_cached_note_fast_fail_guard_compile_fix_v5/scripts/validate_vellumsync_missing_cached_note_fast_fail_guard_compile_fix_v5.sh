#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
rg -n "vellumsync-missing-cached-note-fast-fail-guard-compile-fix-v5|SupernoteVisualPayloadProbe\.probe\(|bytes = bytes,|report = inspection"   "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteInspectorScreen.kt"
if rg -n "pendingExportJson\.value = SupernoteVisualPayloadProbe\.probe\([[:space:]]*$|bytes = noteBytes,"   "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteInspectorScreen.kt"; then
  echo "noteBytes is still used inside visual payload probe block; compile fix incomplete" >&2
  exit 1
fi
echo "Validation markers found for vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5"
