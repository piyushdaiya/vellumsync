#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
mkdir -p "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui"
cp "$(dirname "$0")/../app/src/main/java/io/github/piyushdaiya/vellumsync/ui/RecentNotesScreen.kt"    "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/RecentNotesScreen.kt"
echo "Applied vellumsync_import_note_picker_mime_fallback_v0"
