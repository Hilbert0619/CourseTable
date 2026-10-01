"""Adds zh-Hans translations to Shared/Localizable.xcstrings, writing exactly the format Xcode writes.

    python tools/add_strings.py translations.json

translations.json maps the English key (as Xcode extracts it: Int interpolation → %lld, String/Date → %@) to
either the Chinese text, or {"zh": "...", "one": "..."} where "one" is the English singular (an English plural
variation is added). Existing keys are left alone. Refuses to touch the catalog if re-encoding it would change
anything, so Xcode's own formatting is never disturbed.
"""
import io
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, "Shared", "Localizable.xcstrings")


def unit(value):
    return {"stringUnit": {"state": "translated", "value": value}}


def dump(data):
    return json.dumps(data, ensure_ascii=False, indent=2, separators=(",", " : "), sort_keys=True) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    translations = json.load(io.open(sys.argv[1], encoding="utf-8"))
    raw = io.open(CATALOG, encoding="utf-8", newline="").read()
    data = json.loads(raw)
    if dump(data) != raw:
        sys.exit("The catalog isn't in the expected format; not touching it.")
    added = []
    for key, value in translations.items():
        if key in data["strings"]:
            continue
        zh, one = (value, None) if isinstance(value, str) else (value["zh"], value.get("one"))
        localizations = {"zh-Hans": unit(zh)}
        if one:
            localizations["en"] = {"variations": {"plural": {"one": unit(one), "other": unit(key)}}}
        data["strings"][key] = {"localizations": localizations}
        added.append(key)
    io.open(CATALOG, "w", encoding="utf-8", newline="").write(dump(data))
    print(f"added {len(added)}, total {len(data['strings'])}")


if __name__ == "__main__":
    main()
