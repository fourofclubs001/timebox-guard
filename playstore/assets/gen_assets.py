#!/usr/bin/env python3
"""Pure-stdlib PNG generator for Timebox Guard store assets.

Draws with 4x supersampling for anti-aliasing. No third-party deps.
Outputs:
  - ic_play_store_512.png   (512x512, app icon for the listing)
  - feature_graphic_1024x500.png
  - icon_foreground_432.png (adaptive-icon foreground, transparent, safe zone)
"""
import struct, zlib, math, os

OUT = os.path.join(os.path.dirname(__file__), "out")
os.makedirs(OUT, exist_ok=True)

ACCENT = (0x3F, 0x7D, 0xE0)
ACCENT_DK = (0x2E, 0x5F, 0xB0)
WHITE = (255, 255, 255)
SAND = (0xF2, 0xC1, 0x66)


class Canvas:
    def __init__(self, w, h, bg=(0, 0, 0, 0), ss=4):
        self.w, self.h, self.ss = w, h, ss
        self.W, self.H = w * ss, h * ss
        self.px = bytearray(bg * (self.W * self.H) if len(bg) == 4
                             else (bg + (255,)) * (self.W * self.H))

    def _blend(self, x, y, rgba):
        if x < 0 or y < 0 or x >= self.W or y >= self.H:
            return
        i = (y * self.W + x) * 4
        sr, sg, sb, sa = rgba
        a = sa / 255.0
        for k, s in enumerate((sr, sg, sb)):
            d = self.px[i + k]
            self.px[i + k] = int(s * a + d * (1 - a) + 0.5)
        self.px[i + 3] = max(self.px[i + 3], sa)

    def fill_poly(self, pts, color):
        pts = [(x * self.ss, y * self.ss) for x, y in pts]
        ys = [p[1] for p in pts]
        y0, y1 = max(0, int(min(ys))), min(self.H, int(max(ys)) + 1)
        rgba = color if len(color) == 4 else color + (255,)
        n = len(pts)
        for y in range(y0, y1):
            yc = y + 0.5
            xs = []
            for i in range(n):
                ax, ay = pts[i]
                bx, by = pts[(i + 1) % n]
                if (ay <= yc < by) or (by <= yc < ay):
                    xs.append(ax + (yc - ay) * (bx - ax) / (by - ay))
            xs.sort()
            for j in range(0, len(xs) - 1, 2):
                for x in range(max(0, int(xs[j])), min(self.W, int(xs[j + 1]) + 1)):
                    self._blend(x, y, rgba)

    def fill_rrect(self, x, y, w, h, r, color):
        rgba = color if len(color) == 4 else color + (255,)
        x, y, w, h, r = (v * self.ss for v in (x, y, w, h, r))
        for yy in range(max(0, int(y)), min(self.H, int(y + h))):
            for xx in range(max(0, int(x)), min(self.W, int(x + w))):
                cx = min(max(xx, x + r), x + w - r)
                cy = min(max(yy, y + r), y + h - r)
                if (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r:
                    self._blend(xx, yy, rgba)

    def downsample(self):
        ss = self.ss
        out = bytearray(self.w * self.h * 4)
        for y in range(self.h):
            for x in range(self.w):
                r = g = b = a = 0
                for dy in range(ss):
                    for dx in range(ss):
                        i = ((y * ss + dy) * self.W + (x * ss + dx)) * 4
                        r += self.px[i]; g += self.px[i + 1]
                        b += self.px[i + 2]; a += self.px[i + 3]
                n = ss * ss
                o = (y * self.w + x) * 4
                out[o] = r // n; out[o + 1] = g // n
                out[o + 2] = b // n; out[o + 3] = a // n
        return out

    def write(self, path):
        raw = self.downsample()
        rows = bytearray()
        for y in range(self.h):
            rows.append(0)
            rows.extend(raw[y * self.w * 4:(y + 1) * self.w * 4])
        def chunk(tag, data):
            return (struct.pack(">I", len(data)) + tag + data +
                    struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))
        png = (b"\x89PNG\r\n\x1a\n" +
               chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0)) +
               chunk(b"IDAT", zlib.compress(bytes(rows), 9)) +
               chunk(b"IEND", b""))
        with open(path, "wb") as f:
            f.write(png)
        print("wrote", path, f"({self.w}x{self.h})")


def hourglass(c, cx, cy, half_w, half_h, glass=WHITE, sand=None, cap=WHITE):
    """Draw an hourglass centred at (cx,cy)."""
    neck = half_w * 0.10
    # glass body: two triangles
    c.fill_poly([(cx - half_w, cy - half_h), (cx + half_w, cy - half_h),
                 (cx + neck, cy), (cx - neck, cy)], glass)
    c.fill_poly([(cx - neck, cy), (cx + neck, cy),
                 (cx + half_w, cy + half_h), (cx - half_w, cy + half_h)], glass)
    if sand:
        sh = half_h * 0.55
        c.fill_poly([(cx - half_w * 0.72, cy - half_h * 0.9),
                     (cx + half_w * 0.72, cy - half_h * 0.9),
                     (cx + neck, cy - half_h * 0.06),
                     (cx - neck, cy - half_h * 0.06)], sand)
        c.fill_poly([(cx - neck, cy + half_h * 0.06),
                     (cx + neck, cy + half_h * 0.06),
                     (cx + half_w * 0.5, cy + half_h * 0.9),
                     (cx - half_w * 0.5, cy + half_h * 0.9)], sand)
    # caps
    cap_h = half_h * 0.14
    cap_w = half_w * 1.28
    c.fill_rrect(cx - cap_w / 2, cy - half_h - cap_h * 0.7, cap_w, cap_h, cap_h / 2, cap)
    c.fill_rrect(cx - cap_w / 2, cy + half_h - cap_h * 0.3, cap_w, cap_h, cap_h / 2, cap)


# ---- app icon 512 -------------------------------------------------------
c = Canvas(512, 512, bg=ACCENT)
c.fill_rrect(0, 0, 512, 512, 0, ACCENT)          # full bleed; Play masks it
hourglass(c, 256, 262, 120, 140, glass=WHITE, sand=SAND)
c.write(os.path.join(OUT, "ic_play_store_512.png"))

# ---- adaptive foreground 432 (safe zone ~ centre 264) ------------------
c = Canvas(432, 432, bg=(0, 0, 0, 0))
hourglass(c, 216, 220, 78, 92, glass=WHITE, sand=SAND)
c.write(os.path.join(OUT, "icon_foreground_432.png"))

# ---- feature graphic 1024x500 ----------------------------------------
c = Canvas(1024, 500, bg=ACCENT)
# faint pattern
for i, x in enumerate((760, 880, 1000)):
    hourglass(c, x, 250, 70, 90, glass=(255, 255, 255, 26), sand=None, cap=(255, 255, 255, 26))
hourglass(c, 210, 250, 118, 150, glass=WHITE, sand=SAND)
c.write(os.path.join(OUT, "feature_graphic_1024x500.png"))
