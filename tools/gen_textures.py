"""Generates the PNG textures for the Mob Slayer mod (no Pillow needed).

Usage: python tools/gen_textures.py <project root>
The front of the active block is an animated saw blade (frames stacked vertically, see the .mcmeta file).
"""
import math
import os
import random
import struct
import sys
import zlib


def write_png(path, w, h, px):
    """px: list of rows, each row a list of (r, g, b, a)."""
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(px[y][x])

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def canvas(w, h, color=(0, 0, 0, 0)):
    return [[color for _ in range(w)] for _ in range(h)]


def shade(c, d):
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), c[3])


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


# dark gunmetal casing, darkest to lightest
STEEL = [(30, 32, 36, 255), (52, 56, 62, 255), (74, 79, 87, 255), (98, 104, 113, 255), (126, 133, 142, 255), (160, 167, 175, 255)]
HAZARD = (214, 160, 34, 255)


def casing(seed):
    """Riveted plate with a bevelled frame; shared by the side, top, bottom and back."""
    rnd = random.Random(seed)
    px = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            px[y][x] = shade(STEEL[2], rnd.randint(-4, 4))
    for i in range(16):
        px[0][i] = STEEL[4]
        px[i][0] = STEEL[4]
        px[15][i] = STEEL[0]
        px[i][15] = STEEL[0]
    for i in range(1, 15):
        px[1][i] = shade(STEEL[3], rnd.randint(-3, 3))
        px[i][1] = shade(STEEL[3], rnd.randint(-3, 3))
        px[14][i] = STEEL[1]
        px[i][14] = STEEL[1]
    for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        px[y][x] = STEEL[5]
        px[y + 1][x] = STEEL[1]
        px[y][x - 1] = shade(STEEL[3], 6)
    return px


def side():
    px = casing(11)
    # hazard stripes across the middle
    for y in range(6, 10):
        for x in range(2, 14):
            stripe = ((x + y) // 2) % 2 == 0
            px[y][x] = HAZARD if stripe else STEEL[0]
    for x in range(2, 14):
        px[5][x] = STEEL[1]
        px[10][x] = STEEL[4]
    return px


def back():
    """Casing with a round window that shows the green experience inside."""
    px = casing(29)
    rnd = random.Random(5)
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - cx, y - cy)
            if r <= 3.2:
                glow = 1.0 - r / 3.2
                c = mix((40, 120, 20, 255), (190, 255, 90, 255), glow)
                if (x, y) in [(6, 6), (7, 6), (6, 7)]:
                    c = (235, 255, 200, 255)
                px[y][x] = shade(c, rnd.randint(-6, 6))
            elif r <= 4.4:
                px[y][x] = STEEL[1] if x + y > 15 else STEEL[4]
    return px


def saw(rotation, hot):
    """Front: frame and dark recess with a circular saw blade, supersampled for clean teeth."""
    px = canvas(16, 16)
    rnd = random.Random(3)
    recess = (22, 20, 22, 255)
    for y in range(16):
        for x in range(16):
            px[y][x] = shade(recess, rnd.randint(-2, 2))
    if hot:
        # faint red glow from the motor behind the blade
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                px[y][x] = mix(px[y][x], (120, 20, 16, 255), max(0.0, 0.55 - r / 14.0))

    teeth = 6
    samples = 4
    for y in range(16):
        for x in range(16):
            color_sum = [0.0, 0.0, 0.0]
            covered = 0
            for sy in range(samples):
                for sx in range(samples):
                    fx = x + (sx + 0.5) / samples - 8.0
                    fy = y + (sy + 0.5) / samples - 8.0
                    r = math.hypot(fx, fy)
                    a = math.atan2(fy, fx)
                    frac = ((a - rotation) * teeth / (2 * math.pi)) % 1.0
                    edge = 4.4 + 2.4 * (1.0 - frac)
                    if r > edge:
                        continue
                    if r < 1.3:
                        c = (40, 42, 46)
                    elif r < 2.2:
                        c = (150, 156, 164)
                    elif r > 4.4:
                        # tooth tips
                        c = (255, 110, 70) if hot else (225, 230, 236)
                    else:
                        light = 0.5 + 0.5 * math.cos(a + math.pi * 0.75)
                        c = tuple(v for v in mix((96, 102, 110, 255), (196, 202, 210, 255), light)[:3])
                        if 3.0 < r < 3.5:
                            c = tuple(max(0, v - 30) for v in c)
                    color_sum[0] += c[0]
                    color_sum[1] += c[1]
                    color_sum[2] += c[2]
                    covered += 1
            if covered:
                blade = (color_sum[0] / covered, color_sum[1] / covered, color_sum[2] / covered, 255)
                px[y][x] = mix(px[y][x], blade, covered / (samples * samples))

    # bevelled steel frame around the opening
    for i in range(16):
        px[0][i] = STEEL[4]
        px[i][0] = STEEL[4]
        px[15][i] = STEEL[0]
        px[i][15] = STEEL[0]
    for i in range(1, 15):
        px[1][i] = STEEL[3]
        px[i][1] = STEEL[3]
        px[14][i] = STEEL[1]
        px[i][14] = STEEL[1]
    return px


def main(root):
    tex = os.path.join(root, "src/main/resources/assets/mobslayer/textures/block")
    write_png(os.path.join(tex, "mob_slayer_side.png"), 16, 16, side())
    write_png(os.path.join(tex, "mob_slayer_back.png"), 16, 16, back())
    write_png(os.path.join(tex, "mob_slayer_front.png"), 16, 16, saw(0.0, False))

    # 4 frames cover one tooth (60 degrees), so the loop is seamless
    frames = 4
    animated = []
    for f in range(frames):
        animated.extend(saw(-f * (2 * math.pi / 6) / frames, True))
    write_png(os.path.join(tex, "mob_slayer_front_active.png"), 16, 16 * frames, animated)
    with open(os.path.join(tex, "mob_slayer_front_active.png.mcmeta"), "w", encoding="utf-8") as f:
        f.write('{\n\t"animation": {\n\t\t"frametime": 1\n\t}\n}\n')

    # mod icon: the saw scaled up 8x
    icon = saw(0.0, True)
    big = [[icon[y // 8][x // 8] for x in range(128)] for y in range(128)]
    write_png(os.path.join(root, "src/main/resources/assets/mobslayer/icon.png"), 128, 128, big)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
