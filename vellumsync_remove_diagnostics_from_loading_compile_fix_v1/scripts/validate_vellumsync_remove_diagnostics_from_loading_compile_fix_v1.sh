#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
rg -n "fun NoteInspectorScreen\(|Select \.note file|Open in viewer|Export diagnostics JSON|Export TOTALPATH structural JSON"   "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteInspectorScreen.kt"
if rg -n "validateImportSource\(" "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteInspectorScreen.kt"; then
  echo "validateImportSource is still present in NoteInspectorScreen.kt; compile fix incomplete" >&2
  exit 1
fi
echo "Validation markers found for vellumsync_remove_diagnostics_from_loading_compile_fix_v1"
