"""Saves the screenshots kept in a UI-test results bundle downloaded from CI, on Windows (no Xcode needed).

    gh run download <run id> -n UITestResults -D results     # screenshots.yml uploads it when tests fail
    python tools/xcresult_screenshots.py results shots       # shots/<attachment name>.png

Attachment names come from the bundle's database.sqlite3; a name taken twice (a retried test) gets -2, -3….
Compressed data files are decompressed with the helpers in xcresult_crash.py.
"""
import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from xcresult_crash import _zstd, decompress  # noqa: E402

EXTENSIONS = {"public.png": ".png", "public.jpeg": ".jpg", "public.heic": ".heic"}
ZSTD_MAGIC = b"\x28\xb5\x2f\xfd"


def main(bundle, out):
    os.makedirs(out, exist_ok=True)
    lib = _zstd()
    db = sqlite3.connect(os.path.join(bundle, "database.sqlite3"))
    rows = db.execute(
        "select name, xcResultKitPayloadRefId, uniformTypeIdentifier from Attachments order by timestamp"
    ).fetchall()
    taken = {}
    saved = 0
    for name, ref, kind in rows:
        extension = EXTENSIONS.get(kind)
        if not extension or not ref:
            continue
        path = os.path.join(bundle, "Data", "data." + ref)
        if not os.path.exists(path):
            print("missing data for", name)
            continue
        with open(path, "rb") as f:
            data = f.read()
        # Images are often stored as they are; everything else is zstd-compressed.
        if data[:4] == ZSTD_MAGIC:
            data = decompress(lib, data)
        safe = "".join(c if c.isalnum() or c in "-_." else "_" for c in name)
        taken[safe] = taken.get(safe, 0) + 1
        if taken[safe] > 1:
            safe += "-%d" % taken[safe]
        with open(os.path.join(out, safe + extension), "wb") as f:
            f.write(data)
        saved += 1
    print("saved %d screenshots to %s" % (saved, out))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
