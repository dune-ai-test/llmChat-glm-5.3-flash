# Generates the Aster launcher icon assets — "Light minimal" design:
# gradient sparkle duo on a clean white background (Apple App-Store style).
# Outputs:
#   app/src/main/res/drawable-nodpi/ic_launcher_bg.png   (432x432 full-bleed light bg)
#   app/src/main/res/drawable-nodpi/ic_launcher_fg.png   (432x432 sparkle glyph, safe zone)
#   app/src/main/res/drawable/ic_launcher_mono.xml       (themed-icon vector)
#   tools/icon_preview/icon_{squircle,circle,rounded}.png
from PIL import Image, ImageDraw, ImageFilter
import numpy as np, math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
PREV = os.path.join(ROOT, "tools", "icon_preview")
os.makedirs(PREV, exist_ok=True)

S = 4096            # supersample canvas
U = S / 108.0       # px per adaptive-icon dp unit
CX = CY = 54.0

# ---------------------------------------------------------------- geometry --
def quad_bezier(p0, p1, p2, n=32):
    return [((1-t)**2*p0[0] + 2*(1-t)*t*p1[0] + t**2*p2[0],
             (1-t)**2*p0[1] + 2*(1-t)*t*p1[1] + t**2*p2[1])
            for t in (i/n for i in range(n+1))]

def sparkle(cx, cy, R, k=0.14, n=40):
    """4-point sparkle, concave edges."""
    N, E, Sm, W = (cx, cy-R), (cx+R, cy), (cx, cy+R), (cx-R, cy)
    d = R * k
    pts = []
    for a, b, (sx, sy) in ((N, E, (1, -1)), (E, Sm, (1, 1)), (Sm, W, (-1, 1)), (W, N, (-1, -1))):
        pts += quad_bezier(a, (cx + sx*d, cy + sy*d), b, n)
    return pts

def fit(shapes, max_radius, center=(CX, CY)):
    all_pts = [p for s in shapes for p in s]
    xs = [p[0] for p in all_pts]; ys = [p[1] for p in all_pts]
    mx, my = (min(xs)+max(xs))/2, (min(ys)+max(ys))/2
    rmax = max(math.hypot(p[0]-mx, p[1]-my) for p in all_pts)
    k = max_radius / rmax
    return [[((p[0]-mx)*k + center[0], (p[1]-my)*k + center[1]) for p in s] for s in shapes]

MAIN, COMP = fit([sparkle(54, 55, 19.5), sparkle(72.5, 33.0, 6.6)], 32.5)

def to_px(shape, k=1.0):
    return [((CX + (p[0]-CX)*k) * U, (CY + (p[1]-CY)*k) * U) for p in shape]

def mask_of(shape, k=1.0, alpha=255):
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).polygon(to_px(shape, k), fill=alpha)
    return m

# ------------------------------------------------------------- background --
yy, xx = np.mgrid[0:S, 0:S]
t = np.linspace(0, 1, S)[:, None] * np.ones((1, S))
top, bot = np.array([251.0, 251.0, 253.0]), np.array([236.0, 236.0, 243.0])
bg = top[None, None, :] + t[..., None] * (bot - top)[None, None, :]
bloom_d = np.hypot(xx - 0.2*S, yy - 0.15*S) / (0.5*S)
bg += 255 * (0.5 * np.clip(1 - bloom_d, 0, 1) ** 2.2)[..., None]
bg_img = Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8), "RGB").convert("RGBA")

# ------------------------------------------------------------------ glyph --
GRAD_STOPS = [(0.0, (0x60, 0x5C, 0xF0)), (0.5, (0x8B, 0x4D, 0xF6)), (1.0, (0xC4, 0x45, 0xF7))]
tg = (np.linspace(0, 1, S)[:, None] + np.linspace(0, 1, S)[None, :]) / 2.0
grad = np.zeros((S, S, 3))
for (t0, c0), (t1, c1) in zip(GRAD_STOPS, GRAD_STOPS[1:]):
    m = np.clip((tg - t0) / (t1 - t0), 0, 1)[..., None]
    grad += m * (np.array(c1, np.float64) - np.array(c0, np.float64))[None, None, :]
grad = Image.fromarray((np.array(c0, np.float64)[None, None, :] + grad).clip(0, 255).astype(np.uint8), "RGB")

glyph = Image.new("RGBA", (S, S), (0, 0, 0, 0))
for shp, sh_alpha in ((MAIN, 42), (COMP, 30)):
    sil = mask_of(shp, alpha=sh_alpha).filter(ImageFilter.GaussianBlur(int(2.2 * U)))
    shi = Image.new("RGBA", (S, S), (90, 90, 160, 255)); shi.putalpha(sil)
    glyph.alpha_composite(shi, (int(1.2 * U), int(1.8 * U)))
glyph.paste(grad, (0, 0), mask_of(MAIN))
glyph.paste(grad, (0, 0), mask_of(COMP, alpha=235))

# ------------------------------------------------------------------ write --
bg_img.resize((432, 432), Image.LANCZOS).save(os.path.join(RES, "drawable-nodpi", "ic_launcher_bg.png"))
glyph.resize((432, 432), Image.LANCZOS).save(os.path.join(RES, "drawable-nodpi", "ic_launcher_fg.png"))

base = bg_img.resize((1024, 1024), Image.LANCZOS)
g1024 = glyph.resize((1024, 1024), Image.LANCZOS)
nyy, nxx = np.mgrid[0:1024, 0:1024]
nx, ny = nxx / 512.0 - 1.0, nyy / 512.0 - 1.0
masks = {
    "icon_squircle": (np.abs(nx) ** 5 + np.abs(ny) ** 5) <= 1.0,
    "icon_circle":   (nx ** 2 + ny ** 2) <= 1.0,
    "icon_rounded":  (np.abs(nx) <= 0.885) & (np.abs(ny) <= 0.885),
}
for name, m in masks.items():
    a = np.where(m, 255, 0).astype(np.uint8)
    a = np.array(Image.fromarray(a).filter(ImageFilter.GaussianBlur(1.2)))
    comp = base.copy()
    comp.alpha_composite(g1024)
    comp.putalpha(Image.fromarray(a))
    comp.save(os.path.join(PREV, name + ".png"))

# ------------------------------------------------------- mono themed icon --
def fmt(p): return f"{p[0]:.1f},{p[1]:.1f}"
def path_of(shape):
    return "M" + fmt(shape[0]) + " L" + " L".join(fmt(p) for p in shape[1:]) + " Z"

mono = ('<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        '    <path\n        android:fillColor="#FFFFFF"\n'
        f'        android:pathData="{path_of(MAIN)} {path_of(COMP)}" />\n'
        '</vector>\n')
with open(os.path.join(RES, "drawable", "ic_launcher_mono.xml"), "w", newline="\n") as f:
    f.write(mono)

print("done: light-minimal icon written")
