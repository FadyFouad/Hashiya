#!/usr/bin/env bash
# Zips testdata/backup/format-1/ into testdata/backup/format-1.hashiya, the backup both platforms' tests decode.
set -euo pipefail
cd "$(dirname "$0")/../testdata/backup/format-1"
rm -f ../format-1.hashiya
zip -X -q -r ../format-1.hashiya manifest.json library.json pdfs
echo "Wrote testdata/backup/format-1.hashiya"
