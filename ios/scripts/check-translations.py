#!/usr/bin/env python3
"""Fails when an iOS String Catalog lacks an Arabic translation.

For every *.xcstrings under the given directory (default: the ios/ directory holding this script),
every key not marked "shouldTranslate": false needs an "ar" localization in state "translated", and
a plural key needs the Arabic forms zero, one, two, few, many and other. Prints each problem and
exits non-zero if there is one.
"""
import json
import pathlib
import sys

ARABIC_PLURAL_FORMS = {"zero", "one", "two", "few", "many", "other"}


def plural_variations(localization):
    """Every plural variation in a localization: top level and inside substitutions."""
    found = []
    plural = localization.get("variations", {}).get("plural")
    if plural is not None:
        found.append(("", plural))
    for name, substitution in localization.get("substitutions", {}).items():
        plural = substitution.get("variations", {}).get("plural")
        if plural is not None:
            found.append((f" (substitution {name})", plural))
    return found


def translated(unit_holder):
    unit = unit_holder.get("stringUnit")
    return unit is not None and unit.get("state") == "translated" and unit.get("value", "") != ""


def problems_in(path):
    catalog = json.loads(path.read_text(encoding="utf-8"))
    problems = []
    for key, entry in sorted(catalog.get("strings", {}).items()):
        if entry.get("shouldTranslate") is False:
            continue
        arabic = entry.get("localizations", {}).get("ar")
        if arabic is None:
            problems.append(f"{path}: {key}: no Arabic translation")
            continue
        plurals = plural_variations(arabic)
        if not plurals and not translated(arabic):
            problems.append(f"{path}: {key}: Arabic translation is not in state 'translated'")
        for where, forms in plurals:
            missing = sorted(ARABIC_PLURAL_FORMS - set(forms))
            if missing:
                problems.append(f"{path}: {key}{where}: Arabic plural forms missing: {', '.join(missing)}")
            for form, holder in sorted(forms.items()):
                if not translated(holder):
                    problems.append(f"{path}: {key}{where}: Arabic plural form '{form}' is not translated")
    return problems


def main():
    root = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent
    catalogs = sorted(p for p in root.rglob("*.xcstrings") if ".build" not in p.parts)
    problems = [problem for path in catalogs for problem in problems_in(path)]
    for problem in problems:
        print(problem)
    if problems:
        print(f"{len(problems)} missing Arabic translation(s)")
        return 1
    print(f"All {len(catalogs)} String Catalogs have Arabic translations")
    return 0


if __name__ == "__main__":
    sys.exit(main())
