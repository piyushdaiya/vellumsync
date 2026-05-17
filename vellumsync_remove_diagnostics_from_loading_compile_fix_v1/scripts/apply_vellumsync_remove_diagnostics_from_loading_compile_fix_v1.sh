#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
mkdir -p "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui"
cp "$(dirname "$0")/../app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteInspectorScreen.kt"    "$ROOT/app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteInspectorScreen.kt"
echo "Applied vellumsync_remove_diagnostics_from_loading_compile_fix_v1"
