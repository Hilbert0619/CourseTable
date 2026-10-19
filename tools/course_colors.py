"""Derives the course colour styles (docs/design-0.7.md) from the ten original hues in OKLCH and checks contrast.

    py -3.12 -I tools/course_colors.py [sheet.png]      # the sheet needs Pillow

Solid: the hue darkened until white text reaches 3.5:1. Soft (light): a pale tint (L 0.925) with dark same-hue text
(L 0.40); blue and indigo's tints are nudged apart in hue so every pair stays distinct. Soft (dark): a deep tint
(L 0.32) with light same-hue text (L 0.88). Prints the values for CourseKit's CourseColors.swift and
spec/fixtures/course-colors.json (copy them by hand), the contrasts, the closest pairs, and the accent's contrast.
"""
import math
import sys

BASES = {
    "coral": (0.89, 0.35, 0.33), "orange": (0.93, 0.53, 0.18), "mustard": (0.76, 0.58, 0.05),
    "green": (0.28, 0.64, 0.36), "teal": (0.13, 0.60, 0.58), "blue": (0.22, 0.52, 0.86),
    "indigo": (0.38, 0.40, 0.84), "purple": (0.60, 0.38, 0.83), "pink": (0.86, 0.36, 0.60),
    "slate": (0.47, 0.52, 0.58),
}


def lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def unlin(c):
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def to_oklab(rgb):
    r, g, b = (lin(c) for c in rgb)
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l, m, s = (math.copysign(abs(x) ** (1 / 3), x) for x in (l, m, s))
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def from_oklab(lab):
    L, a, b = lab
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    rgb = (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
           -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
           -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
    return tuple(min(max(unlin(min(max(c, 0), 1)), 0), 1) for c in rgb)


def lch(rgb):
    L, a, b = to_oklab(rgb)
    return L, math.hypot(a, b), math.atan2(b, a)


def from_lch(L, C, h):
    # Reduce chroma until the colour fits in sRGB.
    while C > 0:
        lab = (L, C * math.cos(h), C * math.sin(h))
        l = (lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]) ** 3
        m = (lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]) ** 3
        s = (lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2]) ** 3
        rgb = (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
               -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
               -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
        if all(-0.0005 <= c <= 1.0005 for c in rgb):
            break
        C -= 0.002
    return from_oklab((L, C * math.cos(h), C * math.sin(h)))


def luminance(rgb):
    r, g, b = (lin(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(x, y):
    a, b = sorted((luminance(x), luminance(y)), reverse=True)
    return (a + 0.05) / (b + 0.05)


WHITE = (1, 1, 1)
rows = []
for name, base in BASES.items():
    L, C, h = lch(base)
    solid = base
    while contrast(WHITE, solid) < 3.5:
        L2, C2, _ = lch(solid)
        solid = from_lch(L2 - 0.01, C2, h)
    soft_light_fill = from_lch(0.925, min(C * 0.45, 0.06), h + {"blue": -0.3, "indigo": -0.12}.get(name, 0))
    soft_light_text = from_lch(0.40, min(C * 0.9, 0.13), h)
    soft_dark_fill = from_lch(0.32, min(C * 0.5, 0.07), h)
    soft_dark_text = from_lch(0.88, min(C * 0.5, 0.07), h)
    rows.append((name, base, solid, soft_light_fill, soft_light_text, soft_dark_fill, soft_dark_text))
    print(f"{name:8} white/solid {contrast(WHITE, solid):4.2f} (was {contrast(WHITE, base):4.2f})  "
          f"soft light {contrast(soft_light_text, soft_light_fill):4.2f}  soft dark {contrast(soft_dark_text, soft_dark_fill):4.2f}")


def fmt(rgb):
    return "(" + ", ".join(f"{c:.3f}" for c in rgb) + ")"


print()
for name, base, solid, lf, lt, df, dt in rows:
    print(f'.init(name: "{name}", solid: {fmt(solid)}, softFill: {fmt(lf)}, softText: {fmt(lt)}, '
          f'darkFill: {fmt(df)}, darkText: {fmt(dt)}),')

if len(sys.argv) > 1:
    from PIL import Image, ImageDraw, ImageFont
    font = ImageFont.truetype("arialbd.ttf", 22)
    small = ImageFont.truetype("arial.ttf", 18)
    W, H = 230, 120
    sheet = Image.new("RGB", (W * 3 + 40, H * len(rows) + 40), (244, 242, 238))
    draw = ImageDraw.Draw(sheet)
    dark_bg = (18, 18, 20)
    draw.rectangle((W * 2 + 20, 0, W * 3 + 40, sheet.height), fill=dark_bg)
    for i, (name, base, solid, lf, lt, df, dt) in enumerate(rows):
        y = 20 + i * H
        for col, (fill, text) in enumerate([(solid, WHITE), (lf, lt), (df, dt)]):
            x = 10 + col * (W + 10)
            f = tuple(int(c * 255) for c in fill)
            t = tuple(int(c * 255) for c in text)
            draw.rounded_rectangle((x, y, x + W - 10, y + H - 14), radius=12, fill=f)
            draw.text((x + 12, y + 10), "Adv. Maths", fill=t, font=font)
            draw.text((x + 12, y + 44), f"{name} · A101", fill=t, font=small)
    sheet.save(sys.argv[1])
    print("sheet", sys.argv[1])

# The app's accent: the same hue, darkened until it reaches 4.5:1 on white (links, buttons' text).
accent = (0.345, 0.420, 0.918)
accent_dark = (0.510, 0.580, 1.000)
print("accent on white", round(contrast(WHITE, accent), 2), " dark accent on black", round(contrast((0, 0, 0), accent_dark), 2),
      " on #1c1c1e", round(contrast((0.11, 0.11, 0.118), accent_dark), 2))
L, C, h = lch(accent)
tuned = accent
while contrast(WHITE, tuned) < 4.6:
    L2, C2, _ = lch(tuned)
    tuned = from_lch(L2 - 0.005, C2, h)
print("tuned accent", fmt(tuned), round(contrast(WHITE, tuned), 2), " white text on it", round(contrast(WHITE, tuned), 2))
paper = (0.957, 0.949, 0.933)
print("paper", fmt(paper), " tuned accent on paper", round(contrast(paper, tuned), 2))

# The closest pair of soft fills (largest channel gap), so the timetable keeps courses apart.
worst = min(((max(abs(x - y) for x, y in zip(a[3], b[3])), a[0], b[0]) for i, a in enumerate(rows) for b in rows[i + 1:]))
print("closest soft fills", worst)
worst_dark = min(((max(abs(x - y) for x, y in zip(a[5], b[5])), a[0], b[0]) for i, a in enumerate(rows) for b in rows[i + 1:]))
print("closest dark fills", worst_dark)
