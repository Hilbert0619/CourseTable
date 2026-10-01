"""Checks that every localizable UI literal in the app has a zh-Hans entry in Shared/Localizable.xcstrings.

    python tools/check_strings.py            # all app and widget Swift files
    python tools/check_strings.py PATH...    # only these files or folders

Literals are found in Text/Label/Button/Section/... calls, String(localized:), `case .x: "…"` (LocalizedStringKey
properties) and Swift Charts `.value("…")`. Interpolations become format specifiers (%lld, %@, …) and are matched
against the catalog keys. Exits with 1 if anything is missing.
"""
import glob
import io
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LITERAL = r'"((?:[^"\\]|\\.)*)"'
PATTERNS = [
    re.compile(r'(?:\bText|\bLabel|\bButton|\bSection|\bLabeledContent|\bToggle|\bPicker|\bTextField|\bSecureField|'
               r'\.navigationTitle|\.alert|\.confirmationDialog|\bContentUnavailableView|\bDatePicker)\(\s*' + LITERAL),
    re.compile(r'String\(localized:\s*' + LITERAL),
    re.compile(r'case \.\w+: ' + LITERAL),
    re.compile(r'\.value\(' + LITERAL),
]
SPECIFIER = r"%(?:\d\$)?(?:lld|@|lf|d)"


def swift_files(paths):
    for path in paths:
        if os.path.isdir(path):
            yield from glob.glob(os.path.join(path, "**", "*.swift"), recursive=True)
        else:
            yield path


def unescape(text):
    return text.replace('\\"', '"').replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")


def main():
    paths = sys.argv[1:] or [os.path.join(ROOT, "CourseTable"), os.path.join(ROOT, "CourseTableWidget"), os.path.join(ROOT, "Shared")]
    catalog = json.load(io.open(os.path.join(ROOT, "Shared", "Localizable.xcstrings"), encoding="utf-8"))["strings"]
    translated = {key for key, value in catalog.items() if "zh-Hans" in value.get("localizations", {})}

    def matches(literal):
        if "\\(" not in literal:
            return unescape(literal) in translated
        # `\(…)` with up to three levels of parentheses inside.
        parts = re.split(r"\\\((?:[^()]|\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\))*\)", literal)
        # With interpolations the key is a format string, so a literal % in it is escaped as %%.
        pattern = SPECIFIER.join(re.escape(unescape(part).replace("%", "%%")) for part in parts)
        return any(re.fullmatch(pattern, key) for key in translated)

    missing = set()
    for path in swift_files(paths):
        source = io.open(path, encoding="utf-8").read()
        for index, pattern in enumerate(PATTERNS):
            for match in pattern.finditer(source):
                literal = match.group(1)
                # `case .x: "…"` is also used for raw values and identifiers: only check text with capitals.
                if index == 2 and not re.search(r"[A-Z]", literal):
                    continue
                if not matches(literal):
                    missing.add((os.path.relpath(path, ROOT), literal))
    for path, literal in sorted(missing):
        print(f"MISSING {path}: {literal!r}")
    print(f"{len(missing)} missing")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
