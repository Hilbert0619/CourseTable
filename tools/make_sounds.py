"""Builds the focus timer's background sounds (CourseTable/Sounds/sound-<id>.m4a) from content/sounds/sources.json.

    python3 tools/make_sounds.py OUT_DIR        # needs ffmpeg; runs in the manual `Sounds` workflow on Linux

For each recording: download the Freesound preview, take `length` + 4 seconds from `start`, crossfade the last
4 seconds into the first ones so the loop has no seam, bring it to -20 LUFS with a fixed gain (a limiter holds
peaks at -1 dB, taking at most 9 dB off the loudest ones), and encode AAC (112 kb/s stereo, 44.1 kHz). Prints a report of what was measured and written.
"""
import json
import os
import subprocess
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FADE = 4.0
TARGET_LUFS = -20.0
MAX_PEAK = -1.0
# At most this much of a peaky recording (a campfire's crackles) is held down by a limiter to reach the target.
MAX_LIMITING = 9.0


def ffmpeg(*args):
    """Runs ffmpeg; stops with its messages if it fails. Returns what it printed."""
    result = subprocess.run(["ffmpeg", "-y", "-hide_banner", *args], capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"ffmpeg {' '.join(args)} failed:\n{result.stderr[-3000:]}")
    return result.stderr


def loudness(path):
    """Integrated loudness and true peak, from ffmpeg's loudnorm analysis."""
    log = ffmpeg("-nostats", "-i", path, "-af", "loudnorm=print_format=json", "-f", "null", "-")
    report = json.loads(log[log.rindex("{"):])
    return float(report["input_i"]), float(report["input_tp"])


def duration(path):
    """Seconds of audio, by decoding the file (container headers don't always say)."""
    log = ffmpeg("-i", path, "-f", "null", "-")
    stamp = log[log.rindex("time=") + 5:].split()[0]
    hours, minutes, seconds = stamp.split(":")
    return int(hours) * 3600 + int(minutes) * 60 + float(seconds)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out_dir = sys.argv[1]
    work = os.path.join(out_dir, "work")
    os.makedirs(work, exist_ok=True)
    sources = json.load(open(os.path.join(ROOT, "content", "sounds", "sources.json"), encoding="utf-8"))["sounds"]
    for sound in sources:
        name = sound["id"]
        raw = os.path.join(work, f"{name}.ogg")
        urllib.request.urlretrieve(sound["preview"], raw)
        total = duration(raw)
        start, length = float(sound["start"]), float(sound["length"])
        if start + length + FADE > total:
            sys.exit(f"{name}: {start} + {length} + {FADE} s is longer than the recording ({total:.1f} s)")

        # The section, as 44.1 kHz stereo WAV; then its first 4 seconds (head) and the rest (body).
        section = os.path.join(work, f"{name}-section.wav")
        head = os.path.join(work, f"{name}-head.wav")
        body = os.path.join(work, f"{name}-body.wav")
        ffmpeg("-ss", str(start), "-t", str(length + FADE), "-i", raw, "-ar", "44100", "-ac", "2", section)
        ffmpeg("-i", section, "-t", str(FADE), head)
        ffmpeg("-ss", str(FADE), "-i", section, body)

        integrated, peak = loudness(section)
        gain = TARGET_LUFS - integrated
        limiter = ""
        if peak + gain > MAX_PEAK:
            gain = min(gain, MAX_PEAK - peak + MAX_LIMITING)
            limiter = ",alimiter=limit=0.891:attack=5:release=80:level=0"

        # The body's last 4 seconds fade into the head, which continues where the body begins: no seam.
        target = os.path.join(out_dir, f"sound-{name}.m4a")
        ffmpeg("-i", body, "-i", head, "-filter_complex", f"[0:a][1:a]acrossfade=d={FADE}:c1=qsin:c2=qsin,volume={gain:.2f}dB{limiter}[out]",
               "-map", "[out]", "-c:a", "aac", "-b:a", "112k", "-movflags", "+faststart", target)
        if os.path.getsize(target) < 10_000:
            sys.exit(f"{name}: the encoded file is empty")
        result, _ = loudness(target)
        print(f"{name:9} source {total:6.1f} s  {integrated:6.1f} LUFS  peak {peak:5.1f} dBTP  gain {gain:+5.1f} dB"
              f"{' limited' if limiter else '        '}  -> {duration(target):5.1f} s  {result:6.1f} LUFS"
              f"  {os.path.getsize(target) / 1024:6.0f} KB", flush=True)
    for leftover in os.listdir(work):
        os.remove(os.path.join(work, leftover))
    os.rmdir(work)


if __name__ == "__main__":
    main()
