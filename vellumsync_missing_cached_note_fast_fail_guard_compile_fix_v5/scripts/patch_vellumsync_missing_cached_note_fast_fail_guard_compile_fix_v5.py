from pathlib import Path
import re
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
target = root / "app/src/main/java/io/github/piyushdaiya/vellumsync/ui/NoteInspectorScreen.kt"
if not target.exists():
    raise SystemExit(f"Required file not found: {target}")

text = target.read_text()

marker = "// marker=vellumsync-missing-cached-note-fast-fail-guard-compile-fix-v5"
if marker not in text:
    text = marker + "\n" + text

pattern = re.compile(
    r'(pendingExportJson\.value\s*=\s*SupernoteVisualPayloadProbe\.probe\(\s*\n\s*bytes\s*=\s*)noteBytes(\s*,\s*\n\s*report\s*=\s*inspection\s*\n\s*\)\.toJson\(\))',
    re.MULTILINE
)
new_text, count = pattern.subn(r'\1bytes\2', text, count=1)
if count != 1:
    raise SystemExit("Expected visual payload probe block using noteBytes was not found.")
target.write_text(new_text)
print("Patched NoteInspectorScreen.kt")
