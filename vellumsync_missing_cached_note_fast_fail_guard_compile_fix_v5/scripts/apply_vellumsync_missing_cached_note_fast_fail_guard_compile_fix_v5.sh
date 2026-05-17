#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
python3 "$(dirname "$0")/patch_vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5.py" "$ROOT"
echo "Applied vellumsync_missing_cached_note_fast_fail_guard_compile_fix_v5"
