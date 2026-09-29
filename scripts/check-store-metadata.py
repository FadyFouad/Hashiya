#!/usr/bin/env python3
"""Checks every field in docs/store/metadata.md against its character limit.

A field is a bold label with its limit in brackets, followed by a fenced block:  **Name** [30]
Exits with 1 when any field is too long.
"""
import re
import sys
from pathlib import Path

METADATA = Path(__file__).resolve().parent.parent / "docs/store/metadata.md"
FIELD = re.compile(r"\*\*(?P<label>[^*]+)\*\* \[(?P<limit>\d+)\]\n```\n(?P<text>.*?)\n```", re.S)
SECTION = re.compile(r"<!-- lang:(?P<lang>\w+) store:(?P<store>[\w-]+) -->")


def main() -> int:
    text = METADATA.read_text(encoding="utf-8")
    sections = [(m.start(), f"{m['store']}/{m['lang']}") for m in SECTION.finditer(text)]
    too_long = 0
    for field in FIELD.finditer(text):
        section = next((name for start, name in reversed(sections) if start < field.start()), "?")
        length, limit = len(field["text"]), int(field["limit"])
        ok = length <= limit
        too_long += not ok
        print(f"{'ok ' if ok else 'TOO LONG'} {section:18} {field['label']:22} {length:>5} / {limit}")
    return 1 if too_long else 0


if __name__ == "__main__":
    sys.exit(main())
