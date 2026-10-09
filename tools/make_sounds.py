"""Builds the focus timer's background sounds (CourseTable/Sounds/sound-<id>.m4a) from content/sounds/sources.json.

    python3 tools/make_sounds.py OUT_DIR        # needs ffmpeg; runs in the manual `Sounds` workflow on Linux

For each recording: download the Freesound preview, take `length` + 4 seconds from `start`, crossfade the last
4 seconds into the first ones so the loop has no seam, set it to -20 LUFS with a fixed gain (peaks at most
-1 dBTP), and encode AAC (112 kb/s stereo, 44.1 kHz). Prints a report of what was measured and written.
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


def loudness(path, trim):
    """Integrated loudness and true peak of the trimmed section, from ffmpeg's loudnorm analysis."""
    result = subprocess.run(
        ["ffmpeg", "-hide_banner", "-nostats", "-i", path, "-af", f"{trim},loudnorm=print_format=json", "-f", "null", "-"],
        capture_output=True, text=True, check=True,
    )
    report = json.loads(result.stderr[result.stderr.rindex("{"):])
    return float(report["input_i"]), float(report["input_tp"])


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         capture_output=True, text=True, check=True).stdout
    return float(out.strip())


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    sources = json.load(open(os.path.join(ROOT, "content", "sounds", "sources.json"), encoding="utf-8"))["sounds"]
    for sound in sources:
        raw = os.path.join(out_dir, f"source-{sound['id']}.ogg")
        urllib.request.urlretrieve(sound["preview"], raw)
        total = duration(raw)
        start, length = float(sound["start"]), float(sound["length"])
        if start + length + FADE > total:
            sys.exit(f"{sound['id']}: {start} + {length} + {FADE} s is longer than the recording ({total:.1f} s)")
        trim = f"atrim=start={start}:duration={length + FADE},asetpts=PTS-STARTPTS"
        integrated, peak = loudness(raw, trim)
        gain = TARGET_LUFS - integrated
        if peak + gain > MAX_PEAK:
            gain = MAX_PEAK - peak
        graph = (
            f"[0:a]{trim},aresample=44100,aformat=channel_layouts=stereo,asplit[s1][s2];"
            f"[s1]atrim=start={FADE},asetpts=PTS-STARTPTS[body];"
            f"[s2]atrim=end={FADE},asetpts=PTS-STARTPTS[head];"
            f"[body][head]acrossfade=d={FADE}:c1=qsin:c2=qsin,volume={gain:.2f}dB[out]"
        )
        target = os.path.join(out_dir, f"sound-{sound['id']}.m4a")
        subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", raw, "-filter_complex", graph,
                        "-map", "[out]", "-c:a", "aac", "-b:a", "112k", "-movflags", "+faststart", target], check=True)
        os.remove(raw)
        print(f"{sound['id']:9} source {total:6.1f} s  {integrated:6.1f} LUFS  peak {peak:5.1f} dBTP  gain {gain:+5.1f} dB"
              f"  -> {duration(target):5.1f} s  {os.path.getsize(target) / 1024:6.0f} KB")


if __name__ == "__main__":
    main()
