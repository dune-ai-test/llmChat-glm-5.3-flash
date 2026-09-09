# Generates the Aster launcher icon assets (Apple-style adaptive icon).
# Outputs:
#   app/src/main/res/drawable-nodpi/ic_launcher_bg.png   (432x432 full-bleed gradient)
#   app/src/main/res/drawable-nodpi/ic_launcher_fg.png   (432x432 bubble glyph in safe zone)
#   app/src/main/res/drawable/ic_launcher_mono.xml       (themed-icon vector)
#   tools/icon_preview/*.png                             (squircle / circle / rounded-square previews)
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import numpy as np, math, os, io

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
PREV = os.path.join(ROOT, "tools", "icon_preview")
os.makedirs(PREV, exist_ok=True)

S = 4096            # supersample canvas
U = S / 108.0       # px per adaptive-icon dp unit
CX = CY = 54.0      # canvas center in dp units

# ---------------------------------------------------------------- geometry --
def quad_bezier(p0, p1, p2, n=32):
    return [((1-t)**2*p0[0] + 2*(1-t)*t*p1[0] + t**2*p2[0],
             (1-t)**2*p0[1] + 2*(1-t)*t*p1[1] + t**2*p2[1])
            for t in (i/n for i in range(n+1))]

def cubic_bezier(p0, p1, p2, p3, n=32):
    return [((1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t**2*p2[0] + t**3*p3[0],
             (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t**2*p2[1] + t**3*p3[1])
            for t in (i/n for i in range(n+1))]

def arc(cx, cy, r, a0, a1, n=32):
    return [(cx + r*math.cos(math.radians(a0 + (a1-a0)*i/n)),
             cy + r*math.sin(math.radians(a0 + (a1-a0)*i/n)))
            for i in range(n+1)]

def bubble_outline():
    """Messages-style rounded bubble with a curved tail, bottom-left. 108-unit space."""
    x0, y0, x1, y1, r = 30.0, 31.0, 78.0, 69.0, 13.5
    pts = []
    pts += [(x0+r, y0), (x1-r, y0)]
    pts += arc(x1-r, y0+r, r, -90, 0)          # top-right corner
    pts += [(x1, y1-r)]
    pts += arc(x1-r, y1-r, r, 0, 90)           # bottom-right corner
    pts += [(44.0, y1)]                        # along bottom to tail start
    pts += cubic_bezier((44.0, y1), (41.0, 74.0), (37.5, 80.0), (33.0, 82.0))   # tail out
    pts += cubic_bezier((33.0, 82.0), (30.8, 79.5), (30.0, 68.0), (x0, 57.0))   # tail back
    pts += [(x0, y0+r)]
    pts += arc(x0+r, y0+r, r, 180, 270)        # top-left corner
    return pts

def sparkle_pts(cx, cy, R, k=0.14, n=40):
    """4-point sparkle, concave edges (Apple-style)."""
    N, E, Sm, W = (cx, cy-R), (cx+R, cy), (cx, cy+R), (cx-R, cy)
    d = R * k
    pts = []
    for a, b, (sx, sy) in ((N, E, (1, -1)), (E, Sm, (1, 1)), (Sm, W, (-1, 1)), (W, N, (-1, -1))):
        ctrl = (cx + sx*d, cy + sy*d)
        pts += quad_bezier(a, ctrl, b, n)
    return pts

# normalize: center glyph bbox on canvas center, fit inside the adaptive safe circle
def normalize(shapes, max_radius=32.5, extra=()):
    all_pts = [p for shape in shapes for p in shape] + list(extra)
    xs = [p[0] for p in all_pts]; ys = [p[1] for p in all_pts]
    mx, my = (min(xs)+max(xs))/2, (min(ys)+max(ys))/2
    rmax = max(math.hypot(p[0]-mx, p[1]-my) for p in all_pts)
    k = max_radius / rmax
    def tr(shape):
        return [((p[0]-mx)*k + CX, (p[1]-my)*k + CY) for p in shape]
    return [tr(s) for s in shapes], k

SHAPES, _K = normalize([bubble_outline(), sparkle_pts(54, 49, 14.5)])
BUBBLE, SPARK = SHAPES
rmax_check = max(math.hypot(p[0]-CX, p[1]-CY) for p in BUBBLE)
print(f"glyph normalized: max radius {rmax_check:.2f} dp (safe zone 33 dp)")

def to_px(shape, k=1.0):
    """dp-unit points -> supersampled canvas px, optionally scaled about center."""
    return [((CX + (p[0]-CX)*k) * U, (CY + (p[1]-CY)*k) * U) for p in shape]

def draw_glyph(k=1.0):
    """White bubble with sparkle knocked out + soft drop shadow, on transparent canvas."""
    body = Image.new("L", (S, S), 0)
    ImageDraw.Draw(body).polygon(to_px(BUBBLE, k), fill=255)
    hole = Image.new("L", (S, S), 0)
    ImageDraw.Draw(hole).polygon(to_px(SPARK, k), fill=255)
    glyph_mask = ImageChops.subtract(body, hole)

    # opaque vertical shading: pure white top -> slightly cool bottom
    yy = np.linspace(0, 1, S)[:, None] * np.ones((1, S))
    top, bot = np.array([255.0, 255.0, 255.0]), np.array([235.0, 235.0, 244.0])
    rgb = (top[None, None, :] + yy[..., None] * (bot - top)[None, None, :]).astype(np.uint8)

    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(Image.fromarray(rgb, "RGB"), (0, 0), glyph_mask)

    # soft drop shadow behind the glyph
    sil = body.filter(ImageFilter.GaussianBlur(int(2.6 * U)))
    sh = Image.new("RGBA", (S, S), (30, 26, 100, 255))
    sh.putalpha(sil.point(lambda v: v * 70 // 255))
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    shadow.alpha_composite(sh, (0, int(2.0 * U)))

    final = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    final.alpha_composite(shadow)
    final.alpha_composite(out)
    return final

# ------------------------------------------------------------- background --
def make_bg():
    t = (np.linspace(0, 1, S)[:, None] + np.linspace(0, 1, S)[None, :]) / 2.0
    stops = [(0.00, (0x7D, 0x7A, 0xFF)),   # periwinkle  (top-left)
             (0.50, (0x5B, 0x5B, 0xD6)),   # Aster indigo (brand)
             (1.00, (0xA4, 0x4B, 0xE8))]   # violet      (bottom-right)
    img = np.zeros((S, S, 3), dtype=np.float64)
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        m = np.clip((t - t0) / (t1 - t0), 0, 1)
        img += (m[..., None]) * (np.array(c1) - np.array(c0))[None, None, :]
    img = np.array(stops[0][1], dtype=np.float64)[None, None, :] + img
    # light bloom, top-left
    yy, xx = np.mgrid[0:S, 0:S]
    dd = np.hypot(xx - 0.22*S, yy - 0.18*S) / (0.42*S)
    bloom = np.clip(1 - dd, 0, 1) ** 2.2
    img = img + 255 * (0.16 * bloom)[..., None]
    # gentle vignette toward bottom-right
    dd2 = np.hypot(xx - S, yy - S) / (1.15 * S)
    img *= (1 - 0.10 * np.clip(dd2, 0, 1))[..., None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")

# ------------------------------------------------------------------ write --
bg = make_bg()
bg.resize((432, 432), Image.LANCZOS).save(os.path.join(RES, "drawable-nodpi", "ic_launcher_bg.png"))

glyph_safe = draw_glyph(1.0)
glyph_safe.resize((432, 432), Image.LANCZOS).save(os.path.join(RES, "drawable-nodpi", "ic_launcher_fg.png"))

# previews: iOS squircle / circle / rounded square, glyph scaled like iOS apps
K_IOS = 1.24
g = draw_glyph(K_IOS).resize((1024, 1024), Image.LANCZOS)
base = bg.resize((1024, 1024), Image.LANCZOS).convert("RGBA")

yy, xx = np.mgrid[0:1024, 0:1024]
nx, ny = xx / 512.0 - 1.0, yy / 512.0 - 1.0
masks = {
    "icon_squircle": (np.abs(nx) ** 5 + np.abs(ny) ** 5) <= 1.0,
    "icon_circle":   (nx ** 2 + ny ** 2) <= 1.0,
    "icon_rounded":  (np.abs(nx) <= 0.885) & (np.abs(ny) <= 0.885),
}
for name, m in masks.items():
    a = np.where(m, 255, 0).astype(np.uint8)
    a = np.array(Image.fromarray(a).filter(ImageFilter.GaussianBlur(1.2)))
    comp = base.copy()
    comp.alpha_composite(g)
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
        '        android:fillType="evenOdd"\n'
        f'        android:pathData="{path_of(BUBBLE)} {path_of(SPARK)}" />\n'
        '</vector>\n')
with open(os.path.join(RES, "drawable", "ic_launcher_mono.xml"), "w", newline="\n") as f:
    f.write(mono)

print("done")
