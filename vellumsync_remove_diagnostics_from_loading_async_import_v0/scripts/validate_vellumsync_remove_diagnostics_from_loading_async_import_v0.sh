#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
rg -n "vellumsync-import-note-async-pipeline-import-logging-v0|fun sha256\(|fun cacheReadOnlyCopy\(\s*context: Context,\s*fileName: String,\s*bytes: ByteArray\)|metadataFile.delete\("   "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/note/ImportedNoteCache.kt"
rg -n "Importing note…|rememberCoroutineScope|Dispatchers\.IO|import openInputStream start|import cache write start|import navigation to viewer start|cacheReadOnlyCopy\("   "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/RecentNotesScreen.kt"
rg -n "cacheReadOnlyCopy\(\s*context = context,\s*fileName = doc.displayName,\s*bytes = bytes\)|pageCount = null"   "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/LocalNoteLibrary.kt"
rg -n "viewer open start path=|viewer cached note read bytes=|viewer open complete file=|Opening note…"   "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteViewerScreen.kt"
echo "Validation markers found for vellumsync_remove_diagnostics_from_loading_async_import_v0"
