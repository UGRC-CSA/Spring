#!/usr/bin/env bash
# Rebuild assets/css/ocs.css from this directory's Sass source.
# Pass --check to verify the committed CSS still matches (for CI).
set -euo pipefail
cd "$(dirname "$0")"
OUT="../ocs.css"

# `sass` on PATH is not necessarily Dart Sass. An rbenv shim for the old Ruby
# sass gem shadows it on some machines, and Ruby Sass cannot compile @use, so
# check what we actually got instead of trusting the name.
find_sass() {
  local c
  for c in "${SASS:-}" sass dart-sass "$HOME/.local/bin/sass" /opt/homebrew/bin/sass; do
    [[ -n "$c" ]] && command -v "$c" >/dev/null 2>&1 || continue
    if "$c" --version 2>/dev/null | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then echo "$c"; return 0; fi
  done
  return 1
}
SASS_BIN=$(find_sass) || {
  echo "Dart Sass not found (an old Ruby 'sass' gem on PATH does not count)." >&2
  echo "Install it with: brew install sass/sass/sass" >&2
  echo "Or point at an existing binary: SASS=/path/to/sass $0" >&2
  exit 1; }

echo "==> Verifying contrast guarantees"
python3 tools/contrast.py

if [[ "${1:-}" == "--check" ]]; then
  TMP=$(mktemp); trap 'rm -f "$TMP"' EXIT
  "$SASS_BIN" --no-source-map --style=expanded --load-path=. _index.scss "$TMP"
  if diff -q "$TMP" "$OUT" >/dev/null; then
    echo "==> OK: committed CSS matches the source."
  else
    echo "==> DRIFT: $OUT does not match the source. Run this script without --check." >&2
    exit 1
  fi
else
  "$SASS_BIN" --no-source-map --style=expanded --load-path=. _index.scss "$OUT"
  echo "==> Wrote $OUT ($(wc -c < "$OUT" | tr -d ' ') bytes)"
fi
