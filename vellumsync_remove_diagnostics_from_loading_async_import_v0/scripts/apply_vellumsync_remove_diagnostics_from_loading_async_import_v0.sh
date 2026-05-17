#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
FILES=(
  "app/src/main/java/io/github/piyushdaiya/vellumsync/note/ImportedNoteCache.kt"
  "app/src/main/java/io/github/piyushdaiya/vellumsync/ui/RecentNotesScreen.kt"
  "app/src/main/java/io/github/piyushdaiya/vellumsync/ui/LocalNoteLibrary.kt"
  "app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteViewerScreen.kt"
)
for rel in "${FILES[@]}"; do
  mkdir -p "$ROOT/$(dirname "$rel")"
  cp "$(dirname "$0")/../$rel" "$ROOT/$rel"
done
echo "Applied vellumsync_remove_diagnostics_from_loading_async_import_v0"
