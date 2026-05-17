#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
rg -n "vellumsync-import-note-picker-mime-fallback-v0|application/octet-stream|application/x-note|\*\/\*"   "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/RecentNotesScreen.kt"
echo "Validation markers found for vellumsync_import_note_picker_mime_fallback_v0"
