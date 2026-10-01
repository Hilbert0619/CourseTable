"""Reads crash reports out of a test-results bundle downloaded from CI, on Windows (no Xcode needed).

    gh run download <run id> -n TestResults -D results      # ios.yml uploads it when tests fail
    python tools/xcresult_crash.py results                  # crash message + crashed thread of each app crash
    python tools/xcresult_crash.py results --grep "REGEX"   # search all decompressed data instead

The bundle's data files are zstd-compressed; they are decompressed with Git for Windows' libzstd.dll through
ctypes, so nothing needs installing. When a test "fails" by restarting over and over, the app crashed: the
crashed thread's frames name the file and line.
"""
import ctypes
import glob
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ZSTD_MAGIC = b"\x28\xb5\x2f\xfd"


class _Buffer(ctypes.Structure):
    _fields_ = [("dst", ctypes.c_void_p), ("size", ctypes.c_size_t), ("pos", ctypes.c_size_t)]


def _zstd():
    lib = ctypes.CDLL(r"C:\Program Files\Git\mingw64\bin\libzstd.dll")
    lib.ZSTD_createDStream.restype = ctypes.c_void_p
    lib.ZSTD_decompressStream.restype = ctypes.c_size_t
    lib.ZSTD_decompressStream.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p]
    lib.ZSTD_isError.argtypes = [ctypes.c_size_t]
    lib.ZSTD_freeDStream.argtypes = [ctypes.c_void_p]
    return lib


def decompress(lib, data):
    stream = lib.ZSTD_createDStream()
    source = ctypes.create_string_buffer(data, len(data))
    inp = _Buffer(ctypes.cast(source, ctypes.c_void_p), len(data), 0)
    chunk = 1 << 20
    target = ctypes.create_string_buffer(chunk)
    output = bytearray()
    try:
        while inp.pos < inp.size:
            out = _Buffer(ctypes.cast(target, ctypes.c_void_p), chunk, 0)
            result = lib.ZSTD_decompressStream(stream, ctypes.byref(out), ctypes.byref(inp))
            if lib.ZSTD_isError(result):
                break
            output += target.raw[: out.pos]
            if result == 0 and inp.pos >= inp.size:
                break
    finally:
        lib.ZSTD_freeDStream(stream)
    return bytes(output)


def contents(folder):
    lib = _zstd()
    for path in glob.glob(os.path.join(folder, "Data", "*")):
        raw = open(path, "rb").read()
        yield path, decompress(lib, raw) if raw[:4] == ZSTD_MAGIC else raw


def crashes(folder, process="CourseTable"):
    for _, text in contents(folder):
        if f'"procName" : "{process}"'.encode() not in text or b'"threads"' not in text:
            continue
        body = text.decode("utf-8", "replace")
        try:
            report = json.loads(body[body.find("{", body.find("\n")):])  # .ips: header line, then the report
        except json.JSONDecodeError:
            continue
        print("=== crash", report.get("captureTime"), report.get("exception", {}).get("type"),
              report.get("termination", {}).get("indicator", ""))
        for key in ("asi", "asiBacktraces"):
            if key in report:
                print(key, json.dumps(report[key], ensure_ascii=False)[:2000])
        images = report.get("usedImages", [])
        for thread in report.get("threads", []):
            if thread.get("triggered"):
                for frame in thread.get("frames", [])[:20]:
                    image = images[frame["imageIndex"]].get("name", "?") if "imageIndex" in frame and images else "?"
                    location = f'{frame.get("sourceFile", "")}:{frame.get("sourceLine", "")}' if "sourceFile" in frame else ""
                    print(f'   {image} | {frame.get("symbol", "?")} {location}')


def grep(folder, pattern):
    regex = re.compile(pattern.encode())
    for path, text in contents(folder):
        match = regex.search(text)
        if match:
            print("==", os.path.basename(path)[:24], "==")
            print(text[max(0, match.start() - 300): match.end() + 1200].decode("utf-8", "replace"), "\n")


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[2] == "--grep":
        grep(sys.argv[1], sys.argv[3])
    elif len(sys.argv) == 2:
        crashes(sys.argv[1])
    else:
        sys.exit(__doc__)
